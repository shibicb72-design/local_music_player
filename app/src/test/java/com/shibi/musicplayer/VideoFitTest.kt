package com.shibi.musicplayer

import com.shibi.musicplayer.domain.AspectMode
import com.shibi.musicplayer.domain.VideoFit
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFitTest {

    private val delta = 0.001f

    @Test
    fun fitLetterboxesWidescreenInSquareView() {
        val scale = VideoFit.scale(AspectMode.Fit, 1080, 1080, 1920, 1080)
        assertEquals(1f, scale.scaleX, delta)
        assertEquals(0.5625f, scale.scaleY, delta)
    }

    @Test
    fun fitLetterboxesPortraitInLandscapeView() {
        val scale = VideoFit.scale(AspectMode.Fit, 1920, 1080, 1080, 1920)
        assertEquals(0.3164f, scale.scaleX, delta)
        assertEquals(1f, scale.scaleY, delta)
    }

    @Test
    fun fillCropsWidescreenInSquareView() {
        val scale = VideoFit.scale(AspectMode.Fill, 1080, 1080, 1920, 1080)
        assertEquals(1.7778f, scale.scaleX, delta)
        assertEquals(1f, scale.scaleY, delta)
    }

    @Test
    fun stretchIsIdentity() {
        val scale = VideoFit.scale(AspectMode.Stretch, 1080, 1920, 1920, 1080)
        assertEquals(1f, scale.scaleX, delta)
        assertEquals(1f, scale.scaleY, delta)
    }

    @Test
    fun ratio4x3LetterboxesInside1600x900View() {
        val scale = VideoFit.scale(AspectMode.Ratio4x3, 1600, 900, 1920, 1080)
        assertEquals(0.75f, scale.scaleX, delta)
        assertEquals(1f, scale.scaleY, delta)
    }

    @Test
    fun originalScalesPixelForPixel() {
        val scale = VideoFit.scale(AspectMode.Original, 1920, 1080, 960, 540)
        assertEquals(0.5f, scale.scaleX, delta)
        assertEquals(0.5f, scale.scaleY, delta)
    }

    @Test
    fun anamorphicFitUsesThePixelAspect() {
        val scale = VideoFit.scale(AspectMode.Fit, 1920, 1080, 720, 576, 64, 45)
        val displayAspect = 720f * (64f / 45f) / 576f
        val viewAspect = 1920f / 1080f
        assertEquals(displayAspect / viewAspect, scale.scaleX, delta)
        assertEquals(1f, scale.scaleY, delta)
    }

    @Test
    fun zeroVideoSizeIsIdentity() {
        val scale = VideoFit.scale(AspectMode.Fit, 1920, 1080, 0, 0)
        assertEquals(1f, scale.scaleX, delta)
        assertEquals(1f, scale.scaleY, delta)
    }
}
