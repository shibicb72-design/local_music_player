package com.shibi.musicplayer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.CarouselMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

enum class NavSection(
    val labelRes: Int,
    val icon: ImageVector,
) {
    Songs(R.string.nav_songs, Icons.Rounded.MusicNote),
    Playlists(R.string.nav_playlists, Icons.AutoMirrored.Rounded.QueueMusic),
    NowPlaying(R.string.nav_now_playing, Icons.Rounded.PlayCircle),
    Devices(R.string.nav_devices, Icons.Rounded.Speaker),
    Favorites(R.string.nav_favorites, Icons.Rounded.Favorite),
    Recent(R.string.nav_recent, Icons.Rounded.History),
    Search(R.string.nav_search, Icons.Rounded.Search),
    Settings(R.string.nav_settings, Icons.Rounded.Settings),
}

/** Icon ring only. Bottom inset padding sits under this so the pair stays compact. */
internal val NavCarouselBodyHeight = 120.dp

/** Clearance between the ring and the navigation bar. */
internal val NavCarouselBottomPadding = 12.dp

private val IconSize = 44.dp
private val LabelBlock = 26.dp
private val LabelGap = 4.dp
private val VerticalRadius = 34.dp
private val SelectedFill = Color(0xFF6E4EF0)
private val CarouselSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)
private val FlingDecay = exponentialDecay<Float>(frictionMultiplier = 1.6f)

private data class CarouselLayout(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val verticalRadius: Float,
    val iconRadius: Float,
)

