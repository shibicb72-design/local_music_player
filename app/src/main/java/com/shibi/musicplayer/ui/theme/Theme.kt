package com.shibi.musicplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

@Composable
fun MusicPlayerTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        MusicDarkColors
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
