package com.shibi.musicplayer.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.domain.DotTickState
import com.shibi.musicplayer.domain.FAST_SCROLL_HIDE_DELAY_MS
import com.shibi.musicplayer.domain.MagneticSnap
import com.shibi.musicplayer.domain.ScrollSection
import com.shibi.musicplayer.domain.dragTargetIndex
import com.shibi.musicplayer.domain.magneticSnap
import com.shibi.musicplayer.domain.sectionIndexAt
import com.shibi.musicplayer.domain.stopFractions
import com.shibi.musicplayer.domain.thinStops
import com.shibi.musicplayer.domain.thumbFraction
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.background.contentColorForContainer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The thumb's touch area: the handle the list rows must leave room for. */
internal val FastScrollTouchWidth = 48.dp
private val TouchHeight = 56.dp
private val HaloSize = 36.dp
private val TickGap = 4.dp
/** Right edge of the bubble: left of the thumb and of the year-tick column. */
private val BubbleGap = 104.dp
private val ChipHalfHeight = 10.dp
private val BubbleHalfHeight = 17.dp
private val DotRadius = 2.5.dp
private val DotRingWidth = 1.5.dp
/** How far along the track a dot reaches for the thumb; dots closer than this are thinned. */
private val DotSnapRadius = 16.dp
/** Share of the gap to a dot the thumb closes right at the dot; the pull fades out at the radius. */
private const val DOT_SNAP_STRENGTH = 0.6f

/** A label on the track, such as a year, at [fraction] of it. */
@Immutable
internal data class FastScrollTick(val label: String, val fraction: Float)

/**
 * Fast scroll for a library list of [entryCount] entries. Appears while the list scrolls or the
 * thumb is dragged and fades [FAST_SCROLL_HIDE_DELAY_MS] after the last activity. The list
 * follows the thumb; each of [stops] (a busy week, a letter, a size band) gets a dot that pulls
 * the thumb in, holds the list at the stop's start and ticks once. With [thin] (letters, ranges),
 * dots closer than the snap radius are thinned, the biggest stops kept; busy weeks pass false and
 * keep every dot, as Videos always has. The bubble names the one of [sections] under the
 * thumb ([label] gets its index, null when there is none) and [ticks] mark the track.
 * [description] names what it scrolls by. A new [resetKey] (the list's sort) starts it over:
 * no drag, no latched dot, no pending tick. [initialDragFraction] (tests only) starts mid-drag
 * so screenshots are deterministic.
 */
@Composable
internal fun FastScroller(
    listState: LazyListState,
    entryCount: Int,
    sections: List<ScrollSection>,
    stops: List<ScrollSection>,
    label: (sectionIndex: Int?) -> String,
    description: String,
    modifier: Modifier = Modifier,
    ticks: List<FastScrollTick> = emptyList(),
    resetKey: Any? = null,
    tag: String = VideoFastScrollerTag,
    initialDragFraction: Float? = null,
    thin: Boolean = true,
) {
    key(resetKey) {
        FastScrollerBody(
            listState = listState,
            entryCount = entryCount,
            sections = sections,
            stops = stops,
            label = label,
            description = description,
            modifier = modifier,
            ticks = ticks,
            tag = tag,
            initialDragFraction = initialDragFraction,
            thin = thin,
        )
    }
}

