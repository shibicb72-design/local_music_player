package com.shibi.musicplayer.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.BackgroundCatalog
import com.shibi.musicplayer.ui.theme.Base0

@Composable
fun AppBackgroundHost(
    background: AppBackground,
    customColors: Map<String, List<Long>>,
    forceStatic: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val play = rememberBackgroundMotion(forceStatic)
    val phase = rememberScenePhase(play)
    val colors = remember(background, customColors) {
        BackgroundCatalog.colorsFor(background, customColors)
    }
    Box(modifier = modifier.fillMaxSize()) {
        AppBackgroundLayer(
            background = background,
            colors = colors,
            phase = { phase.value },
            modifier = Modifier.fillMaxSize(),
        )
        BackgroundContentTheme(
            background = background,
            customColors = customColors,
        ) {
            content()
        }
    }
}

@Composable
internal fun AppBackgroundLayer(
    background: AppBackground,
    colors: List<Long>,
    phase: () -> Float,
    modifier: Modifier = Modifier,
) {
    val scratch = remember { SceneScratch() }
    val field = remember(background) {
        val id = (background as? AppBackground.Animated)?.sceneId ?: "default"
        DriftField(12, id.hashCode())
    }
    val blendColors = remember(colors) {
        val mapped = colors.map { it.toComposeColor() }
        when {
            mapped.isEmpty() -> listOf(Base0, Base0)
            mapped.size == 1 -> listOf(mapped[0], mapped[0])
            else -> mapped
        }
    }
    Canvas(modifier = modifier) {
        when (background) {
            AppBackground.Default -> drawRect(Base0)
            is AppBackground.Solid -> drawRect(colors.colorOr(Base0))
            is AppBackground.Gradient -> drawRect(brush = Brush.linearGradient(blendColors))
            is AppBackground.Animated -> {
                drawScene(background.sceneId, colors, phase(), scratch, field)
            }
        }
    }
}

private fun List<Long>.colorOr(fallback: androidx.compose.ui.graphics.Color) =
    firstOrNull()?.toComposeColor() ?: fallback
