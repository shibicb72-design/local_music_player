package com.shibi.musicplayer.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.AnimatedScene
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.BackgroundCatalog
import com.shibi.musicplayer.domain.PastelColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackgroundFineTuneSheet(
    scene: AnimatedScene,
    colors: List<Long>,
    onColorAt: (Int, Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetColor = LocalBackgroundChrome.current.sheet
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetColor,
        contentColor = contentColorForContainer(sheetColor),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        BackgroundFineTuneContent(
            scene = scene,
            colors = colors,
            onColorAt = onColorAt,
            onReset = onReset,
        )
    }
}

@Composable
internal fun BackgroundFineTuneContent(
    scene: AnimatedScene,
    colors: List<Long>,
    onColorAt: (Int, Long) -> Unit,
    onReset: () -> Unit,
    forceStatic: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var slot by remember { mutableIntStateOf(0) }
    val selected = colors.getOrElse(slot) { scene.defaults.first() }
    val hsl = remember(selected) { PastelColor.fromArgb(selected) }
    var lightness by remember(selected) { mutableFloatStateOf(hsl.lightness) }
    val play = rememberBackgroundMotion(forceStatic)
    val phase = rememberScenePhase(play)
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.bg_fine_tune_title, stringResource(backgroundTitleRes(scene.id))),
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface,
        )
        Text(
            text = stringResource(R.string.bg_fine_tune_body),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            AppBackgroundLayer(
                background = AppBackground.Animated(scene.id),
                colors = colors,
                phase = { phase.value },
                modifier = Modifier.matchParentSize(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.bg_slots),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            scene.slotKeys.forEachIndexed { index, key ->
                val color = colors.getOrElse(index) { scene.defaults[index] }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { slot = index },
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color.toComposeColor())
                            .border(
                                width = if (index == slot) 3.dp else 1.dp,
                                color = if (index == slot) scheme.onSurface else scheme.outline,
                                shape = CircleShape,
                            ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(slotLabelRes(key)),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurface,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.bg_pastel_swatches),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 40.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false,
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items(BackgroundCatalog.solids, key = { it.id }, contentType = { "swatch" }) { swatch ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(swatch.argb.toComposeColor())
                        .border(1.dp, scheme.outline.copy(alpha = 0.4f), CircleShape)
                        .clickable { onColorAt(slot, swatch.argb) },
                )
            }
        }
        Text(
            text = stringResource(R.string.bg_hue),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        HueBar(
            lightness = lightness,
            onHue = { hue ->
                onColorAt(slot, PastelColor.pastelArgb(hue, lightness))
            },
        )
        Text(
            text = stringResource(R.string.bg_lightness),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Slider(
            value = lightness,
            onValueChange = { value ->
                lightness = value
                onColorAt(slot, PastelColor.pastelArgb(hsl.hue, value))
            },
            valueRange = PastelColor.MIN_LIGHTNESS..PastelColor.MAX_LIGHTNESS,
            colors = SliderDefaults.colors(
                thumbColor = scheme.onSurface,
                activeTrackColor = scheme.onSurface,
                inactiveTrackColor = scheme.outline,
            ),
        )
        TextButton(
            onClick = onReset,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.bg_reset))
        }
    }
}

@Composable
private fun HueBar(
    lightness: Float,
    onHue: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spectrum = remember(lightness) {
        List(9) { index ->
            PastelColor.pastelArgb(index * 40f, lightness).toComposeColor()
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(spectrum))
            .pointerInput(lightness) {
                detectTapGestures { offset ->
                    val hue = (offset.x / size.width.toFloat()).coerceIn(0f, 1f) * 360f
                    onHue(hue)
                }
            }
            .pointerInput(lightness) {
                detectDragGestures { change, _ ->
                    val hue = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f) * 360f
                    onHue(hue)
                }
            },
    )
}
