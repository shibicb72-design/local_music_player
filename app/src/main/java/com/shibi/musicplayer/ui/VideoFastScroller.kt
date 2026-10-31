package com.shibi.musicplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.FAST_SCROLL_HIDE_DELAY_MS
import com.shibi.musicplayer.domain.VideoTimeline
import com.shibi.musicplayer.domain.VideoTimelineLayout
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.background.contentColorForContainer
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

/**
 * Month fast-scroll for the Videos list. Appears while the list scrolls or the thumb is
 * dragged and fades [FAST_SCROLL_HIDE_DELAY_MS] after the last activity. Dragging snaps to
 * the start of a month; a bubble names the month and year ticks mark the track.
 * [initialDragFraction] (tests only) starts mid-drag so screenshots are deterministic.
 */
@Composable
internal fun VideoFastScroller(
    listState: LazyListState,
    layout: VideoTimelineLayout,
    modifier: Modifier = Modifier,
    initialDragFraction: Float? = null,
) {
    val chrome = LocalBackgroundChrome.current
    val container = chrome.sheet
    val onContainer = contentColorForContainer(container)
    val scope = rememberCoroutineScope()
    val currentLayout by rememberUpdatedState(layout)

    var dragging by remember { mutableStateOf(initialDragFraction != null) }
    var dragFraction by remember { mutableFloatStateOf(initialDragFraction ?: 0f) }
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
            layout.entries.size,
        )

        if (dragging) {
            val entry = VideoTimeline.entryIndexForFraction(dragFraction, layout.entries.size)
            val section = layout.sections.getOrNull(VideoTimeline.sectionIndexForEntry(layout, entry))
            val label = section?.let { VideoTimeline.bubbleLabel(it, Locale.getDefault()) } ?: unknown
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
            // Drawn after the ticks, so the month bubble is never covered by a year label.
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
                            y = (dragFraction * travelPx + touchHeightPx / 2f -
                                with(density) { BubbleHalfHeight.toPx() }).roundToInt(),
                        )
                    },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val f = if (dragging) dragFraction else listFraction()
                    IntOffset(0, (f * travelPx).roundToInt())
                }
                .size(width = TouchWidth, height = TouchHeight)
                .testTag(VideoFastScrollerTag)
                .semantics { contentDescription = description }
                .systemGestureExclusion()
                .pointerInput(travelPx) {
                    var lastTarget = -1
                    var dragY = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragFraction = listFraction()
                            dragY = dragFraction * travelPx
                            lastTarget = -1
                            dragging = true
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, amount ->
                        change.consume()
                        dragY = (dragY + amount).coerceIn(0f, travelPx)
                        dragFraction = dragY / travelPx
                        val target = VideoTimeline.snapIndexForFraction(currentLayout, dragFraction)
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
    }
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
