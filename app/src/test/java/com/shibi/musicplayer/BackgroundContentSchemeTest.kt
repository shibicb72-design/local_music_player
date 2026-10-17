package com.shibi.musicplayer

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.BackgroundCatalog
import com.shibi.musicplayer.domain.BackgroundContrast
import com.shibi.musicplayer.ui.background.DarkBackgroundChrome
import com.shibi.musicplayer.ui.background.LightBackgroundChrome
import com.shibi.musicplayer.ui.background.backgroundContentScheme
import com.shibi.musicplayer.ui.background.chromeFor
import com.shibi.musicplayer.ui.theme.MusicDarkColors
import com.shibi.musicplayer.ui.theme.MusicLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundContentSchemeTest {
    @Test
    fun defaultBackdropWithDarkThemeOffKeepsLightText() {
        val scheme = schemeFor(AppBackground.Default, MusicLightColors)
        val page = BackgroundCatalog.DEFAULT_ARGB
        assertReadable(scheme.onBackground, page)
        assertReadable(scheme.onSurfaceVariant, page)
        assertReadable(scheme.onSurface, scheme.surface.argb())
        assertReadable(scheme.onSurface, scheme.surfaceContainer.argb())
    }

    @Test
    fun defaultBackdropWithDarkThemeOnIsTheDarkScheme() {
        assertEquals(MusicDarkColors, schemeFor(AppBackground.Default, MusicDarkColors))
    }

    @Test
    fun pastelKeepsDarkTextWhicheverThemeFlag() {
        val pastel = AppBackground.Solid("blush")
        val page = BackgroundCatalog.sampleArgb(pastel, emptyMap())
        listOf(MusicDarkColors, MusicLightColors).forEach { base ->
            val scheme = schemeFor(pastel, base)
            assertReadable(scheme.onBackground, page)
            assertReadable(scheme.onSurfaceVariant, page)
        }
    }

    @Test
    fun accentTextIsReadableOnEveryLightCatalogColorAndTheLightSheet() {
        val accent = LightBackgroundChrome.accentText.argb()
        val lightColors = (
            BackgroundCatalog.solids.map { it.argb } +
                BackgroundCatalog.blends.flatMap { it.colors } +
                BackgroundCatalog.scenes.map { it.defaults.first() }
            ).filter { BackgroundContrast.isLight(it) }
        assertTrue(lightColors.isNotEmpty())
        (lightColors + LightBackgroundChrome.sheet.argb()).forEach { argb ->
            assertReadable(accent, argb)
        }
    }

    @Test
    fun darkAccentTextIsUnchangedLilac() {
        assertEquals(Color(0xFFB9A8FF), DarkBackgroundChrome.accentText)
        assertReadable(DarkBackgroundChrome.accentText.argb(), BackgroundCatalog.DEFAULT_ARGB)
    }

    private fun schemeFor(background: AppBackground, base: ColorScheme): ColorScheme {
        val roles = BackgroundContrast.rolesFor(background)
        return backgroundContentScheme(base, roles, chromeFor(roles))
    }

    private fun assertReadable(content: Color, containerArgb: Long) =
        assertReadable(content.argb(), containerArgb)

    private fun assertReadable(contentArgb: Long, containerArgb: Long) {
        val ratio = BackgroundContrast.contrastRatio(contentArgb, containerArgb)
        assertTrue(
            "contrast %.2f for %08X on %08X".format(ratio, contentArgb, containerArgb),
            ratio >= BackgroundContrast.MIN_BODY_CONTRAST,
        )
    }

    private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL
}
