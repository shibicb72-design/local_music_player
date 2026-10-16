package com.shibi.musicplayer.ui.background

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
)

val LocalBackgroundChrome = staticCompositionLocalOf { DarkBackgroundChrome }

fun chromeFor(roles: BackgroundContrast.Roles): BackgroundChrome {
    return if (roles.isLight) LightBackgroundChrome else DarkBackgroundChrome
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
    val base = MaterialTheme.colorScheme
    val scheme = if (roles.isLight) {
        base.copy(
            onBackground = roles.onBackgroundArgb.toComposeColor(),
            onSurface = roles.onSurfaceArgb.toComposeColor(),
            onSurfaceVariant = roles.onVariantArgb.toComposeColor(),
            surfaceContainer = roles.frostArgb.toComposeColor(),
            surfaceContainerHigh = Color.White.copy(alpha = 0.86f),
            surfaceContainerHighest = Color.White.copy(alpha = 0.92f),
            outline = Color(0xFF7A7588),
            outlineVariant = Color(0xFFD4D0DE),
        )
    } else {
        base
    }
    CompositionLocalProvider(LocalBackgroundChrome provides chrome) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

internal val DefaultOnBackground: Color = TextHigh
