package com.shibi.musicplayer

import com.shibi.musicplayer.domain.ItemCheck
import com.shibi.musicplayer.domain.MediaKind
import com.shibi.musicplayer.domain.TrashFailReason
import com.shibi.musicplayer.domain.TrashFailure
import com.shibi.musicplayer.domain.TrashItem
import com.shibi.musicplayer.domain.TrashResult
import com.shibi.musicplayer.playlist.CleanupSnapshot
import com.shibi.musicplayer.playlist.PlaylistSongEntity
import com.shibi.musicplayer.playlist.TrashController
import com.shibi.musicplayer.playlist.TrashNotice
import com.shibi.musicplayer.playlist.TrashNoticeKind
import com.shibi.musicplayer.playlist.TrashStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashControllerTest {
    private fun song(id: Long) = TrashItem(MediaKind.Song, id, "content://media/external/audio/media/$id", "s$id")

    private companion object {
        val DONE = ItemCheck(found = true, trashed = true, volumeName = TrashResult.PRIMARY_VOLUME)
        val PENDING = ItemCheck(found = true, trashed = false, volumeName = TrashResult.PRIMARY_VOLUME)
    }

    private val rowsFor = mapOf(
        1L to PlaylistSongEntity(10, 1, 0),
        2L to PlaylistSongEntity(10, 2, 1),
        3L to PlaylistSongEntity(10, 3, 2),
    )

    private class Fixture(
        val rowsFor: Map<Long, PlaylistSongEntity>,
        var supported: Boolean = true,
        var notTrashed: Set<String> = emptySet(),
    ) {
        var checkCalls = 0

        /** Answers per call; when empty, [notTrashed] uris are found-not-trashed and the rest trashed. */
        var scripted: MutableList<(List<String>) -> Map<String, ItemCheck>> = mutableListOf()
        val captured = mutableListOf<List<TrashItem>>()
        val cleaned = mutableListOf<List<TrashItem>>()
        val restored = mutableListOf<CleanupSnapshot>()
        val trashed = mutableListOf<List<TrashItem>>()

        /** Rows the capture saw; the "prune" empties this before cleanup runs. */
        var live: Map<Long, PlaylistSongEntity> = rowsFor

        val controller = TrashController(
            scope = CoroutineScope(Dispatchers.Unconfined),
            supported = { supported },
            check = { uris ->
                checkCalls++
                if (scripted.isNotEmpty()) {
                    scripted.removeAt(0)(uris)
                } else {
                    uris.associateWith { if (it in notTrashed) PENDING else DONE }
                }
            },
            capture = { items ->
                captured += items
                CleanupSnapshot(playlistSongs = items.mapNotNull { live[it.id] })
            },
            cleanup = { items ->
                cleaned += items
                CleanupSnapshot(playlistSongs = items.mapNotNull { live[it.id] })
            },
            restore = { restored += it },
            onTrashed = { trashed += it },
            retryDelayMs = 0,
            maxChecks = 3,
        )
    }

    private fun fixture(configure: Fixture.() -> Unit = {}) = Fixture(rowsFor).apply(configure)

    @Test
    fun wholeListAsksFirstThenCapturesBeforeTheSystemPopup() {
        val f = fixture()
        val items = listOf(song(1), song(2))

        f.controller.request(items, wholeList = true)

        assertEquals(TrashStep.ConfirmGroup(items), f.controller.state.value.step)
        assertTrue(f.captured.isEmpty())

        f.controller.confirmGroup()

        assertEquals(listOf(items), f.captured)
        assertEquals(TrashStep.AwaitSystem(items, trash = true), f.controller.state.value.step)
    }

    @Test
    fun aSelectionGoesStraightToTheSystemAfterCapturing() {
        val f = fixture()
        val items = listOf(song(1), song(2))

        f.controller.request(items, wholeList = false)

        assertEquals(listOf(items), f.captured)
        assertEquals(TrashStep.AwaitSystem(items, trash = true), f.controller.state.value.step)
    }

    @Test
    fun dismissChangesNothing() {
        val f = fixture()
        f.controller.request(listOf(song(1), song(2)), wholeList = true)

        f.controller.dismiss()

        assertEquals(TrashStep.Idle, f.controller.state.value.step)
        assertTrue(f.captured.isEmpty() && f.cleaned.isEmpty() && f.trashed.isEmpty())
        assertEquals(null, f.controller.state.value.notice)
    }

    @Test
    fun emptyRequestIsIgnored() {
        val f = fixture()
        f.controller.request(emptyList(), wholeList = true)
        assertEquals(TrashStep.Idle, f.controller.state.value.step)
    }

    @Test
    fun systemCancelRemovesNothingAndNeverCallsOnTrashed() {
        val f = fixture()
        f.controller.request(listOf(song(1)), wholeList = false)

        f.controller.onSystemResult(ok = false)

        assertEquals(TrashStep.Idle, f.controller.state.value.step)
        assertTrue(f.cleaned.isEmpty())
        assertTrue(f.trashed.isEmpty())
        assertEquals(null, f.controller.state.value.notice)
        f.controller.undo()
        assertEquals(TrashStep.Idle, f.controller.state.value.step)
    }

    @Test
    fun aPruneBetweenCaptureAndOkStillLetsUndoRestoreThePlaylistRows() {
        val f = fixture()
        val items = listOf(song(1), song(2))
        f.controller.request(items, wholeList = false)
        f.live = emptyMap() // the library watcher pruned the playlists after the system trash

        f.controller.onSystemResult(ok = true)
        assertEquals(TrashNotice(2, 2, TrashNoticeKind.Songs, canUndo = true), f.controller.state.value.notice)
        f.controller.undo()
        assertEquals(TrashStep.AwaitSystem(items, trash = false), f.controller.state.value.step)
        f.controller.onSystemResult(ok = true)

        assertEquals(listOf(rowsFor.getValue(1), rowsFor.getValue(2)), f.restored.single().playlistSongs)
    }

    @Test
    fun oneFailedItemIsReportedAndOnlyTheOthersAreCleaned() {
        val f = fixture()
        val items = listOf(song(1), song(2), song(3))
        f.notTrashed = setOf(song(2).uri)
        f.controller.request(items, wholeList = false)

        f.controller.onSystemResult(ok = true)

        val state = f.controller.state.value
        assertEquals(listOf(TrashFailure(TrashFailReason.NotTrashed, listOf(song(2)))), state.failures)
        assertEquals(3, state.requestedCount)
        assertEquals(listOf(listOf(song(1), song(3))), f.cleaned)
        assertEquals(listOf(listOf(song(1), song(3))), f.trashed)
        assertEquals(TrashNotice(2, 3, TrashNoticeKind.Songs, canUndo = true), state.notice)

        f.controller.undo()
        f.controller.onSystemResult(ok = true)
        // The failed item's row was captured but never trashed, so it is not restored.
        assertEquals(listOf(rowsFor.getValue(1), rowsFor.getValue(3)), f.restored.single().playlistSongs)

        f.controller.failedShown()
        assertTrue(f.controller.state.value.failures.isEmpty())
    }

    @Test
    fun singleItemSuccessShowsOneOfOneAndNoFailures() {
        val f = fixture()
        f.controller.request(listOf(song(1)), wholeList = false)

        f.controller.onSystemResult(ok = true)

        val state = f.controller.state.value
        assertEquals(TrashNotice(1, 1, TrashNoticeKind.Songs, canUndo = true), state.notice)
        assertTrue(state.failures.isEmpty())
        assertEquals(1, f.checkCalls)
    }

    @Test
    fun aCheckBeforeMediaStoreAppliedTheTrashIsRetried() {
        val f = fixture()
        f.scripted = mutableListOf(
            { uris -> uris.associateWith { PENDING } },
            { uris -> uris.associateWith { DONE } },
        )
        f.controller.request(listOf(song(1)), wholeList = false)

        f.controller.onSystemResult(ok = true)

        val state = f.controller.state.value
        assertEquals(2, f.checkCalls)
        assertTrue(state.failures.isEmpty())
        assertEquals(TrashNotice(1, 1, TrashNoticeKind.Songs, canUndo = true), state.notice)
    }

    @Test
    fun stillNotTrashedAfterMaxChecksIsAFailure() {
        val f = fixture()
        f.notTrashed = setOf(song(1).uri)
        f.controller.request(listOf(song(1)), wholeList = false)

        f.controller.onSystemResult(ok = true)

        assertEquals(3, f.checkCalls)
        assertEquals(
            listOf(TrashFailure(TrashFailReason.NotTrashed, listOf(song(1)))),
            f.controller.state.value.failures,
        )
    }

    @Test
    fun mixedSongsAndVideosGetTheItemsKind() {
        val f = fixture()
        val video = TrashItem(MediaKind.Video, 9, "content://media/external/video/media/9", "v9")
        f.controller.request(listOf(song(1), video), wholeList = false)

        f.controller.onSystemResult(ok = true)

        assertEquals(TrashNoticeKind.Items, f.controller.state.value.notice?.kind)
    }

    @Test
    fun undoOkShowsARestoredNoticeWithoutUndo() {
        val f = fixture()
        f.controller.request(listOf(song(1), song(2)), wholeList = false)
        f.controller.onSystemResult(ok = true)
        f.controller.undo()

        f.controller.onSystemResult(ok = true)

        assertEquals(
            TrashNotice(2, 2, TrashNoticeKind.Songs, restored = true, canUndo = false),
            f.controller.state.value.notice,
        )
    }

    @Test
    fun everyItemFailingShowsNoNoticeAndNoUndo() {
        val f = fixture()
        f.notTrashed = setOf(song(1).uri)
        f.controller.request(listOf(song(1)), wholeList = false)

        f.controller.onSystemResult(ok = true)

        assertEquals(null, f.controller.state.value.notice)
        assertTrue(f.trashed.isEmpty() && f.cleaned.isEmpty())
        f.controller.undo()
        assertEquals(TrashStep.Idle, f.controller.state.value.step)
    }

    @Test
    fun undoCancelKeepsItemsTrashedAndClearsTheNotice() {
        val f = fixture()
        f.controller.request(listOf(song(1)), wholeList = false)
        f.controller.onSystemResult(ok = true)
        f.controller.undo()

        f.controller.onSystemResult(ok = false)

        assertTrue(f.restored.isEmpty())
        assertEquals(null, f.controller.state.value.notice)
        assertEquals(TrashStep.Idle, f.controller.state.value.step)
    }

    @Test
    fun noticeShownClearsTheNotice() {
        val f = fixture()
        f.controller.request(listOf(song(1)), wholeList = false)
        f.controller.onSystemResult(ok = true)

        f.controller.noticeShown()

        assertEquals(null, f.controller.state.value.notice)
    }

    @Test
    fun unsupportedDevicesGetTheFlagAndNothingElse() {
        val f = fixture { supported = false }

        f.controller.request(listOf(song(1)), wholeList = false)

        assertTrue(f.controller.state.value.unsupported)
        assertEquals(TrashStep.Idle, f.controller.state.value.step)
        assertFalse(f.captured.isNotEmpty())
    }
}
