package com.shibi.musicplayer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.CarouselMath
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import kotlin.math.abs
import kotlin.math.min
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

/** Tile ring only. The body grows with the tile; this is the phone-sized floor. */
internal val NavCarouselBodyHeight = 156.dp

/** Clearance between the front label and the navigation-bar inset. */
internal val NavCarouselBottomPadding = 8.dp

/** Space under a song list so the last row clears the carousel. */
internal val SongListBottomPadding = 36.dp

/** Space under playlist cards so the carousel does not cover the last square. */
internal val PlaylistListBottomPadding = 36.dp

/** Gap between the two-pane player card and the screen edge, past the nav inset. */
internal val TwoPanePlayerBottomPadding = 48.dp

/** Air around the carousel when it sits in the two-pane list column. */
internal val TwoPaneCarouselPadding = 16.dp

/** Space between the mini player and the top of the carousel. */
internal val MiniPlayerCarouselGap = 12.dp

private val TileCorner = 18.dp
private val LabelGap = 8.dp
private val LabelHeight = 20.dp
private val TopBreathing = 12.dp
private val NeighborOverlap = 8.dp
private val BackLift = 10.dp
private val SelectedFill = Color(0xFF6E4EF0)
private val TileShape = RoundedCornerShape(TileCorner)
private val CarouselSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)
private val FlingDecay = exponentialDecay<Float>(frictionMultiplier = 1.6f)

private data class CarouselLayout(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val liftPx: Float,
    val tileRadius: Float,
    val labelExtent: Float,
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
    val chrome = LocalBackgroundChrome.current

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
                .semantics {
                    role = Role.Tab
                    contentDescription = label
                },
        ) {
            val density = LocalDensity.current
            val densityScale = density.density
            val tileSize = with(density) {
                val widthPx = maxWidth.toPx()
                val radius = widthPx * CarouselMath.RADIUS_FRACTION
                val neighbor = radius * sin(math.stepRadians)
                val scale45 = math.scale(math.stepRadians)
                val overlap = NeighborOverlap.toPx()
                ((2f * (neighbor + overlap)) / (1f + scale45))
                    .coerceIn(72.dp.toPx(), 120.dp.toPx())
                    .toDp()
            }
            val layout = with(density) {
                val tileRadius = tileSize.toPx() / 2f
                val labelExtent = (LabelGap + LabelHeight).toPx()
                val liftPx = BackLift.toPx()
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = (tileSize + TopBreathing + LabelGap + LabelHeight).toPx()
                CarouselLayout(
                    centerX = widthPx / 2f,
                    centerY = heightPx - labelExtent - tileRadius,
                    radius = widthPx * CarouselMath.RADIUS_FRACTION,
                    liftPx = liftPx,
                    tileRadius = tileRadius,
                    labelExtent = labelExtent,
                )
            }
            val layoutState = rememberUpdatedState(layout)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tileSize + TopBreathing + LabelGap + LabelHeight),
            ) {
            Canvas(modifier = Modifier.fillMaxSize().zIndex(0.4f)) {
                // Floor sits on the front tile, above the label.
                val floorY = layout.centerY + layout.tileRadius * 0.72f
                val rx = min(layout.radius * sin(math.stepRadians) * 0.55f, size.width * 0.28f)
                val ry = 12.dp.toPx()
                drawOval(
                    brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.28f),
                        0.42f to SelectedFill.copy(alpha = 0.24f),
                        1f to Color.Transparent,
                        center = Offset(layout.centerX, floorY),
                        radius = rx,
                    ),
                    topLeft = Offset(layout.centerX - rx, floorY - ry),
                    size = Size(rx * 2f, ry * 2f),
                )
            }

            NavSection.entries.forEachIndexed { index, section ->
                val offset = math.offsetRadians(index, rotationValue)
                val scale = math.scale(offset)
                val itemAlpha = math.alpha(offset)
                if (itemAlpha <= 0.01f) return@forEachIndexed
                val depth = math.zIndex(offset)
                val shade = math.shade(offset)
                val centered = index == selectedIndex
                val itemLabel = stringResource(section.labelRes)
                val shiftX = math.translationX(offset, layout.radius)
                val shiftY = math.translationY(offset, layout.liftPx)
                Box(
                    modifier = Modifier
                        .zIndex(depth + 1f)
                        .offset {
                            IntOffset(
                                (layout.centerX - layout.tileRadius).roundToInt(),
                                (layout.centerY - layout.tileRadius).roundToInt(),
                            )
                        }
                        .size(tileSize)
                        .graphicsLayer {
                            translationX = shiftX
                            translationY = shiftY
                            scaleX = scale
                            scaleY = scale
                            alpha = itemAlpha
                            rotationY = math.rotationYDegrees(offset)
                            cameraDistance = 12f * densityScale
                            transformOrigin = TransformOrigin.Center
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(
                                elevation = if (centered) 16.dp else 6.dp,
                                shape = TileShape,
                                ambientColor = Color.Black.copy(alpha = 0.55f),
                                spotColor = if (centered) {
                                    SelectedFill.copy(alpha = 0.45f)
                                } else {
                                    Color.Black.copy(alpha = 0.40f)
                                },
                            )
                            .clip(TileShape)
                            .background(if (centered) SelectedFill else chrome.carouselTile)
                            .border(
                                width = 1.dp,
                                color = if (centered) {
                                    Color.White.copy(alpha = 0.55f)
                                } else {
                                    chrome.carouselTileBorder
                                },
                                shape = TileShape,
                            ),
                    ) {
                        if (!centered && shade > 0.02f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = shade)),
                            )
                        }
                    }
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = if (centered) Color.White else chrome.carouselIcon,
                        modifier = Modifier.size(tileSize * 0.38f),
                    )
                }
            }

            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(7f)
                    .width(160.dp)
                    .height(LabelHeight),
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
        }
        Box(modifier = Modifier.height(NavCarouselBottomPadding))
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
        val scale = math.scale(offset)
        if (math.alpha(offset) < 0.12f) continue
        val pointX = layout.centerX + math.translationX(offset, layout.radius)
        val pointY = layout.centerY + math.translationY(offset, layout.liftPx)
        val dx = abs(x - pointX)
        val dy = y - pointY
        val halfW = layout.tileRadius * scale * 1.35f
        val top = layout.tileRadius * scale
        val bottom = (layout.tileRadius + layout.labelExtent) * scale
        if (dx > halfW || dy < -top || dy > bottom) continue
        val distance = dx + abs(dy)
        val depth = math.zIndex(offset)
        if (depth > bestDepth || (depth == bestDepth && distance < bestDistance)) {
            bestDepth = depth
            bestDistance = distance
            best = index
        }
    }
    return best.takeIf { it >= 0 }
}
