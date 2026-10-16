package com.shibi.musicplayer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.NavWheelMath
import com.shibi.musicplayer.ui.theme.Accent
import com.shibi.musicplayer.ui.theme.Base0
import com.shibi.musicplayer.ui.theme.Secondary
import com.shibi.musicplayer.ui.theme.TextHigh
import com.shibi.musicplayer.ui.theme.TextMed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

enum class WheelDestination(
    val labelRes: Int,
    val icon: ImageVector,
) {
    Songs(R.string.nav_songs, Icons.Rounded.MusicNote),
    Playlists(R.string.nav_playlists, Icons.AutoMirrored.Rounded.QueueMusic),
    NowPlaying(R.string.nav_now_playing, Icons.Rounded.PlayCircle),
    Devices(R.string.nav_devices, Icons.Rounded.Speaker),
}

internal val NavWheelHeight = 196.dp

private val IconSize = 52.dp
private val IconCenterFromTop = 40.dp
private val PreferredRadius = 92.dp
private val RingStroke = 2.5.dp
private val WheelSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)

private data class WheelLayout(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val iconRadius: Float,
    val arcPixels: Float,
)

@Composable
fun NavWheel(
    selected: WheelDestination,
    onSelect: (WheelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val math = remember { NavWheelMath(WheelDestination.entries.size) }
    val rotation = remember { Animatable(selected.ordinal.toFloat()) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val onSelectState = rememberUpdatedState(onSelect)
    var gestureActive by remember { mutableStateOf(false) }
    val rotationValue = rotation.value
    val selectedIndex = math.selectedIndex(rotationValue)
    val label = stringResource(WheelDestination.entries[selectedIndex].labelRes)

    LaunchedEffect(math) {
        var last = rotation.value.roundToInt()
        snapshotFlow { rotation.value.roundToInt() }.collect { rounded ->
            if (rounded != last) {
                last = rounded
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }
    }

    LaunchedEffect(selected) {
        if (gestureActive) return@LaunchedEffect
        val target = math.rotationForIndex(rotation.value, selected.ordinal)
        if (abs(rotation.value - target) > 0.01f) {
            rotation.animateTo(target, WheelSpring)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(NavWheelHeight)
            .clipToBounds()
            .semantics {
                role = Role.Tab
                contentDescription = label
            },
    ) {
        val density = LocalDensity.current
        val layout = with(density) {
            val widthPx = constraints.maxWidth.toFloat()
            val iconRadius = IconSize.toPx() / 2f
            val sideMargin = 16.dp.toPx()
            val maxRadius = (widthPx / 2f - sideMargin - iconRadius).coerceAtLeast(iconRadius)
            val radius = PreferredRadius.toPx().coerceAtMost(maxRadius)
            val centerY = IconCenterFromTop.toPx() + radius
            WheelLayout(
                centerX = widthPx / 2f,
                centerY = centerY,
                radius = radius,
                iconRadius = iconRadius,
                arcPixels = radius * (math.stepDegrees * PI.toFloat() / 180f),
            )
        }
        val layoutState = rememberUpdatedState(layout)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(layout.centerX, layout.centerY)
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = layout.radius,
                center = center,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.42f),
                radius = layout.radius,
                center = center,
                style = Stroke(width = RingStroke.toPx(), cap = StrokeCap.Round),
            )
        }

        WheelDestination.entries.forEachIndexed { index, destination ->
            val offsetDegrees = math.offsetDegrees(index, rotationValue)
            val point = math.position(offsetDegrees, layout.radius)
            val scale = math.scale(offsetDegrees)
            val itemAlpha = math.alpha(offsetDegrees)
            val centered = index == selectedIndex
            val itemLabel = stringResource(destination.labelRes)
            val x = layout.centerX + point.x
            val y = layout.centerY + point.y
            val half = layout.iconRadius
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (x - half).roundToInt(),
                            (y - half).roundToInt(),
                        )
                    }
                    .size(IconSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = itemAlpha
                    }
                    .clip(CircleShape)
                    .semantics {
                        role = Role.Tab
                        this.selected = centered
                        contentDescription = itemLabel
                        onClick {
                            scope.launch {
                                gestureActive = true
                                rotation.stop()
                                val target = math.rotationForIndex(rotation.value, index)
                                rotation.animateTo(target, WheelSpring)
                                gestureActive = false
                                onSelectState.value(destination)
                            }
                            true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = Base0)
                    if (centered) drawCircle(color = Accent.copy(alpha = 0.22f))
                }
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = if (centered) Secondary else TextMed,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = IconCenterFromTop + IconSize / 2 + 6.dp)
                .padding(horizontal = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(width = 22.dp, height = 3.dp)
                    .clip(CircleShape),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(Secondary)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(math) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        scope.launch { rotation.stop() }
                        gestureActive = true
                        var spun = rotation.value
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var totalX = 0f
                        var totalY = 0f
                        var dragging = false
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                change.consume()
                                val currentLayout = layoutState.value
                                if (!dragging) {
                                    val hit = hitTest(
                                        position = down.position,
                                        layout = currentLayout,
                                        rotation = rotation.value,
                                        math = math,
                                    )
                                    if (hit != null) {
                                        val target = math.rotationForIndex(rotation.value, hit)
                                        scope.launch {
                                            rotation.animateTo(target, WheelSpring)
                                            gestureActive = false
                                            onSelectState.value(WheelDestination.entries[hit])
                                        }
                                    } else {
                                        gestureActive = false
                                    }
                                } else {
                                    val velocity = tracker.calculateVelocity().x
                                    val fling = math.flingItems(velocity, currentLayout.arcPixels)
                                    val target = math.snapTarget(spun, fling)
                                    scope.launch {
                                        rotation.animateTo(target, WheelSpring)
                                        gestureActive = false
                                        onSelectState.value(
                                            WheelDestination.entries[math.selectedIndex(rotation.value)],
                                        )
                                    }
                                }
                                break
                            }
                            val delta = change.positionChange()
                            totalX += delta.x
                            totalY += delta.y
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!dragging && (abs(totalX) > slop || abs(totalY) > slop)) {
                                dragging = abs(totalX) >= abs(totalY)
                                if (!dragging) {
                                    gestureActive = false
                                    break
                                }
                            }
                            if (dragging) {
                                change.consume()
                                val arc = layoutState.value.arcPixels.coerceAtLeast(1f)
                                spun += -delta.x / arc
                                val target = spun
                                scope.launch { rotation.snapTo(target) }
                            }
                        }
                    }
                },
        )
    }
}

private fun hitTest(
    position: Offset,
    layout: WheelLayout,
    rotation: Float,
    math: NavWheelMath,
): Int? {
    var best = -1
    var bestDistance = Float.MAX_VALUE
    val reach = layout.iconRadius * 1.45f
    for (index in 0 until math.itemCount) {
        val point = math.position(math.offsetDegrees(index, rotation), layout.radius)
        val distance = hypot(
            position.x - (layout.centerX + point.x),
            position.y - (layout.centerY + point.y),
        )
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best.takeIf { bestDistance <= reach }
}
