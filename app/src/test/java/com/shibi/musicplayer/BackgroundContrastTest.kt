package com.shibi.musicplayer

import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.BackgroundCatalog
import com.shibi.musicplayer.domain.BackgroundContrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundContrastTest {
    @Test
    fun luminanceOfWhiteAndBlack() {
        assertEquals(1.0, BackgroundContrast.relativeLuminance(0xFFFFFFFF), 0.001)
        assertEquals(0.0, BackgroundContrast.relativeLuminance(0xFF000000), 0.001)
    }

    @Test
    fun defaultBackdropUsesLightContent() {
        val roles = BackgroundContrast.roles(BackgroundCatalog.DEFAULT_ARGB)
        assertFalse(roles.isLight)
        assertEquals(BackgroundContrast.ON_DARK, roles.onBackgroundArgb)
        assertTrue(
            BackgroundContrast.contrastRatio(
                roles.onBackgroundArgb,
                roles.sampleArgb,
            ) >= BackgroundContrast.MIN_BODY_CONTRAST,
        )
    }

    @Test
    fun everySolidPastelUsesDarkContent() {
        BackgroundCatalog.solids.forEach { swatch ->
            val roles = BackgroundContrast.roles(swatch.argb)
            assertTrue("${swatch.id} should be light", roles.isLight)
            assertEquals(BackgroundContrast.ON_LIGHT, roles.onBackgroundArgb)
            assertEquals(BackgroundContrast.ON_LIGHT, BackgroundContrast.contentArgb(swatch.argb))
            assertTrue(
                "${swatch.id} contrast",
                BackgroundContrast.contrastRatio(roles.onBackgroundArgb, swatch.argb) >=
                    BackgroundContrast.MIN_BODY_CONTRAST,
            )
        }
    }

    @Test
    fun blendsAndScenesPickDarkText() {
        BackgroundCatalog.blends.forEach { blend ->
            val roles = BackgroundContrast.rolesFor(AppBackground.Gradient(blend.id))
            assertTrue(blend.id, roles.isLight)
            assertEquals(BackgroundContrast.ON_LIGHT, roles.onBackgroundArgb)
        }
        BackgroundCatalog.scenes.forEach { scene ->
            val roles = BackgroundContrast.rolesFor(AppBackground.Animated(scene.id))
            assertTrue(scene.id, roles.isLight)
            assertEquals(BackgroundContrast.ON_LIGHT, roles.onBackgroundArgb)
        }
    }

    @Test
    fun neverChoosesLightOnLightOrDarkOnDark() {
        val light = BackgroundContrast.contentArgb(0xFFF8C8D4)
        val dark = BackgroundContrast.contentArgb(0xFF0E0E12)
        assertEquals(BackgroundContrast.ON_LIGHT, light)
        assertEquals(BackgroundContrast.ON_DARK, dark)
        assertTrue(BackgroundContrast.contrastRatio(light, 0xFFF8C8D4) >
            BackgroundContrast.contrastRatio(BackgroundContrast.ON_DARK, 0xFFF8C8D4))
        assertTrue(BackgroundContrast.contrastRatio(dark, 0xFF0E0E12) >
            BackgroundContrast.contrastRatio(BackgroundContrast.ON_LIGHT, 0xFF0E0E12))
    }

    @Test
    fun customSceneColorsStillResolve() {
        val custom = mapOf("waves" to listOf(0xFFF5EDE0, 0xFFF8C8D4, 0xFFC8EDE0))
        val roles = BackgroundContrast.rolesFor(AppBackground.Animated("waves"), custom)
        assertTrue(roles.isLight)
        assertEquals(BackgroundContrast.ON_LIGHT, roles.onBackgroundArgb)
    }

    @Test
    fun nowPlayingPaneDoesNotUseWindowContentColor() {
        val window = 0xFFC5E4F7L
        val darkPane = BackgroundCatalog.DEFAULT_ARGB
        val windowContent = BackgroundContrast.contentArgb(window)
        val paneContent = BackgroundContrast.contentArgb(darkPane)
        assertEquals(BackgroundContrast.ON_LIGHT, windowContent)
        assertEquals(BackgroundContrast.ON_DARK, paneContent)
        assertFalse(
            "window onBackground on a dark Now Playing pane is dark-on-dark",
            BackgroundContrast.isReadable(windowContent, darkPane),
        )
        assertTrue(BackgroundContrast.isReadable(paneContent, darkPane))
        val paneRoles = BackgroundContrast.rolesForContainer(darkPane, behindArgb = window)
        assertFalse(paneRoles.isLight)
        assertEquals(BackgroundContrast.ON_DARK, paneRoles.onBackgroundArgb)
        assertTrue(paneRoles.onBackgroundArgb != windowContent)
    }

    @Test
    fun frostCardOverPastelKeepsDarkText() {
        val window = 0xFFC5E4F7L
        val painted = BackgroundContrast.compositeOver(BackgroundContrast.FROST_LIGHT, window)
        assertTrue(BackgroundContrast.isLight(painted))
        val roles = BackgroundContrast.rolesForContainer(
            BackgroundContrast.FROST_LIGHT,
            behindArgb = window,
        )
        assertEquals(BackgroundContrast.ON_LIGHT, roles.onBackgroundArgb)
        assertTrue(BackgroundContrast.isReadable(roles.onBackgroundArgb, painted))
    }

    @Test
    fun darkFrostOverPastelKeepsLightText() {
        val window = 0xFFC5E4F7L
        val painted = BackgroundContrast.compositeOver(BackgroundContrast.FROST_DARK, window)
        assertFalse(BackgroundContrast.isLight(painted))
        val roles = BackgroundContrast.rolesForContainer(
            BackgroundContrast.FROST_DARK,
            behindArgb = window,
        )
        assertEquals(BackgroundContrast.ON_DARK, roles.onBackgroundArgb)
        assertTrue(BackgroundContrast.isReadable(roles.onBackgroundArgb, painted))
    }
}
