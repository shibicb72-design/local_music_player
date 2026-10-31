package com.shibi.musicplayer.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.DotTickState
import com.shibi.musicplayer.domain.FAST_SCROLL_HIDE_DELAY_MS
import com.shibi.musicplayer.domain.MIN_VIDEOS_PER_WEEK_DOT
import com.shibi.musicplayer.domain.MagneticSnap
import com.shibi.musicplayer.domain.VideoTimeline
import com.shibi.musicplayer.domain.VideoTimelineLayout
import com.shibi.musicplayer.domain.magneticSnap
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.background.contentColorForContainer
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Test tag of the thumb's touch area. */
internal const val VideoFastScrollerTag = "video_fast_scroller"

private val TouchWidth = 48.dp
private val TouchHeight = 56.dp
private val PillWidth = 6.dp
private val PillHeight = 44.dp
private val TickGap = 4.dp
/** Right edge of the bubble: left of the thumb and of the year-tick column. */
private val BubbleGap = 104.dp
private val ChipHalfHeight = 10.dp
private val BubbleHalfHeight = 17.dp
private val DotRadius = 2.5.dp
private val DotRingWidth = 1.5.dp
/** How far along the track a week dot reaches for the thumb. */
private val DotSnapRadius = 16.dp
/** Share of the gap to a dot the thumb closes right at the dot; the pull fades out at the radius. */
private const val DOT_SNAP_STRENGTH = 0.6f

/**
 * Week fast-scroll for the Videos list. Appears while the list scrolls or the thumb is dragged
 * and fades [FAST_SCROLL_HIDE_DELAY_MS] after the last activity. The list follows the thumb; each
 * week with at least [MIN_VIDEOS_PER_WEEK_DOT] videos gets a dot that pulls the thumb in, holds the
 * list at the start of that week and ticks once. A bubble names the week and year ticks mark the
 * track. [zone] must be the one [layout] was built with. [initialDragFraction] (tests only) starts
 * mid-drag so screenshots are deterministic.
 */
