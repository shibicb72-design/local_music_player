package com.shibi.musicplayer

import com.shibi.musicplayer.domain.PlaybackTime
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimeTest {
    @Test
    fun format_zeroIsZeroMinutesAndSeconds() {
        assertEquals("00:00", PlaybackTime.format(0L))
    }

    @Test
    fun format_floorsPartialSeconds() {
        assertEquals("00:00", PlaybackTime.format(999L))
        assertEquals("00:01", PlaybackTime.format(1_000L))
        assertEquals("00:01", PlaybackTime.format(1_999L))
    }

    @Test
    fun format_rollsSecondsIntoMinutes() {
        assertEquals("01:01", PlaybackTime.format(61_000L))
        assertEquals("59:59", PlaybackTime.format((59 * 60 + 59) * 1_000L))
    }

    @Test
    fun format_keepsMinutesPastOneHour() {
        assertEquals("60:00", PlaybackTime.format(3_600_000L))
        assertEquals("61:01", PlaybackTime.format(3_661_000L))
    }

    @Test
    fun format_negativeDurationIsZero() {
        assertEquals("00:00", PlaybackTime.format(-1L))
        assertEquals("00:00", PlaybackTime.format(Long.MIN_VALUE))
    }

    @Test
    fun clampSeek_limitsPositionToTheTrack() {
        assertEquals(0L, PlaybackTime.clampSeek(-20L, 10_000L))
        assertEquals(4_000L, PlaybackTime.clampSeek(4_000L, 10_000L))
        assertEquals(10_000L, PlaybackTime.clampSeek(12_000L, 10_000L))
    }

    @Test
    fun clampSeek_unknownDurationStaysAtZero() {
        assertEquals(0L, PlaybackTime.clampSeek(1_500L, 0L))
        assertEquals(0L, PlaybackTime.clampSeek(1_500L, -1L))
    }
}