@Composable
private fun FastScrollerBody(
    listState: LazyListState,
    entryCount: Int,
    sections: List<ScrollSection>,
    stops: List<ScrollSection>,
    label: (Int?) -> String,
    description: String,
    modifier: Modifier,
    ticks: List<FastScrollTick>,
    tag: String,
    initialDragFraction: Float?,
    thin: Boolean,
) {
    val chrome = LocalBackgroundChrome.current
    val container = chrome.sheet
    val onContainer = contentColorForContainer(container)
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val currentReduceMotion by rememberUpdatedState(reduceMotion)
    val latchPulse = remember { Animatable(0f) }
    val view = LocalView.current
    // The pointer block outlives recompositions, so it reads these through updated state.
    val currentEntryCount by rememberUpdatedState(entryCount)

    var dragging by remember { mutableStateOf(initialDragFraction != null) }
    // The list/text detent is independent of the raw finger position used by the halo.
    var thumb by remember { mutableStateOf(MagneticSnap(initialDragFraction ?: 0f)) }
    var fingerFraction by remember { mutableFloatStateOf(initialDragFraction ?: 0f) }
    val active = listState.isScrollInProgress || dragging
    val dragPresence = animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 220),
        label = "fastScrollDragPresence",
    )
    val dragDecorationsPresent by remember { derivedStateOf { dragging || dragPresence.value > 0f } }
    val haloPresence = animateFloatAsState(
        targetValue = if (dragging) 1f else if (active) 0.6f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 220),
        label = "fastScrollHaloPresence",
    )
    var shown by remember { mutableStateOf(initialDragFraction != null) }
    LaunchedEffect(active) {
        if (active) {
            shown = true
        } else {
            delay(FAST_SCROLL_HIDE_DELAY_MS)
            shown = false
        }
    }
    val alpha = animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 200),
        label = "fastScrollAlpha",
    )
    val present by remember { derivedStateOf { shown || alpha.value > 0f } }
    val canScroll = listState.canScrollForward || listState.canScrollBackward
    if (!present || !(canScroll || initialDragFraction != null)) return

    val density = LocalDensity.current
    val touchHeightPx = with(density) { TouchHeight.toPx() }
    val snapRadiusPx = with(density) { DotSnapRadius.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer { this.alpha = alpha.value },
    ) {
        val travelPx = (constraints.maxHeight - touchHeightPx).coerceAtLeast(1f)
        // Thinned, no two dots sit inside one snap radius, where the thumb could not tell them
        // apart. Busy weeks are not thinned; between two close ones the nearer dot wins.
        val shownStops = remember(stops, entryCount, travelPx, snapRadiusPx, thin) {
            if (thin) thinStops(stops, entryCount, travelPx, snapRadiusPx) else stops
        }
        val dots = remember(shownStops, entryCount) { stopFractions(shownStops, entryCount) }
        val currentStops by rememberUpdatedState(shownStops)
        val currentDots by rememberUpdatedState(dots)
        // Changes only when the thumb crosses into another section, so the bubble does not recompose per frame.
        val bubbleSection by remember(sections, shownStops, entryCount) {
            derivedStateOf { sectionIndexAt(sections, dragTargetIndex(thumb, shownStops, entryCount)) }
        }

        fun listFraction(): Float = thumbFraction(
            listState.firstVisibleItemIndex,
            listState.layoutInfo.visibleItemsInfo.size,
            currentEntryCount,
        )

        // Read only from layout/draw: position follows every pointer event, never an animation or
        // the list's snapped target. The pull fades continuously to zero at the capture radius.
        fun haloFraction(): Float {
            val raw = fingerFraction
            val radius = snapRadiusPx / travelPx
            var nearest = raw
            var distance = radius
            for (index in currentDots.indices) {
                val dot = currentDots[index]
                val gap = abs(raw - dot)
                if (gap < distance) {
                    nearest = dot
                    distance = gap
                }
            }
            val pull = DOT_SNAP_STRENGTH * (1f - distance / radius)
            return raw + (nearest - raw) * pull
        }

        if (dragDecorationsPresent) {
            val bubble = label(bubbleSection)
            for (tick in ticks) {
                Chip(
                    text = tick.label,
                    container = container,
                    content = onContainer,
                    bold = false,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .wrapContentWidth(Alignment.End, unbounded = true)
                        .offset {
                            IntOffset(
                                x = -with(density) { (FastScrollTouchWidth + TickGap).roundToPx() },
                                y = (tick.fraction * travelPx + touchHeightPx / 2f -
                                    with(density) { ChipHalfHeight.toPx() }).roundToInt(),
                            )
                        }
                        .graphicsLayer { this.alpha = dragPresence.value },
                )
            }
            // Drawn after the ticks, so the bubble is never covered by a year label.
            Chip(
                text = bubble,
                container = container,
                content = onContainer,
                bold = true,
                large = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .wrapContentWidth(Alignment.End, unbounded = true)
                    .offset {
                        IntOffset(
                            x = -with(density) { BubbleGap.roundToPx() },
                            y = (haloFraction() * travelPx + touchHeightPx / 2f -
                                with(density) { BubbleHalfHeight.toPx() }).roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        val presence = dragPresence.value
                        this.alpha = presence
                        scaleX = 0.92f + 0.08f * presence
                        scaleY = 0.92f + 0.08f * presence
                    },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    // Hold the last finger position throughout release instead of jumping to the list.
                    val f = if (dragging || dragPresence.value > 0f) haloFraction() else listFraction()
                    IntOffset(0, (f * travelPx).roundToInt())
                }
                .size(width = FastScrollTouchWidth, height = TouchHeight)
                .testTag(tag)
                .semantics { contentDescription = description }
                .systemGestureExclusion()
                .pointerInput(travelPx) {
                    val radius = DotSnapRadius.toPx() / travelPx
                    var lastTarget = -1
                    var dragY = 0f
                    var ticksState = DotTickState()
                    detectVerticalDragGestures(
                        onDragStart = {
                            val start = listFraction()
                            thumb = MagneticSnap(start)
                            fingerFraction = start
                            dragY = start * travelPx
                            lastTarget = -1
                            ticksState = DotTickState()
                            dragging = true
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, amount ->
                        change.consume()
                        dragY = (dragY + amount).coerceIn(0f, travelPx)
                        fingerFraction = dragY / travelPx
                        val snapped = magneticSnap(dragY / travelPx, currentDots, radius, DOT_SNAP_STRENGTH)
                        thumb = snapped
                        ticksState = ticksState.next(snapped.lockedIndex)
                        if (ticksState.tick) {
                            view.performDotTick()
                            // Start the decoration in this event, alongside the existing haptic.
                            // Animatable cancels an earlier pulse if another detent arrives.
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                latchPulse.snapTo(if (currentReduceMotion) 0f else 1f)
                                if (!currentReduceMotion) {
                                    latchPulse.animateTo(0f, tween(durationMillis = 100))
                                }
                            }
                        }
                        val target = dragTargetIndex(snapped, currentStops, currentEntryCount)
                        if (target != lastTarget) {
                            lastTarget = target
                            scope.launch { listState.scrollToItem(target) }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            ThumbHalo(
                presence = { haloPresence.value },
                pulse = { latchPulse.value },
                content = onContainer,
            )
        }

        if (dragDecorationsPresent && dots.isNotEmpty()) {
            // Over the halo, so the dot holding the thumb shows on it. Touches pass through.
            StopDots(
                fractions = dots,
                thumb = { thumb },
                haloFraction = { haloFraction() },
                travelPx = travelPx,
                container = container,
                content = onContainer,
                pulse = { latchPulse.value },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(FastScrollTouchWidth)
                    .fillMaxHeight()
                    .graphicsLayer { this.alpha = dragPresence.value },
            )
        }
    }
}

/** The layer deforms around its centre; its position belongs entirely to the thumb's offset. */
@Composable
private fun ThumbHalo(
    presence: () -> Float,
    pulse: () -> Float,
    content: Color,
) {
    Box(
        Modifier
            .size(HaloSize)
            .graphicsLayer {
                val kick = pulse()
                alpha = presence()
                scaleX = 1f - 0.16f * kick
                scaleY = 1f + 0.28f * kick
            }
            .drawWithCache {
                val haloCenter = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f
                val rimRadius = radius * 0.48f
                val rim = Stroke(width = 1.25.dp.toPx())
                val softRim = Stroke(width = 3.dp.toPx())
                val glow = Brush.radialGradient(
                    0f to Color.Transparent,
                    0.3f to content.copy(alpha = 0.04f),
                    0.48f to content.copy(alpha = 0.4f),
                    0.7f to content.copy(alpha = 0.12f),
                    1f to Color.Transparent,
                    center = haloCenter,
                    radius = radius,
                )
                onDrawBehind {
                    val kick = pulse()
                    drawCircle(glow, radius, haloCenter, alpha = 0.55f + 0.45f * kick)
                    drawCircle(content, rimRadius, haloCenter, alpha = 0.12f + 0.16f * kick, style = softRim)
                    drawCircle(content, rimRadius, haloCenter, alpha = 0.4f + 0.4f * kick, style = rim)
                }
            },
    )
}

/** Cached lights; finger proximity and the shared haptic-frame bloom are read only in draw. */
@Composable
private fun StopDots(
    fractions: List<Float>,
    thumb: () -> MagneticSnap,
    haloFraction: () -> Float,
    travelPx: Float,
    container: Color,
    content: Color,
    pulse: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.drawWithCache {
            val x = size.width / 2f
            val top = TouchHeight.toPx() / 2f
            val dot = DotRadius.toPx()
            val ring = dot + DotRingWidth.toPx()
            val glowRadius = ring * 3f
            val reach = DotSnapRadius.toPx() * 2f
            val centers = fractions.map { fraction -> Offset(x, top + fraction * travelPx) }
            val glows = centers.map { dotCenter ->
                Brush.radialGradient(
                    0f to content.copy(alpha = 0.65f),
                    0.35f to content.copy(alpha = 0.3f),
                    1f to Color.Transparent,
                    center = dotCenter,
                    radius = glowRadius,
                )
            }
            onDrawBehind {
                val state = thumb()
                val kick = pulse()
                val haloY = top + haloFraction() * travelPx
                for (index in centers.indices) {
                    val light = (1f - abs(centers[index].y - haloY) / reach).coerceIn(0f, 1f)
                    val lockPulse = if (state.lockedIndex == index) kick else 0f
                    val dotCenter = centers[index]
                    drawCircle(
                        glows[index], glowRadius, dotCenter,
                        alpha = (0.12f + 0.68f * light + 0.2f * lockPulse).coerceAtMost(1f),
                    )
                    drawCircle(container, ring, dotCenter)
                    drawCircle(content, dot, dotCenter, alpha = 0.55f + 0.45f * light)
                }
            }
        },
    )
}

/** One light detent: SEGMENT_TICK where the platform has it (API 34), else CLOCK_TICK. */
private fun View.performDotTick() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        },
    )
}

/** A small container chip whose text colour comes from the chip's own fill. */
@Composable
private fun Chip(
    text: String,
    container: Color,
    content: Color,
    bold: Boolean,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    Text(
        text = text,
        style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = content,
        maxLines = 1,
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = if (large) 14.dp else 8.dp, vertical = if (large) 7.dp else 3.dp),
    )
}
