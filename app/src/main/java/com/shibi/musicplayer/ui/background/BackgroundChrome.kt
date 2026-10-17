package com.shibi.musicplayer.ui.background

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.BackgroundCatalog
import com.shibi.musicplayer.domain.BackgroundContrast
import com.shibi.musicplayer.ui.theme.Accent
import com.shibi.musicplayer.ui.theme.MusicDarkColors
import com.shibi.musicplayer.ui.theme.TextHigh

data class BackgroundChrome(
    val isLight: Boolean,
    val carouselIcon: Color,
    val carouselTile: Color,
    val carouselTileBorder: Color,
    val cardBorder: Color,
    val frost: Color,
    val frostBorder: Color,
    val iconMuted: Color,
    val progress: Color,
    val progressTrack: Color,
    val rowFill: Color,
    val playingRowFill: Color,
    val sampleArgb: Long,
    val sheet: Color,
    val dialog: Color,
    val pageFill: Color,
    /** Accent for text and small icons on the page, row, and sheet. */
    val accentText: Color,
)

val DarkBackgroundChrome = BackgroundChrome(
    isLight = false,
    carouselIcon = Color.White.copy(alpha = 0.92f),
    carouselTile = Color.White.copy(alpha = 0.12f),
    carouselTileBorder = Color.White.copy(alpha = 0.22f),
    cardBorder = Color.White.copy(alpha = 0.08f),
    frost = Color(0xFF24242E).copy(alpha = 0.92f),
    frostBorder = Color.White.copy(alpha = 0.10f),
    iconMuted = Color.White.copy(alpha = 0.92f),
    progress = Color.White,
    progressTrack = Color.White.copy(alpha = 0.12f),
    rowFill = Color.Transparent,
    playingRowFill = Accent.copy(alpha = 0.12f),
    sampleArgb = BackgroundCatalog.DEFAULT_ARGB,
    sheet = Color(0xFF2E2E3A),
    dialog = Color(0xFF24242E),
    pageFill = Color(0xFF0E0E12),
    accentText = Color(0xFFB9A8FF),
)

val LightBackgroundChrome = BackgroundChrome(
    isLight = true,
    carouselIcon = Color(0xFF1B1240),
    carouselTile = Color.White.copy(alpha = 0.78f),
    carouselTileBorder = Color(0xFF1B1240).copy(alpha = 0.16f),
    cardBorder = Color(0xFF1B1240).copy(alpha = 0.10f),
    frost = Color.White.copy(alpha = 0.80f),
    frostBorder = Color(0xFF1B1240).copy(alpha = 0.10f),
    iconMuted = Color(0xFF3A3450),
    progress = Color(0xFF5B45C6),
    progressTrack = Color(0xFF5B45C6).copy(alpha = 0.22f),
    rowFill = Color.White.copy(alpha = 0.58f),
    playingRowFill = Color(0xFF6E4EF0).copy(alpha = 0.16f),
    sampleArgb = 0xFFF8C8D4,
    sheet = Color(0xFFF7F4FB),
    dialog = Color(0xFFFBF8FF),
    pageFill = Color(0xFFF8C8D4),
    accentText = Color(0xFF4A34B0),
)

val LocalBackgroundChrome = staticCompositionLocalOf { DarkBackgroundChrome }

fun chromeFor(roles: BackgroundContrast.Roles): BackgroundChrome {
    val base = if (roles.isLight) LightBackgroundChrome else DarkBackgroundChrome
    return base.copy(
        sampleArgb = roles.sampleArgb,
        pageFill = roles.sampleArgb.toComposeColor(),
    )
}

fun Long.toComposeColor(): Color = Color((this and 0xFFFFFFFFL).toInt())

@Composable
fun BackgroundContentTheme(
    background: AppBackground,
    customColors: Map<String, List<Long>>,
    content: @Composable () -> Unit,
) {
    val sample = remember(background, customColors) {
        BackgroundCatalog.sampleArgb(background, customColors)
    }
    val roles = remember(sample) { BackgroundContrast.rolesFor(background, customColors) }
    val chrome = remember(roles) { chromeFor(roles) }
    val scheme = backgroundContentScheme(MaterialTheme.colorScheme, roles, chrome)
    CompositionLocalProvider(LocalBackgroundChrome provides chrome) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * Content roles follow the backdrop, not the theme flag. A dark backdrop gets
 * the dark scheme even when the theme is light, so text never goes dark-on-dark.
 */
internal fun backgroundContentScheme(
    base: ColorScheme,
    roles: BackgroundContrast.Roles,
    chrome: BackgroundChrome,
    darkScheme: ColorScheme = MusicDarkColors,
): ColorScheme {
    return if (roles.isLight) {
        base.copy(
            onBackground = roles.onBackgroundArgb.toComposeColor(),
            surface = chrome.dialog,
            onSurface = roles.onSurfaceArgb.toComposeColor(),
            surfaceVariant = Color(0xFFE8E4F2),
            onSurfaceVariant = roles.onVariantArgb.toComposeColor(),
            surfaceContainer = roles.frostArgb.toComposeColor(),
            surfaceContainerLow = Color.White.copy(alpha = 0.72f),
            surfaceContainerHigh = chrome.sheet,
            surfaceContainerHighest = Color.White.copy(alpha = 0.92f),
            inverseSurface = Color(0xFF16161C),
            inverseOnSurface = Color(0xFFF4F4F8),
            outline = Color(0xFF7A7588),
            outlineVariant = Color(0xFFD4D0DE),
        )
    } else {
        darkScheme
    }
}

internal val DefaultOnBackground: Color = TextHigh
