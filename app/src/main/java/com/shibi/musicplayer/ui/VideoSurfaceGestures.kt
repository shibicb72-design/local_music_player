package com.shibi.musicplayer.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import com.shibi.musicplayer.domain.ChainWindowMs
import com.shibi.musicplayer.domain.DoubleTapSkipState
import com.shibi.musicplayer.domain.FullscreenGestures
import com.shibi.musicplayer.domain.TapAction
import com.shibi.musicplayer.domain.TapSide
import com.shibi.musicplayer.domain.onTap
import com.shibi.musicplayer.domain.onTimeout
import com.shibi.musicplayer.domain.sideOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A vertical drag on the picture (fullscreen only: volume on the right, brightness on the left). */
internal interface VerticalSwipe {
    fun start(x: Float, width: Float)
    fun drag(dragUpPx: Float, height: Float)
    fun end()
}

/**
 * What the gestures need from their owner, read fresh at every step so the one long-lived
 * pointer loop never uses a stale callback or position.
 */
internal class VideoSurfaceActions(
    val positionMs: () -> Long,
    val durationMs: () -> Long,
    val onToggleControls: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onRequestPreview: (Long) -> Unit,
    /** Null = inline or pane: a vertical drag is never consumed, so the page or pane keeps it. */
    val vertical: VerticalSwipe?,
)

/** Test-only: a swipe-seek overlay already showing. */
internal data class SwipePreview(val startMs: Long, val targetMs: Long)

/** Test-only: a double-tap ripple already showing ([totalMs] is signed). */
internal data class TapPreview(val side: TapSide, val totalMs: Long)

/**
 * The gesture state. The snapshot fields are read only by the overlays (their own small
 * composables), never by the player, so a drag frame recomposes the overlay and nothing else.
 * The plain fields belong to the pointer loop, which runs on the main thread.
 */
@Stable
internal class VideoGestureState(initialSwipe: SwipePreview?, initialTap: TapPreview?) {

    var swiping by mutableStateOf(initialSwipe != null)
    var swipeStartMs by mutableLongStateOf(initialSwipe?.startMs ?: 0L)
    var swipeTargetMs by mutableLongStateOf(initialSwipe?.targetMs ?: 0L)

    var rippleSide by mutableStateOf(initialTap?.side)
    var rippleTotalMs by mutableLongStateOf(initialTap?.totalMs ?: 0L)
    var rippleSerial by mutableIntStateOf(0)

    internal var taps = DoubleTapSkipState()
    internal var chainStartMs = 0L
    internal var timer: Job? = null
    internal var coords: LayoutCoordinates? = null

    fun placed(coordinates: LayoutCoordinates) {
        coords = coordinates
    }
}

@Composable
internal fun rememberVideoGestureState(
    initialSwipe: SwipePreview? = null,
    initialTap: TapPreview? = null,
): VideoGestureState = remember { VideoGestureState(initialSwipe, initialTap) }

/**
 * Taps and drags on the picture, one pointer loop for fullscreen, the inline box and the pane.
 *
 * - A pointer that goes down inside the system gesture [edges] is ignored entirely.
 * - Centre tap toggles the controls at once; a side tap waits for the double-tap timeout and
 *   toggles if no second tap comes; a second tap skips 10 s and each further tap in the chain
 *   adds 10 s, always from the position at the start of the chain.
 * - After the touch slop the axis is locked. Horizontal: swipe-seek with a preview on every move,
 *   one seek on release (a cancel never seeks). Vertical: only if [VideoSurfaceActions.vertical]
 *   is set; otherwise nothing is consumed, so a list or pane around the player scrolls.
 * - Nothing is consumed before the slop, and horizontal drags only once locked.
 */
