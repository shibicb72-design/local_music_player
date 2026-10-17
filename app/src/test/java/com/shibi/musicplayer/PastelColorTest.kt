package com.shibi.musicplayer

import com.shibi.musicplayer.domain.PastelColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PastelColorTest {
    @Test
    fun constrainClampsSaturationAndLightness() {
        val neon = PastelColor.constrain(12f, 1f, 0.4f)
        assertEquals(12f, neon.hue, 0.01f)
        assertEquals(PastelColor.MAX_SATURATION, neon.saturation, 0.001f)
        assertEquals(PastelColor.MIN_LIGHTNESS, neon.lightness, 0.001f)

        val washed = PastelColor.constrain(400f, 0.01f, 0.99f)
        assertEquals(40f, washed.hue, 0.01f)
        assertEquals(PastelColor.MIN_SATURATION, washed.saturation, 0.001f)
        assertEquals(PastelColor.MAX_LIGHTNESS, washed.lightness, 0.001f)
    }

    @Test
    fun pastelArgbStaysInRange() {
        val color = PastelColor.pastelArgb(hue = 330f, lightness = 0.82f)
        val hsl = PastelColor.fromArgb(color)
        assertTrue(hsl.saturation in PastelColor.MIN_SATURATION..PastelColor.MAX_SATURATION)
        assertTrue(hsl.lightness in PastelColor.MIN_LIGHTNESS..PastelColor.MAX_LIGHTNESS)
        assertTrue(PastelColor.isPastel(color))
    }

    @Test
    fun hueWrapsAndRoundTrips() {
        val argb = PastelColor.toArgb(-30f, 0.3f, 0.8f)
        val hsl = PastelColor.fromArgb(argb)
        assertEquals(330f, hsl.hue, 12f)
        assertEquals(0.3f, hsl.saturation, 0.08f)
        assertEquals(0.8f, hsl.lightness, 0.08f)
    }
}
