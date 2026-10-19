package com.shibi.musicplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.FullscreenGestures
import com.shibi.musicplayer.domain.TapSide
import com.shibi.musicplayer.video.PreviewFrame
import kotlin.math.abs
import kotlin.math.hypot

/** Bubble widths: bigger in fullscreen, the seek-bar bubble's size inline. */
internal val FullscreenSwipeBubbleWidth = 220.dp
internal val InlineSwipeBubbleWidth = 160.dp

private const val FrameIsCurrentMs = 1_000L
private const val RippleMs = 400
private val RippleTint = Color.White.copy(alpha = 0.18f)

/**
 * The swipe-seek bubble in the middle of the picture while [VideoGestureState.swiping], with
 * "+0:45 · 2:13" under the frame. It shrinks to fit a short box. Fixed white on black. The player
 * never reads the state; the label changes once a second, the frame as it arrives.
 */
@Composable
internal fun SwipeSeekOverlay(
    state: VideoGestureState,
    frame: State<PreviewFrame?>,
    bubbleWidth: Dp,
    modifier: Modifier = Modifier,
) {
    if (!state.swiping) return
    val label by remember(state) {
        derivedStateOf {
            FullscreenGestures.seekOffsetLabel(state.swipeTargetMs - state.swipeStartMs, state.swipeTargetMs)
        }
    }
    val loading by remember(state, frame) {
        derivedStateOf {
            val shown = frame.value
            shown == null || abs(shown.bucketMs - state.swipeTargetMs) > FrameIsCurrentMs
        }
    }
    val bitmap = frame.value?.bitmap
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        SeekPreviewBubble(
            frame = image,
            loading = loading,
            label = label,
            width = bubbleWidth,
            modifier = Modifier
                .align(Alignment.Center)
                .heightIn(max = (maxHeight - 16.dp).coerceAtLeast(0.dp)),
        )
    }
}

/**
 * The half-oval on the tapped side with the fast-forward or rewind icon and "+20s" / "-10s",
 * live as taps accumulate. The ripple grows over 400 ms (snaps under reduce motion), restarted on
 * each tap, and the whole thing fades out 200 ms after the chain ends. Progress is read in the
 * draw lambda only.
 */
@Composable
internal fun DoubleTapRipple(state: VideoGestureState, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val side = state.rippleSide
    // The last side and total, so the fade-out keeps its content.
    var shownSide by remember { mutableStateOf(side) }
    var shownTotal by remember { mutableLongStateOf(state.rippleTotalMs) }
    if (side != null) {
        shownSide = side
        shownTotal = state.rippleTotalMs
    }
    val progress = remember { Animatable(if (state.rippleSide != null) 1f else 0f) }
    val serial = state.rippleSerial
    LaunchedEffect(serial, reduceMotion) {
        if (serial == 0) return@LaunchedEffect
        if (reduceMotion) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(RippleMs))
        }
    }
    AnimatedVisibility(
        visible = side != null,
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(200)),
        modifier = modifier.fillMaxSize(),
    ) {
        val shown = shownSide ?: return@AnimatedVisibility
        val back = shown == TapSide.Back
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(if (back) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.4f)
                    .clip(halfOval(back))
                    .drawBehind {
                        drawCircle(
                            color = RippleTint,
                            radius = hypot(size.width, size.height) * progress.value,
                            center = Offset(if (back) 0f else size.width, size.height / 2f),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (back) Icons.Rounded.FastRewind else Icons.Rounded.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                    val seconds = (abs(shownTotal) / 1_000L).toInt()
                    Text(
                        text = stringResource(
                            if (back) R.string.video_skip_back else R.string.video_skip_forward,
                            seconds,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/** An oval twice as wide as the box, centred on the screen edge, so half of it shows. */
private fun halfOval(back: Boolean) = GenericShape { size, _ ->
    val w = size.width
    addOval(
        if (back) Rect(-w, 0f, w, size.height) else Rect(0f, 0f, 2f * w, size.height),
    )
}
