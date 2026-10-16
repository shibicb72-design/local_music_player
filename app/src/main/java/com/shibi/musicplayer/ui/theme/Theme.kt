package com.shibi.musicplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Amber = Color(0xFFFFB74D)
private val AmberDark = Color(0xFF4A2800)
private val Ink = Color(0xFF121212)
private val InkSurface = Color(0xFF1E1E1E)
private val Mist = Color(0xFFE8E0D6)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = AmberDark,
    secondary = Color(0xFFD7CCC8),
    background = Ink,
    surface = InkSurface,
    surfaceVariant = Color(0xFF2C241B),
    onSurfaceVariant = Color(0xFFD7CCC8),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A4B08),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF6D4C41),
    background = Mist,
    surface = Color(0xFFFFF8F2),
    surfaceVariant = Color(0xFFF3E6D8),
)

@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
