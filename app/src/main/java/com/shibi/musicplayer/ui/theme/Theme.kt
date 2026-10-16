package com.shibi.musicplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Amber = Color(0xFFFFB74D)
private val AmberDark = Color(0xFF4A2800)
private val Ink = Color(0xFF121212)
private val InkSurface = Color(0xFF1E1E1E)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = AmberDark,
    secondary = Color(0xFFD7CCC8),
    background = Ink,
    surface = InkSurface,
    surfaceVariant = Color(0xFF2C241B),
    onSurfaceVariant = Color(0xFFD7CCC8),
)

@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        DarkColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
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
