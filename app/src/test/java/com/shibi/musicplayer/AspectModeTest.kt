package com.shibi.musicplayer

import com.shibi.musicplayer.domain.AspectMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AspectModeTest {

    @Test
    fun eachModeMapsToItsLibVlcScale() {
        assertEquals(
            listOf(
                "SURFACE_BEST_FIT",
                "SURFACE_FIT_SCREEN",
                "SURFACE_FILL",
                "SURFACE_16_9",
                "SURFACE_4_3",
                "SURFACE_ORIGINAL",
            ),
            AspectMode.entries.map { it.vlcScale },
        )
    }

    /** The engine resolves the name with `ScaleType.valueOf`; a typo would throw there. */
    @Test
    fun everyScaleNameIsARealLibVlcScaleType() {
        val names = org.videolan.libvlc.MediaPlayer.ScaleType.entries.map { it.name }.toSet()
        AspectMode.entries.forEach { mode ->
            assertEquals("$mode", true, mode.vlcScale in names)
        }
    }

    @Test
    fun cyclesInOrderAndWrapsToFit() {
        assertEquals(
            listOf(
                AspectMode.Fill,
                AspectMode.Stretch,
                AspectMode.Ratio16x9,
                AspectMode.Ratio4x3,
                AspectMode.Original,
                AspectMode.Fit,
            ),
            AspectMode.entries.map { it.next() },
        )
    }

    @Test
    fun fitIsTheDefault() {
        assertEquals(AspectMode.Fit, AspectMode.Default)
    }

    @Test
    fun storedNamesRoundTripAndUnknownFallsBackToFit() {
        AspectMode.entries.forEach { mode ->
            assertEquals(mode, AspectMode.decode(mode.encode()))
        }
        assertEquals(AspectMode.Fit, AspectMode.decode(null))
        assertEquals(AspectMode.Fit, AspectMode.decode("Zoom"))
    }
}