@Composable
fun NavCarousel(
    selected: NavSection,
    onSelect: (NavSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val math = remember { CarouselMath(NavSection.entries.size) }
    val rotation = remember { Animatable(selected.ordinal.toFloat()) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val onSelectState = rememberUpdatedState(onSelect)
    var gestureActive by remember { mutableStateOf(false) }
    var settleGeneration by remember { mutableIntStateOf(0) }
    val rotationValue = rotation.value
    val selectedIndex = math.selectedIndex(rotationValue)
    val label = stringResource(NavSection.entries[selectedIndex].labelRes)

    LaunchedEffect(selected) {
        if (gestureActive) return@LaunchedEffect
        val target = math.rotationForIndex(rotation.value, selected.ordinal)
        if (abs(rotation.value - target) > 0.01f) {
            rotation.animateTo(target, CarouselSpring)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(NavCarouselBodyHeight)
                .semantics {
                    role = Role.Tab
                    contentDescription = label
                },
        ) {
            val density = LocalDensity.current
            val densityScale = density.density
            val layout = with(density) {
                val iconRadius = IconSize.toPx() / 2f
                val labelBlock = LabelBlock.toPx()
                val gap = LabelGap.toPx()
                val verticalRadius = VerticalRadius.toPx()
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()
                CarouselLayout(
                    centerX = widthPx / 2f,
                    centerY = heightPx - labelBlock - gap - verticalRadius - iconRadius,
                    radius = (widthPx * 0.30f).coerceAtMost(128.dp.toPx()),
                    verticalRadius = verticalRadius,
                    iconRadius = iconRadius,
                )
            }
            val layoutState = rememberUpdatedState(layout)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                val steps = 36
                for (step in 0..steps) {
                    val theta = (-PI / 2.0 + PI * step / steps).toFloat()
                    val x = layout.centerX + sin(theta) * layout.radius * 0.94f
                    val y = layout.centerY + cos(theta) * layout.verticalRadius
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.38f),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            NavSection.entries.forEachIndexed { index, section ->
                val offset = math.offsetRadians(index, rotationValue)
                val scale = math.scale(offset)
                val itemAlpha = math.alpha(offset)
                val depth = math.zIndex(offset)
                val centered = index == selectedIndex
                val itemLabel = stringResource(section.labelRes)
                val shiftX = math.translationX(offset, layout.radius)
                val shiftY = math.translationY(offset, layout.verticalRadius)
                Box(
                    modifier = Modifier
                        .zIndex(depth)
                        .offset {
                            IntOffset(
                                (layout.centerX - layout.iconRadius).roundToInt(),
                                (layout.centerY - layout.iconRadius).roundToInt(),
                            )
                        }
                        .size(IconSize)
                        .graphicsLayer {
                            translationX = shiftX
                            translationY = shiftY
                            scaleX = scale
                            scaleY = scale
                            alpha = itemAlpha
                            rotationY = math.rotationYDegrees(offset)
                            cameraDistance = 16f * densityScale
                        }
                        .semantics {
                            role = Role.Tab
                            this.selected = centered
                            contentDescription = itemLabel
                            onClick {
                                val generation = ++settleGeneration
                                scope.launch {
                                    gestureActive = true
                                    rotation.stop()
                                    val target = math.rotationForIndex(rotation.value, index)
                                    rotation.animateTo(target, CarouselSpring)
                                    if (settleGeneration == generation) {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        gestureActive = false
                                        onSelectState.value(section)
                                    }
                                }
                                true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (centered) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.radialGradient(
                                        0f to SelectedFill.copy(alpha = 0.55f),
                                        0.55f to SelectedFill.copy(alpha = 0.22f),
                                        1f to Color.Transparent,
                                    ),
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(SelectedFill),
                        )
                    }
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = if (centered) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                        modifier = Modifier.size(if (centered) 26.dp else 24.dp),
                    )
                }
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
                    .zIndex(2f),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(8f)
                    .pointerInput(math) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val generation = ++settleGeneration
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
                                            x = down.position.x,
                                            y = down.position.y,
                                            layout = currentLayout,
                                            rotation = rotation.value,
                                            math = math,
                                        )
                                        if (hit != null) {
                                            scope.launch {
                                                val target = math.rotationForIndex(rotation.value, hit)
                                                rotation.animateTo(target, CarouselSpring)
                                                if (settleGeneration == generation) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                    gestureActive = false
                                                    onSelectState.value(NavSection.entries[hit])
                                                }
                                            }
                                        } else if (settleGeneration == generation) {
                                            gestureActive = false
                                        }
                                    } else {
                                        val velocity = tracker.calculateVelocity().x
                                        val itemsPerSecond = math.dragItems(velocity, currentLayout.radius)
                                        scope.launch {
                                            rotation.animateDecay(itemsPerSecond, FlingDecay)
                                            val target = math.snapTarget(rotation.value)
                                            if (abs(rotation.value - target) > 0.001f) {
                                                rotation.animateTo(target, CarouselSpring)
                                            }
                                            if (settleGeneration == generation) {
                                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                gestureActive = false
                                                onSelectState.value(
                                                    NavSection.entries[math.selectedIndex(rotation.value)],
                                                )
                                            }
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
                                        if (settleGeneration == generation) gestureActive = false
                                        break
                                    }
                                }
                                if (dragging) {
                                    change.consume()
                                    spun += math.dragItems(delta.x, layoutState.value.radius)
                                    val target = spun
                                    scope.launch { rotation.snapTo(target) }
                                }
                            }
                        }
                    },
            )
        }
        Box(modifier = Modifier.height(NavCarouselBottomPadding))
    }
}

@Composable
internal fun ListBottomFade(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, background),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(background),
        )
    }
}

private fun hitTest(
    x: Float,
    y: Float,
    layout: CarouselLayout,
    rotation: Float,
    math: CarouselMath,
): Int? {
    var best = -1
    var bestDepth = Float.NEGATIVE_INFINITY
    var bestDistance = Float.MAX_VALUE
    for (index in 0 until math.itemCount) {
        val offset = math.offsetRadians(index, rotation)
        if (math.alpha(offset) < 0.12f) continue
        val pointX = layout.centerX + math.translationX(offset, layout.radius)
        val pointY = layout.centerY + math.translationY(offset, layout.verticalRadius)
        val distance = hypot(x - pointX, y - pointY)
        val reach = layout.iconRadius * math.scale(offset) * 1.25f
        if (distance > reach) continue
        val depth = math.zIndex(offset)
        if (depth > bestDepth || (depth == bestDepth && distance < bestDistance)) {
            bestDepth = depth
            bestDistance = distance
            best = index
        }
    }
    return best.takeIf { it >= 0 }
}
