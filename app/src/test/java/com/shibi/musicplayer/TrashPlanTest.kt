package com.shibi.musicplayer

import com.shibi.musicplayer.domain.MediaKind
import com.shibi.musicplayer.domain.TrashItem
import com.shibi.musicplayer.domain.TrashPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashPlanTest {
    private fun song(id: Long) = TrashItem(MediaKind.Song, id, "content://s/$id", "s$id")
    private fun video(id: Long) = TrashItem(MediaKind.Video, id, "content://v/$id", "v$id")

    @Test
    fun groupWarning() {
        assertTrue(TrashPlan.needsGroupWarning(2, true))
        assertFalse(TrashPlan.needsGroupWarning(1, true))
        assertFalse(TrashPlan.needsGroupWarning(5, false))
    }

    @Test
    fun failedKeepsRequestOrder() {
        val req = listOf(song(3), song(1), song(2))
        val out = TrashPlan.failed(req, setOf("content://s/2", "content://s/3"))
        assertEquals(listOf(song(3), song(2)), out)
        assertTrue(TrashPlan.failed(req, emptySet()).isEmpty())
    }

    @Test
    fun queueIndicesDescendingWithDuplicates() {
        val q = listOf("1", "2", "1", "x", "3", "1")
        assertEquals(listOf(5, 2, 0), TrashPlan.queueIndicesToRemove(q, setOf(1L)))
        assertEquals(listOf(4, 1), TrashPlan.queueIndicesToRemove(q, setOf(2L, 3L)))
    }

    @Test
    fun queueIgnoresUnparsable() {
        assertTrue(TrashPlan.queueIndicesToRemove(listOf("a", "", "content://x"), setOf(1L)).isEmpty())
    }

    @Test
    fun trashesCurrentVideo() {
        val items = listOf(song(1), video(2))
        assertTrue(TrashPlan.trashesCurrentVideo("content://v/2", items))
        assertFalse(TrashPlan.trashesCurrentVideo("content://s/1", items))
        assertFalse(TrashPlan.trashesCurrentVideo(null, items))
    }

    @Test
    fun missingGuards() {
        assertTrue(TrashPlan.missingFromLibrary(setOf(1L), setOf(2L), false).isEmpty())
        assertTrue(TrashPlan.missingFromLibrary(setOf(1L), emptySet(), true).isEmpty())
    }

    @Test
    fun missingBasic() {
        assertEquals(setOf(1L), TrashPlan.missingFromLibrary(setOf(1L, 2L), setOf(2L), true))
    }

    @Test
    fun missingLimit() {
        val library = setOf(1000L)
        val s51 = (1L..51L).toSet() + 1000L
        assertTrue(TrashPlan.missingFromLibrary(s51, library, true).isEmpty())
        val s50 = (1L..50L).toSet() + 1000L
        assertEquals((1L..50L).toSet(), TrashPlan.missingFromLibrary(s50, library, true))
    }

    @Test
    fun negativeIdsNeverMissing() {
        assertTrue(TrashPlan.missingFromLibrary(setOf(-1L, -2L), setOf(5L), true).isEmpty())
    }
}
