package com.shibi.musicplayer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.PlaylistDetailContent
import com.shibi.musicplayer.ui.PlaylistSongRow
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Long-press selection in the Songs list, the Videos list and a playlist. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SelectionUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val tracks = listOf(
        Track(1L, "Alpha", "Artist One", "Album A", 100_000L, "content://tracks/1", null),
        Track(2L, "Bravo", "Artist Two", "Album B", 110_000L, "content://tracks/2", null),
        Track(3L, "Charlie", "Artist Three", "Album C", 120_000L, "content://tracks/3", null),
        Track(4L, "Delta", "Artist Four", "Album D", 130_000L, "content://tracks/4", null),
    )

    private fun songs(onTrash: (List<Track>, Boolean) -> Unit = { _, _ -> }) {
        compose.setContent {
            MusicPlayerTheme {
                LibraryContent(
                    tracks = tracks,
                    nowPlaying = PlayerUiState(),
                    showMiniPlayer = false,
                    onTrackSelected = { _, _ -> },
                    onOpenPlayer = {},
                    onTogglePlayPause = {},
                    onTrashSongs = onTrash,
                    contentBottomPadding = 0.dp,
                )
            }
        }
    }

    @Test
    fun songsLongPressTapSelectAllAndTrash() {
        var trashed: List<Track>? = null
        var whole: Boolean? = null
        songs { list, all ->
            trashed = list
            whole = all
        }
        compose.onNodeWithText("Alpha").performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithText("Bravo").performClick()
        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("4 selected").assertExists()
        compose.onNodeWithContentDescription("Move to trash").performClick()
        assertEquals(tracks, trashed)
        assertEquals(true, whole)
        compose.onNodeWithText("4 selected").assertDoesNotExist()
    }

    @Test
    fun songsTrashOfSomeIsNotTheWholeList() {
        var trashed: List<Track>? = null
        var whole: Boolean? = null
        songs { list, all ->
            trashed = list
            whole = all
        }
        compose.onNodeWithText("Charlie").performTouchInput { longClick() }
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithContentDescription("Move to trash").performClick()
        assertEquals(listOf(tracks[0], tracks[2]), trashed)
        assertEquals(false, whole)
    }

    @Test
    fun songsCancelExitsSelection() {
        songs()
        compose.onNodeWithText("Alpha").performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithContentDescription("Cancel selection").performClick()
        compose.onNodeWithText("1 selected").assertDoesNotExist()
    }

    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun videosLongPressSelectsAndTapDoesNotOpen() {
        val opened = mutableListOf<Video>()
        compose.setContent {
            MusicPlayerTheme {
                CompositionLocalProvider(
                    LocalInspectionMode provides true,
                    LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
                ) {
                    VideosContent(
                        state = VideosScreenshotTest.full,
                        onVideoClick = { opened += it },
                        onRequestAccess = {},
                        onOpenSettings = {},
                        contentBottomPadding = 0.dp,
                    )
                }
            }
        }
        compose.onNodeWithText("Beach day").performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithText("Concert encore").performClick()
        compose.onNodeWithText("2 selected").assertExists()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun playlistSelectTwoAndRemove() {
        val removed = mutableListOf<Long>()
        val rows = listOf(10L to "One", 11L to "Two", 12L to "Three").map { (id, title) ->
            PlaylistSongRow(id, title, "Someone", null, null, false)
        }
        compose.setContent {
            MusicPlayerTheme {
                PlaylistDetailContent(
                    name = "Mix",
                    songs = rows,
                    onBack = {},
                    onPlayAll = {},
                    onShuffle = {},
                    onRenameClick = {},
                    onDeleteClick = {},
                    onRemove = { removed += it },
                    onReorder = {},
                    onPlayFrom = {},
                    contentBottomPadding = 0.dp,
                )
            }
        }
        compose.onNodeWithText("One").performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertExists()
        compose.onNodeWithText("Three").performClick()
        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithText("Remove from playlist").performClick()
        assertEquals(listOf(10L, 12L), removed)
        compose.onNodeWithText("2 selected").assertDoesNotExist()
    }
}
