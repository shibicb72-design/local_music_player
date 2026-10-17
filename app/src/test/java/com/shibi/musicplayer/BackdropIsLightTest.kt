package com.shibi.musicplayer

import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.ui.theme.backdropIsLight
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** System-bar icons are dark exactly when the painted backdrop is light. */
class BackdropIsLightTest {

    @Test
    fun darkThemeIsDarkWhateverTheChoice() {
        assertFalse(backdropIsLight(true, AppBackground.Default, emptyMap()))
        assertFalse(backdropIsLight(true, AppBackground.Solid("blush"), emptyMap()))
    }

    @Test
    fun pastelWithDarkThemeOffIsLight() {
        assertTrue(backdropIsLight(false, AppBackground.Solid("blush"), emptyMap()))
        assertTrue(backdropIsLight(false, AppBackground.Gradient("cotton_candy"), emptyMap()))
        assertTrue(backdropIsLight(false, AppBackground.Animated("clouds"), emptyMap()))
    }

    @Test
    fun defaultBackdropStaysDarkWithDarkThemeOff() {
        assertFalse(backdropIsLight(false, AppBackground.Default, emptyMap()))
    }

    @Test
    fun fineTunedDarkSceneColorsCount() {
        val dark = listOf(0xFF101018, 0xFF181820, 0xFF202028)
        assertFalse(backdropIsLight(false, AppBackground.Animated("clouds"), mapOf("clouds" to dark)))
    }
}