@Composable
internal fun VideoFastScroller(
    listState: LazyListState,
    layout: VideoTimelineLayout,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    initialDragFraction: Float? = null,
) {
    val chrome = LocalBackgroundChrome.current
    val container = chrome.sheet
    val onContainer = contentColorForContainer(container)
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val locale = Locale.getDefault()
    val weeks = remember(layout, zone, locale) { VideoTimeline.weeks(layout, zone, locale) }
    val stops = remember(weeks) { VideoTimeline.weekStops(weeks) }
    val dots = remember(stops, layout) { VideoTimeline.dotFractions(stops, layout.entries.size) }
    // The pointer block outlives recompositions, so it reads these through updated state.
    val currentLayout by rememberUpdatedState(layout)
    val currentStops by rememberUpdatedState(stops)
    val currentDots by rememberUpdatedState(dots)

    var dragging by remember { mutableStateOf(initialDragFraction != null) }
    // The thumb while dragging: where it is drawn, the dot holding it and how near the next one is.
    var thumb by remember { mutableStateOf(MagneticSnap(initialDragFraction ?: 0f)) }
    // Changes only when the thumb crosses into another week, so the bubble does not recompose per frame.
    val bubbleWeek by remember(weeks, stops, layout) {
        derivedStateOf {
            VideoTimeline.weekForEntry(weeks, VideoTimeline.dragTargetIndex(thumb, stops, layout.entries.size))
        }
    }
    val active = listState.isScrollInProgress || dragging
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
        animationSpec = if (rememberReduceMotion()) snap() else tween(durationMillis = 200),
        label = "videoFastScrollAlpha",
    )
    val present by remember { derivedStateOf { shown || alpha.value > 0f } }
    val canScroll = listState.canScrollForward || listState.canScrollBackward
    if (!present || !(canScroll || initialDragFraction != null)) return

    val density = LocalDensity.current
    val touchHeightPx = with(density) { TouchHeight.toPx() }
    val description = stringResource(R.string.video_fast_scroll)
    val unknown = stringResource(R.string.video_date_unknown)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer { this.alpha = alpha.value },
    ) {
        val travelPx = (constraints.maxHeight - touchHeightPx).coerceAtLeast(1f)

        fun listFraction(): Float = VideoTimeline.thumbFraction(
            listState.firstVisibleItemIndex,
            listState.layoutInfo.visibleItemsInfo.size,
            currentLayout.entries.size,
        )

        if (dragging) {
            val weekStart = bubbleWeek?.let { VideoTimeline.weekLabel(it, locale) }
            val label = if (weekStart != null) stringResource(R.string.video_week_of, weekStart) else unknown
            for ((year, fraction) in VideoTimeline.yearTicks(layout)) {
                Chip(
                    text = year.toString(),
                    container = container,
                    content = onContainer,
                    bold = false,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .wrapContentWidth(Alignment.End, unbounded = true)
                        .offset {
                            IntOffset(
                                x = -with(density) { (TouchWidth + TickGap).roundToPx() },
                                y = (fraction * travelPx + touchHeightPx / 2f -
                                    with(density) { ChipHalfHeight.toPx() }).roundToInt(),
                            )
                        },
                )
            }
            // Drawn after the ticks, so the week bubble is never covered by a year label.
            Chip(
                text = label,
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
                            y = (thumb.fraction * travelPx + touchHeightPx / 2f -
                                with(density) { BubbleHalfHeight.toPx() }).roundToInt(),
                        )
                    },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val f = if (dragging) thumb.fraction else listFraction()
                    IntOffset(0, (f * travelPx).roundToInt())
                }
                .size(width = TouchWidth, height = TouchHeight)
                .testTag(VideoFastScrollerTag)
                .semantics { contentDescription = description }
                .systemGestureExclusion()
                .pointerInput(travelPx) {
                    val radius = DotSnapRadius.toPx() / travelPx
                    var lastTarget = -1
                    var dragY = 0f
                    var ticks = DotTickState()
                    detectVerticalDragGestures(
                        onDragStart = {
                            val start = listFraction()
                            thumb = MagneticSnap(start)
                            dragY = start * travelPx
                            lastTarget = -1
                            ticks = DotTickState()
                            dragging = true
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, amount ->
                        change.consume()
                        dragY = (dragY + amount).coerceIn(0f, travelPx)
                        val snapped = magneticSnap(dragY / travelPx, currentDots, radius, DOT_SNAP_STRENGTH)
                        thumb = snapped
                        ticks = ticks.next(snapped.lockedIndex)
                        if (ticks.tick) view.performDotTick()
                        val target = VideoTimeline.dragTargetIndex(snapped, currentStops, currentLayout.entries.size)
                        if (target != lastTarget) {
                            lastTarget = target
                            scope.launch { listState.scrollToItem(target) }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = PillWidth, height = PillHeight)
                    .background(container, RoundedCornerShape(50))
                    .border(1.dp, onContainer.copy(alpha = 0.55f), RoundedCornerShape(50)),
            )
        }

        if (dragging && dots.isNotEmpty()) {
            // Over the pill, so the dot holding the thumb shows on it. Draws only; touches pass through.
            WeekDots(
                fractions = dots,
                thumb = { thumb },
                travelPx = travelPx,
                container = container,
                content = onContainer,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(TouchWidth)
                    .fillMaxHeight(),
            )
        }
    }
}

/**
 * The busy-week dots along the thumb's track, drawn plain: an on-container dot in a container ring,
 * so it shows on any backdrop, at full strength for the dot holding the thumb. [thumb] is read only
 * while drawing, so a drag redraws without recomposing; its lockedIndex, nearestIndex and proximity
 * (0..1) are the inputs for the glow and motion pass.
 */
@Composable
private fun WeekDots(
    fractions: List<Float>,
    thumb: () -> MagneticSnap,
    travelPx: Float,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val locked = thumb().lockedIndex
        val x = size.width / 2f
        val top = TouchHeight.toPx() / 2f
        val dot = DotRadius.toPx()
        val ring = dot + DotRingWidth.toPx()
        fractions.forEachIndexed { index, fraction ->
            val center = Offset(x, top + fraction * travelPx)
            drawCircle(container, radius = ring, center = center)
            drawCircle(if (index == locked) content else content.copy(alpha = 0.55f), radius = dot, center = center)
        }
    }
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
