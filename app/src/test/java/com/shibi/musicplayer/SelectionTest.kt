package com.shibi.musicplayer

import com.shibi.musicplayer.domain.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {
    @Test
    fun startActivatesWithOneId() {
        val s = Selection().start(5)
        assertTrue(s.active)
        assertEquals(setOf(5L), s.ids)
        assertEquals(1, s.count)
    }

    @Test
    fun toggleAddsAndRemoves() {
        val s = Selection().start(1).toggle(2)
        assertEquals(setOf(1L, 2L), s.ids)
        assertEquals(setOf(1L), s.toggle(2).ids)
    }

    @Test
    fun toggleLastIdExits() {
        val s = Selection().start(1).toggle(1)
        assertEquals(Selection(), s)
        assertFalse(s.active)
    }

    @Test
    fun selectAllAndEmpty() {
        val s = Selection().selectAll(listOf(1L, 2L, 2L))
        assertTrue(s.active)
        assertEquals(setOf(1L, 2L), s.ids)
        assertEquals(Selection(), Selection().selectAll(emptyList()))
    }

    @Test
    fun clearResets() {
        assertEquals(Selection(), Selection().start(1).clear())
    }

    @Test
    fun retainDropsMissingIds() {
        val s = Selection().selectAll(listOf(1L, 2L, 3L)).retain(setOf(2L, 3L, 9L))
        assertEquals(setOf(2L, 3L), s.ids)
        assertTrue(s.active)
    }

    @Test
    fun retainExitsWhenNoneLeft() {
        assertEquals(Selection(), Selection().start(1).retain(setOf(2L)))
    }

    @Test
    fun retainInactiveUnchanged() {
        val s = Selection()
        assertEquals(s, s.retain(setOf(1L)))
    }

    @Test
    fun isWholeListCases() {
        val all = listOf(1L, 2L)
        assertTrue(Selection().selectAll(all).isWholeList(all))
        assertFalse(Selection().start(1).isWholeList(all))
        assertFalse(Selection().isWholeList(all))
        assertFalse(Selection().start(1).isWholeList(emptyList()))
    }
}
