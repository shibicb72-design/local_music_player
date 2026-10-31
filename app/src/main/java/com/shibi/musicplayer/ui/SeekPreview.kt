package com.shibi.musicplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shibi.musicplayer.video.PreviewFrame
import com.shibi.musicplayer.video.VideoViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the player controls need beyond the plain playback callbacks, in one holder so six
 * composables do not each gain five parameters. The defaults do nothing, so a call site that
 * does not care (a screenshot, an old test) compiles unchanged. [previewFrame] is a [State] on
 * purpose: only [SeekPreviewOverlay] reads its value, so a new frame never recomposes the player.
 */
@Stable
internal class PlayerExtras(
    val onPause: () -> Unit = {},
    val onPlay: () -> Unit = {},
    val onSetSpeed: (Float) -> Unit = {},
    val onRequestPreview: (Long) -> Unit = {},
    val previewFrame: State<PreviewFrame?> = mutableStateOf(null),
)

internal val NoPlayerExtras = PlayerExtras()

/** Wires [PlayerExtras] to the view model; the frame stays a [State] (never read here). */
@Composable
internal fun rememberPlayerExtras(videoViewModel: VideoViewModel): PlayerExtras {
    val previewFrame = videoViewModel.previewFrame.collectAsStateWithLifecycle()
    return remember(videoViewModel) {
        PlayerExtras(
            onPause = videoViewModel::pause,
            onPlay = videoViewModel::play,
            onSetSpeed = videoViewModel::setSpeed,
            onRequestPreview = videoViewModel::requestPreview,
            previewFrame = previewFrame,
        )
    }
}

/**
 * One seek-bar drag, shared by the inline and fullscreen bars: the first [onDrag] of a gesture
 * remembers whether it was playing and pauses once; the release seeks, then resumes only if it
 * was playing. [dragging] and [positionMs] are snapshot state, read by the bubble in layout.
 */
@Stable
internal class ScrubState(initialMs: Long?) {

    var dragging by mutableStateOf(initialMs != null)
        private set

    var positionMs by mutableLongStateOf(initialMs ?: 0L)
        private set

    /** The slider's rectangle in the player's coordinates; zero until both are placed. */
    var trackInRoot by mutableStateOf(Rect.Zero)
        private set

    private var wasPlaying = false
    private var root: LayoutCoordinates? = null
    private var slider: LayoutCoordinates? = null

    fun onDrag(ms: Long, isPlaying: Boolean, onPause: () -> Unit, onRequestPreview: (Long) -> Unit) {
        if (!dragging) {
            wasPlaying = isPlaying
            dragging = true
            if (isPlaying) onPause()
        }
        positionMs = ms
        onRequestPreview(ms)
    }

    fun onDragEnd(onSeek: (Long) -> Unit, onPlay: () -> Unit) {
        if (!dragging) return
        onSeek(positionMs)
        if (wasPlaying) onPlay()
        wasPlaying = false
        dragging = false
    }

    /** The player left the composition mid-drag: resume if it was playing, never seek. */
    fun abandon(onPlay: () -> Unit) {
        if (dragging && wasPlaying) onPlay()
        wasPlaying = false
        dragging = false
    }

    fun placedRoot(coordinates: LayoutCoordinates) {
        root = coordinates
        updateTrack()
    }

    fun placedSlider(coordinates: LayoutCoordinates) {
        slider = coordinates
        updateTrack()
    }

    private fun updateTrack() {
        val player = root
        val bar = slider
        if (player != null && bar != null && player.isAttached && bar.isAttached) {
            trackInRoot = player.localBoundingBoxOf(bar)
        }
    }
}

@Composable
internal fun rememberScrubState(initialScrubMs: Long? = null): ScrubState =
    remember { ScrubState(initialScrubMs) }

private val BubbleWidth = 160.dp
private val BubbleGap = 8.dp
private val ThumbInset = 6.dp
private const val FrameIsCurrentMs = 1_000L

/**
 * The preview bubble above the slider thumb while [ScrubState.dragging]. Its place is computed
 * in a layout lambda, so a drag frame only lays out this bubble; the text changes once a second.
 */
@Composable
internal fun SeekPreviewOverlay(scrub: ScrubState, durationMs: Long, frame: State<PreviewFrame?>) {
    if (!scrub.dragging) return
    val seconds by remember(scrub) { derivedStateOf { scrub.positionMs / 1_000L } }
    val loading by remember(scrub, frame) {
        derivedStateOf {
            val shown = frame.value
            shown == null || abs(shown.bucketMs - scrub.positionMs) > FrameIsCurrentMs
        }
    }
    // Read here, not only in layout: the slider is placed after the first layout, and a change
    // (rare: a resize) must reach the lambda below. A drag frame never changes it.
    val track = scrub.trackInRoot
    val bitmap = frame.value?.bitmap
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(
        modifier = Modifier.layout { measurable, constraints ->
            val gap = BubbleGap.roundToPx()
            val placeable = measurable.measure(
                Constraints(
                    maxWidth = BubbleWidth.roundToPx().coerceAtMost(constraints.maxWidth),
                    maxHeight = (track.top.roundToInt() - gap).coerceAtLeast(0),
                ),
            )
            val inset = ThumbInset.toPx()
            val fraction = if (durationMs > 0L) (scrub.positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            val centre = track.left + inset + fraction * (track.width - 2f * inset)
            val x = (centre - placeable.width / 2f).roundToInt()
                .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
            val y = (track.top.roundToInt() - gap - placeable.height).coerceAtLeast(0)
            layout(0, 0) { placeable.place(x, y) }
        },
    ) {
        SeekPreviewBubble(frame = image, loading = loading, label = elapsedLabel(seconds * 1_000L))
    }
}

/**
 * A 12 dp card with the frame (blurred while the right one loads, a dark placeholder when there
 * is none) and the time in a pill below. Fixed light-on-dark colours, never the theme's.
 */
@Composable
internal fun SeekPreviewBubble(
    frame: ImageBitmap?,
    loading: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    width: Dp = BubbleWidth,
) {
    val aspect = frame
        ?.let { it.width.toFloat() / it.height.coerceAtLeast(1) }
        ?.coerceIn(9f / 16f, 21f / 9f)
        ?: (16f / 9f)
    Column(
        modifier = modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
        ) {
            if (frame != null) {
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (loading) Modifier.blur(8.dp) else Modifier),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF202020)))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
