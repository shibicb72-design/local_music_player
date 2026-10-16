package com.shibi.musicplayer

import com.shibi.musicplayer.domain.PlaylistOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistOrderTest {
    private val ids = listOf(1L, 2L, 3L)

    @Test
    fun movedShiftsAnItemDownOrUp() {
        assertEquals(listOf(2L, 3L, 1L), PlaylistOrder.moved(ids, 0, 2))
        assertEquals(listOf(3L, 1L, 2L), PlaylistOrder.moved(ids, 2, 0))
        assertEquals(listOf(2L, 1L, 3L), PlaylistOrder.moved(ids, 0, 1))
    }

    @Test
    fun movedIgnoresANoOpOrAnIndexOutsideTheList() {
        assertEquals(ids, PlaylistOrder.moved(ids, 1, 1))
        assertEquals(ids, PlaylistOrder.moved(ids, -1, 0))
        assertEquals(ids, PlaylistOrder.moved(ids, 0, 5))
    }
}