@Composable
internal fun Modifier.videoSurfaceGestures(
    state: VideoGestureState,
    edges: WindowInsets,
    actions: () -> VideoSurfaceActions,
): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    return this
        .onPlaced(state::placed)
        .pointerInput(state, edges, layoutDirection) {
            coroutineScope {
                val scope = this
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val placed = state.coords?.takeIf { it.isAttached }
                    val rootWidth = placed?.findRootCoordinates()?.size?.width?.toFloat() ?: 0f
                    if (placed != null && rootWidth > 0f && FullscreenGestures.startsInGestureEdge(
                            placed.positionInRoot().x + down.position.x,
                            rootWidth,
                            edges.getLeft(this, layoutDirection).toFloat(),
                            edges.getRight(this, layoutDirection).toFloat(),
                        )
                    ) {
                        do {
                            val pressed = awaitPointerEvent().changes.any { it.pressed }
                        } while (pressed)
                        return@awaitEachGesture
                    }
                    val slop = viewConfiguration.touchSlop
                    var total = Offset.Zero
                    var horizontal = false
                    var startX = 0f
                    var locked = false
                    while (!locked) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            ?: return@awaitEachGesture
                        if (change.isConsumed) return@awaitEachGesture
                        if (!change.pressed) {
                            change.consume()
                            state.tap(
                                scope,
                                sideOf(change.position.x, width),
                                change.uptimeMillis,
                                viewConfiguration.doubleTapTimeoutMillis,
                                actions,
                            )
                            return@awaitEachGesture
                        }
                        total += change.positionChange()
                        if (total.getDistance() > slop) {
                            horizontal = FullscreenGestures.isHorizontal(total.x, total.y)
                            if (!horizontal && actions().vertical == null) {
                                state.timer?.cancel()
                                state.timer = null
                                state.taps = DoubleTapSkipState()
                                return@awaitEachGesture
                            }
                            startX = change.position.x
                            change.consume()
                            locked = true
                        }
                    }
                    state.timer?.cancel()
                    state.timer = null
                    state.taps = DoubleTapSkipState()
                    state.rippleSide = null
                    val over = total - total / total.getDistance() * slop
                    if (horizontal) {
                        val start = actions().positionMs()
                        var accX = over.x
                        var target = start
                        state.swipeStartMs = start
                        state.swipeTargetMs = start
                        state.swiping = true
                        try {
                            fun update() {
                                val now = actions()
                                target = FullscreenGestures.seekPreview(start, now.durationMs(), accX, width)
                                state.swipeTargetMs = target
                                now.onRequestPreview(target)
                            }
                            update()
                            val released = dragUntilUp(down.id) { delta ->
                                accX += delta.x
                                update()
                            }
                            if (released && target != start) actions().onSeek(target)
                        } finally {
                            state.swiping = false
                        }
                    } else {
                        val swipe = actions().vertical ?: return@awaitEachGesture
                        var accY = over.y
                        swipe.start(startX, width)
                        try {
                            swipe.drag(-accY, height)
                            dragUntilUp(down.id) { delta ->
                                accY += delta.y
                                swipe.drag(-accY, height)
                            }
                        } finally {
                            swipe.end()
                        }
                    }
                }
            }
        }
}

/** Follows [id] until it lifts (true) or is consumed elsewhere or lost (false). */
private suspend fun AwaitPointerEventScope.dragUntilUp(id: PointerId, onMove: (Offset) -> Unit): Boolean {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return false
        if (change.isConsumed) return false
        if (!change.pressed) {
            change.consume()
            return true
        }
        onMove(change.positionChange())
        change.consume()
    }
}

/** One finished tap, through the double-tap state machine. All times are event times. */
private fun VideoGestureState.tap(
    scope: CoroutineScope,
    side: TapSide,
    atMs: Long,
    timeoutMs: Long,
    actions: () -> VideoSurfaceActions,
) {
    val (next, action) = onTap(taps, side, atMs, timeoutMs)
    taps = next
    timer?.cancel()
    timer = null
    when (action) {
        TapAction.ToggleControls -> {
            rippleSide = null
            actions().onToggleControls()
        }
        TapAction.Pending -> {
            rippleSide = null
            chainStartMs = actions().positionMs()
            val pendingAt = next.pendingAtMs ?: atMs
            timer = scope.launch {
                delay(timeoutMs)
                val (after, due) = onTimeout(taps, pendingAt + timeoutMs, timeoutMs)
                taps = after
                if (due == TapAction.ToggleControls) actions().onToggleControls()
            }
        }
        is TapAction.Skip -> {
            val now = actions()
            val signed = if (action.stepMs < 0L) -action.totalMs else action.totalMs
            val duration = now.durationMs().takeIf { it > 0L } ?: Long.MAX_VALUE
            now.onSeek((chainStartMs + signed).coerceIn(0L, duration))
            rippleSide = side
            rippleTotalMs = signed
            rippleSerial++
            timer = scope.launch {
                delay(ChainWindowMs + 1L)
                taps = DoubleTapSkipState()
                rippleSide = null
            }
        }
    }
}
