package com.shibi.musicplayer

import com.shibi.musicplayer.domain.ItemCheck
import com.shibi.musicplayer.domain.MediaKind
import com.shibi.musicplayer.domain.TrashFailReason
import com.shibi.musicplayer.domain.TrashFailure
import com.shibi.musicplayer.domain.TrashItem
import com.shibi.musicplayer.domain.TrashResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashResultTest {
    private fun video(id: Long) = TrashItem(MediaKind.Video, id, "content://v/$id", "v$id")

    private val trashed = ItemCheck(found = true, trashed = true, volumeName = TrashResult.PRIMARY_VOLUME)
    private val notTrashed = ItemCheck(found = true, trashed = false, volumeName = TrashResult.PRIMARY_VOLUME)

    private fun checks(vararg pairs: Pair<TrashItem, ItemCheck>) = pairs.associate { it.first.uri to it.second }

    @Test
    fun allTrashedHasNoFailures() {
        val items = (1L..5L).map(::video)
        val outcome = TrashResult.classify(items, items.associate { it.uri to trashed })
        assertEquals(items, outcome.trashed)
        assertTrue(outcome.failures.isEmpty())
        assertEquals(0, outcome.failedCount)
    }

    @Test
    fun singleTrashedItemIsNotAFailure() {
        val outcome = TrashResult.classify(listOf(video(1)), checks(video(1) to trashed))
        assertEquals(listOf(video(1)), outcome.trashed)
        assertTrue(outcome.failures.isEmpty())
    }

    @Test
    fun anItemGoneEntirelyCountsAsSuccess() {
        val items = listOf(video(1), video(2))
        val outcome = TrashResult.classify(items, checks(video(1) to ItemCheck(found = false)))
        assertEquals(items, outcome.trashed)
        assertTrue(outcome.failures.isEmpty())
    }

    @Test
    fun partlyFailedReportsNotTrashed() {
        val items = (1L..5L).map(::video)
        val outcome = TrashResult.classify(
            items,
            checks(
                video(1) to trashed,
                video(2) to notTrashed,
                video(3) to trashed,
                video(4) to notTrashed,
                video(5) to trashed,
            ),
        )
        assertEquals(listOf(video(1), video(3), video(5)), outcome.trashed)
        assertEquals(listOf(TrashFailure(TrashFailReason.NotTrashed, listOf(video(2), video(4)))), outcome.failures)
        assertEquals(2, outcome.failedCount)
    }

    @Test
    fun removableStorageIsReportedAsRemovable() {
        val sd = ItemCheck(found = true, trashed = false, volumeName = "1234-ABCD")
        val outcome = TrashResult.classify(listOf(video(1)), checks(video(1) to sd))
        assertEquals(listOf(TrashFailure(TrashFailReason.Removable, listOf(video(1)))), outcome.failures)
    }

    @Test
    fun securityExceptionIsNoPermission() {
        val denied = ItemCheck(found = true, securityError = true)
        val outcome = TrashResult.classify(listOf(video(1)), checks(video(1) to denied))
        assertEquals(listOf(TrashFailure(TrashFailReason.NoPermission, listOf(video(1)))), outcome.failures)
        assertTrue(outcome.trashed.isEmpty())
    }

    @Test
    fun failuresAreGroupedInEnumOrderAndKeepRequestOrder() {
        val items = (1L..5L).map(::video)
        val sd = ItemCheck(found = true, trashed = false, volumeName = "1234-ABCD")
        val denied = ItemCheck(found = true, securityError = true)
        val outcome = TrashResult.classify(
            items,
            checks(
                video(1) to notTrashed,
                video(2) to denied,
                video(3) to sd,
                video(4) to notTrashed,
                video(5) to sd,
            ),
        )
        assertEquals(
            listOf(
                TrashFailure(TrashFailReason.Removable, listOf(video(3), video(5))),
                TrashFailure(TrashFailReason.NoPermission, listOf(video(2))),
                TrashFailure(TrashFailReason.NotTrashed, listOf(video(1), video(4))),
            ),
            outcome.failures,
        )
        assertEquals(5, outcome.failedCount)
    }

    @Test
    fun isSettled() {
        assertTrue(TrashResult.isSettled(null))
        assertTrue(TrashResult.isSettled(ItemCheck(found = false)))
        assertTrue(TrashResult.isSettled(trashed))
        assertTrue(TrashResult.isSettled(ItemCheck(found = true, securityError = true)))
        assertFalse(TrashResult.isSettled(notTrashed))
    }
}
