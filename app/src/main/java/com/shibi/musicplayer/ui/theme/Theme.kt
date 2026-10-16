package com.shibi.musicplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val MusicDarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentContainer,
    onPrimaryContainer = OnAccentContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    tertiary = Secondary,
    background = Base0,
    onBackground = TextHigh,
    surface = Base1,
    onSurface = TextHigh,
    surfaceVariant = Base4,
    onSurfaceVariant = TextMed,
    inverseSurface = TextHigh,
    inverseOnSurface = Base0,
    error = Error,
    outline = Outline,
    outlineVariant = OutlineVariant,
    surfaceBright = Base4,
    surfaceDim = Base0,
    surfaceContainer = Base2,
    surfaceContainerHigh = Base3,
    surfaceContainerHighest = Base4,
    surfaceContainerLow = Base1,
    surfaceContainerLowest = Base0,
)

private val SelectedPrimary = Color(0xFF6E4EF0)

private val MusicLightColors = lightColorScheme(
    primary = SelectedPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DDFF),
    onPrimaryContainer = Color(0xFF1B1240),
    secondary = Color(0xFF5B45C6),
    onSecondary = Color.White,
    tertiary = Color(0xFF5B45C6),
    background = Color(0xFFF6F5FA),
    onBackground = Color(0xFF16161C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16161C),
    surfaceVariant = Color(0xFFE6E4EE),
    onSurfaceVariant = Color(0xFF454055),
    inverseSurface = Color(0xFF16161C),
    inverseOnSurface = Color(0xFFF6F5FA),
    error = Color(0xFFB3261E),
    outline = Color(0xFF7A7588),
    outlineVariant = Color(0xFFD4D0DE),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE7E5EE),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE6E4EE),
    surfaceContainerHighest = Color(0xFFDEDCE8),
    surfaceContainerLow = Color(0xFFF3F2F8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> MusicDarkColors
        else -> MusicLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        typography = AppTypography,
    ) {
        Surface(
            color = colorScheme.background,
            contentColor = colorScheme.onBackground,
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
    }
}
