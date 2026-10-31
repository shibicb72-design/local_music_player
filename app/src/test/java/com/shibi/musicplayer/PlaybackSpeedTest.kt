package com.shibi.musicplayer

import com.shibi.musicplayer.domain.PlaybackSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedTest {

    @Test
    fun theOptionsRunFromHalfToDouble() {
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f), PlaybackSpeed.Options)
        assertEquals(1f, PlaybackSpeed.Default, 0f)
    }

    @Test
    fun labelsUseTheMultiplicationSign() {
        assertEquals("0.5×", PlaybackSpeed.label(0.5f))
        assertEquals("0.75×", PlaybackSpeed.label(0.75f))
        assertEquals("1×", PlaybackSpeed.label(1f))
        assertEquals("1.25×", PlaybackSpeed.label(1.25f))
        assertEquals("1.5×", PlaybackSpeed.label(1.5f))
        assertEquals("2×", PlaybackSpeed.label(2f))
    }

    @Test
    fun coerceSnapsToTheNearestOption() {
        assertEquals(1.5f, PlaybackSpeed.coerce(1.4f), 0f)
        assertEquals(2f, PlaybackSpeed.coerce(9f), 0f)
        assertEquals(0.5f, PlaybackSpeed.coerce(0f), 0f)
        assertEquals(1f, PlaybackSpeed.coerce(1f), 0f)
        assertEquals(1f, PlaybackSpeed.coerce(Float.NaN), 0f)
    }
}
