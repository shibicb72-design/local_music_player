package com.shibi.musicplayer

import com.shibi.musicplayer.domain.PlaybackCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCheckTest {

    @Test
    fun framesShownPerSecondOverTheWindow() {
        assertEquals(30.0, PlaybackCheck.fps(100, 250, 5_000L), 0.0)
    }

    @Test
    fun zeroWindowReadsZero() {
        assertEquals(0.0, PlaybackCheck.fps(100, 250, 0L), 0.0)
    }

    @Test
    fun summaryNamesFramesLostAndHardwareDecodingOn() {
        assertEquals(
            "Playback check: 29.8 fps shown, 0 frames lost, hardware decoding on",
            PlaybackCheck.summary(29.84, 0, true),
        )
    }

    @Test
    fun summaryNamesFramesLostAndHardwareDecodingOff() {
        val line = PlaybackCheck.summary(12.0, 7, false)
        assertTrue(line.contains("7 frames lost"))
        assertTrue(line.contains("hardware decoding off"))
    }
}
