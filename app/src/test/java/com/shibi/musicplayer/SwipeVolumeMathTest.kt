package com.shibi.musicplayer

import com.shibi.musicplayer.domain.SwipeVolumeMath
import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeVolumeMathTest {

    private val height = 1_000f

    private fun after(start: Int, dragUp: Float, max: Int = 15) =
        SwipeVolumeMath.volumeAfterDrag(start, max, dragUp, height)

    @Test
    fun aFullHeightSwipeCoversTheWholeRange() {
        assertEquals(15, after(start = 0, dragUp = height))
        assertEquals(0, after(start = 15, dragUp = -height))
    }

    @Test
    fun upRaisesAndDownLowers() {
        // 15 steps over 1000 px: one step every 66.7 px.
        assertEquals(10, after(start = 8, dragUp = 140f))
        assertEquals(6, after(start = 8, dragUp = -140f))
    }

    @Test
    fun clampsAtBothEnds() {
        assertEquals(15, after(start = 12, dragUp = 3 * height))
        assertEquals(0, after(start = 3, dragUp = -3 * height))
    }

    @Test
    fun tinyDragsSnapToTheNearestStep() {
        // Under half a step (33 px) is no change; just over it is one step.
        assertEquals(8, after(start = 8, dragUp = 20f))
        assertEquals(8, after(start = 8, dragUp = -20f))
        assertEquals(9, after(start = 8, dragUp = 40f))
        assertEquals(7, after(start = 8, dragUp = -40f))
    }

    @Test
    fun theStartIsClampedIntoRange() {
        assertEquals(15, after(start = 99, dragUp = 0f))
        assertEquals(0, after(start = -3, dragUp = 0f))
    }

    @Test
    fun degenerateInputsKeepTheVolume() {
        assertEquals(8, SwipeVolumeMath.volumeAfterDrag(8, 15, 500f, 0f))
        assertEquals(8, SwipeVolumeMath.volumeAfterDrag(8, 15, Float.NaN, height))
        assertEquals(0, SwipeVolumeMath.volumeAfterDrag(3, 0, 500f, height))
    }

    @Test
    fun theChipReadsLevelOverMaximum() {
        assertEquals("8 / 15", SwipeVolumeMath.label(8, 15))
        assertEquals("0 / 25", SwipeVolumeMath.label(0, 25))
    }
}
