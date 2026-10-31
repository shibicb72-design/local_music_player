package com.shibi.musicplayer.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.domain.VideoFormat
import com.shibi.musicplayer.library.videoThumbnailRequest
import com.shibi.musicplayer.ui.background.ContrastSurface
import com.shibi.musicplayer.video.VideoEngine
import com.shibi.musicplayer.video.VideoError
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoUiState
import kotlinx.coroutines.delay

private const val ControlsAutoHideMs = 3_000L

/** Test tag of the compact inline box. */
internal const val InlineVideoTag = "inline_video"

/** Fixed, never from the backdrop: the box is black in every theme and on every pastel. */
internal val OnVideo = Color.White
internal val OnVideoMuted = Color.White.copy(alpha = 0.72f)
private val VideoScrim = Color.Black.copy(alpha = 0.55f)

/**
 * The compact player box above the Videos list. Height is `min(width / aspect, half of
 * [maxHeight])`, 16:9 until the size is known; the picture is letterboxed on black.
 * Switching videos glides the box to the new aspect and keeps the new video's poster
 * visible until its first frame; a tap shows or hides the controls, which hide after 3 s
 * while playing.
 */
@Composable
internal fun InlineVideoPlayer(
    state: VideoUiState,
    maxHeight: Dp,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    surface: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onFullscreen: () -> Unit = {},
    onOpenTracks: (() -> Unit)? = null,
    holdControls: Boolean = false,
    initialControlsVisible: Boolean = false,
    onStartOver: () -> Unit = {},
    extras: PlayerExtras = NoPlayerExtras,
    initialScrubMs: Long? = null,
    initialSpeedMenuOpen: Boolean = false,
    gestureEdges: WindowInsets = WindowInsets.systemGestures,
    initialSwipe: SwipePreview? = null,
    initialTap: TapPreview? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val targetHeightPx = with(LocalDensity.current) {
            InlineVideoBox.height(maxWidth.value, maxHeight.value, state.aspect).dp.toPx()
        }
        val height = rememberGlide(targetHeightPx)
        VideoPlayerBox(
            state = state,
            onTogglePlayPause = onTogglePlayPause,
            onSeek = onSeek,
            onClose = onClose,
            surface = surface,
            onFullscreen = onFullscreen,
            onOpenTracks = onOpenTracks,
            holdControls = holdControls,
            initialControlsVisible = initialControlsVisible,
            onStartOver = onStartOver,
            extras = extras,
            initialScrubMs = initialScrubMs,
            initialSpeedMenuOpen = initialSpeedMenuOpen,
            gestureEdges = gestureEdges,
            initialSwipe = initialSwipe,
            initialTap = initialTap,
            modifier = Modifier
                .fillMaxWidth()
                .animatedHeight { height.value }
                .testTag(InlineVideoTag),
        )
    }
}

/**
 * The picture on black with tap-to-show controls, sized by the caller: the compact inline
 * box and the two-pane video pane share it. The controls hide after 3 s while playing.
 */
@Composable
internal fun VideoPlayerBox(
    state: VideoUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    surface: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onFullscreen: () -> Unit = {},
    /** Null hides the tracks button. */
    onOpenTracks: (() -> Unit)? = null,
    /** The tracks sheet is open: the controls stay up behind it. */
    holdControls: Boolean = false,
    initialControlsVisible: Boolean = false,
    /** The resume chip's Start over. */
    onStartOver: () -> Unit = {},
    extras: PlayerExtras = NoPlayerExtras,
    /** Test-only: draws the preview bubble as if the thumb were being dragged to this position. */
    initialScrubMs: Long? = null,
    /** Test-only: draws the speed menu open. */
    initialSpeedMenuOpen: Boolean = false,
    /** Pointers that go down inside these edges are ignored (back gesture, edge panel). */
    gestureEdges: WindowInsets = WindowInsets.systemGestures,
    /** Test-only: draws the swipe-seek bubble as if a swipe were under way. */
    initialSwipe: SwipePreview? = null,
    /** Test-only: draws the double-tap ripple as if a chain were under way. */
    initialTap: TapPreview? = null,
) {
    ContrastSurface(
        color = Color.Black,
        modifier = modifier.reportVideoBounds(LocalVideoBounds.current),
    ) {
        var controlsVisible by rememberSaveable { mutableStateOf(initialControlsVisible) }
        var touches by remember { mutableIntStateOf(0) }
        var speedMenuOpen by remember { mutableStateOf(false) }
        val scrub = rememberScrubState(initialScrubMs)
        val resume by rememberUpdatedState(extras.onPlay)
        DisposableEffect(scrub) { onDispose { scrub.abandon(resume) } }
        val playing = state.status == VideoStatus.Playing
        val held = holdControls || speedMenuOpen
        LaunchedEffect(controlsVisible, playing, touches, held) {
            if (controlsVisible && playing && !held) {
                delay(ControlsAutoHideMs)
                controlsVisible = false
            }
        }
        val gestures = rememberVideoGestureState(initialSwipe, initialTap)
        val current by rememberUpdatedState(state)
        val toggleControls: () -> Unit = {
            controlsVisible = !controlsVisible
            touches++
        }
        // Read by the pointer loop at each step; the player never reads the gesture state.
        // No vertical swipe here: a vertical drag is left to the page or pane around the box.
        val actions by rememberUpdatedState(
            VideoSurfaceActions(
                positionMs = { current.positionMs },
                durationMs = { current.durationMs },
                onToggleControls = toggleControls,
                onSeek = onSeek,
                onRequestPreview = extras.onRequestPreview,
                vertical = null,
            ),
        )
        Box(modifier = Modifier.fillMaxSize().onPlaced { scrub.placedRoot(it) }) {
            surface()
            PosterLayer(state)
            // Above the picture, so the video view never takes the tap.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .videoSurfaceGestures(gestures, gestureEdges) { actions }
                    .semantics {
                        onClick {
                            toggleControls()
                            true
                        }
                    },
            )
            DoubleTapRipple(gestures)
            if (state.status == VideoStatus.Buffering) {
                CircularProgressIndicator(
                    color = OnVideo,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp),
                )
            }
            AnimatedVisibility(
                // Paused, ended or failed: the controls stay until something plays.
                visible = controlsVisible || !playing,
                enter = controlsEnter(),
                exit = controlsExit(),
                modifier = Modifier.matchParentSize(),
            ) {
                InlineControls(
                    state = state,
                    onTogglePlayPause = {
                        touches++
                        onTogglePlayPause()
                    },
                    onSeek = { position ->
                        touches++
                        onSeek(position)
                    },
                    onInteract = { touches++ },
                    onClose = onClose,
                    onFullscreen = onFullscreen,
                    onOpenTracks = onOpenTracks?.let { open ->
                        {
                            controlsVisible = true
                            touches++
                            open()
                        }
                    },
                    scrub = scrub,
                    extras = extras,
                    initialSpeedMenuOpen = initialSpeedMenuOpen,
                    onSpeedMenuOpenChange = { speedMenuOpen = it },
                )
            }
            // Over the title, clear of the tracks and close buttons; above the controls, so
            // Start over works whether they show or not.
            ResumeChipSlot(
                resumedAtMs = state.resumedAtMs,
                onStartOver = onStartOver,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 6.dp, end = 104.dp),
            )
            // Last, outside the animated controls: clipped to the player, free of the bar's padding.
            SeekPreviewOverlay(scrub, state.durationMs, extras.previewFrame)
            SwipeSeekOverlay(gestures, extras.previewFrame, InlineSwipeBubbleWidth)
        }
    }
}

/** The engine's own view (a libVLC texture view in the app, a placeholder frame in tests). */
@Composable
internal fun VideoSurface(engine: VideoEngine, modifier: Modifier = Modifier) {
    // A new engine (after ✕ and another tap) needs a new view.
    key(engine) {
        AndroidView(
            factory = { context -> engine.createView(context).also(engine::attach) },
            modifier = modifier.fillMaxSize(),
            onRelease = { view -> engine.detach(view) },
        )
    }
}

/**
 * The new video's thumbnail covering the picture until a frame can be on screen: playing
 * past 0, or paused or ended with a position. When the video switches, the key restarts
 * the poster and it is opaque again, because the state is buffering at 0. It fades away
 * once real pixels are on screen.
 */
@Composable
private fun PosterLayer(state: VideoUiState) {
    val video = state.video ?: return
    val showPoster = when (state.status) {
        VideoStatus.Idle, VideoStatus.Buffering -> true
        VideoStatus.Playing -> state.positionMs <= 0L
        else -> false
    }
    val alpha = animateFloatAsState(
        targetValue = if (showPoster) 1f else 0f,
        animationSpec = if (rememberReduceMotion()) snap() else tween(200),
        label = "poster",
    )
    val context = LocalContext.current
    key(video.contentUri) {
        AsyncImage(
            model = remember(video.contentUri) { videoThumbnailRequest(context, video.contentUri) },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha.value },
        )
    }
}

/** `0:00` at the start; [VideoFormat.duration] dashes zero, which suits a missing length only. */
internal fun elapsedLabel(positionMs: Long): String =
    if (positionMs < 1_000L) "0:00" else VideoFormat.duration(positionMs)

@StringRes
internal fun VideoError.messageRes(): Int = when (this) {
    VideoError.CannotOpen -> R.string.video_error_open
    VideoError.Playback -> R.string.video_error_playback
}

@Composable
private fun InlineControls(
    state: VideoUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onInteract: () -> Unit,
    onClose: () -> Unit,
    onFullscreen: () -> Unit,
    onOpenTracks: (() -> Unit)?,
    scrub: ScrubState,
    extras: PlayerExtras,
    initialSpeedMenuOpen: Boolean,
    onSpeedMenuOpenChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VideoScrim),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, end = 4.dp)
                .graphicsLayer { alpha = if (scrub.dragging) 0f else 1f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackTitle(
                text = state.video?.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = OnVideo,
                modifier = Modifier.weight(1f),
            )
            if (onOpenTracks != null && state.status != VideoStatus.Error) {
                IconButton(onClick = onOpenTracks) {
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = stringResource(R.string.video_tracks),
                        tint = OnVideo,
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.video_close),
                    tint = OnVideo,
                )
            }
        }
        val error = state.error
        if (state.status == VideoStatus.Error && error != null) {
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = OnVideo,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            )
        } else if (state.status != VideoStatus.Buffering) {
            CenterButton(
                status = state.status,
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = if (scrub.dragging) 0f else 1f },
            )
        }
        if (state.status != VideoStatus.Error) {
            InlineSeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                speed = state.speed,
                isPlaying = state.status == VideoStatus.Playing || state.status == VideoStatus.Buffering,
                scrub = scrub,
                extras = extras,
                initialSpeedMenuOpen = initialSpeedMenuOpen,
                onSpeedMenuOpenChange = onSpeedMenuOpenChange,
                onSeek = onSeek,
                onInteract = onInteract,
                onFullscreen = onFullscreen,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun CenterButton(
    status: VideoStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 64.dp,
    iconSize: Dp = 40.dp,
) {
    val (icon, label) = when (status) {
        VideoStatus.Playing -> Icons.Rounded.Pause to R.string.pause
        VideoStatus.Ended -> Icons.Rounded.Replay to R.string.video_replay
        else -> Icons.Rounded.PlayArrow to R.string.play
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.size(buttonSize),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.35f),
            contentColor = OnVideo,
        ),
        shape = CircleShape,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(label),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** The white position slider shared by the box, the pane and fullscreen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PositionSlider(
    shownMs: Long,
    durationMs: Long,
    onDrag: (Long) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rangeEnd = durationMs.coerceAtLeast(1L).toFloat()
    val seekDescription = stringResource(R.string.seek)
    Slider(
        value = shownMs.toFloat().coerceIn(0f, rangeEnd),
        onValueChange = { value -> onDrag(value.toLong()) },
        onValueChangeFinished = onDragEnd,
        enabled = durationMs > 0L,
        valueRange = 0f..rangeEnd,
        thumb = {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(OnVideo, CircleShape),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(3.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = OnVideo,
                    inactiveTrackColor = OnVideo.copy(alpha = 0.3f),
                    disabledActiveTrackColor = OnVideo.copy(alpha = 0.5f),
                    disabledInactiveTrackColor = OnVideo.copy(alpha = 0.3f),
                ),
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
            )
        },
        modifier = modifier.semantics { contentDescription = seekDescription },
    )
}

/** A position bar with elapsed and total times; never a volume control. */
@Composable
private fun InlineSeekBar(
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    isPlaying: Boolean,
    scrub: ScrubState,
    extras: PlayerExtras,
    initialSpeedMenuOpen: Boolean,
    onSpeedMenuOpenChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onInteract: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = if (scrub.dragging) scrub.positionMs else positionMs
    Column(modifier = modifier.padding(start = 16.dp, end = 4.dp)) {
        PositionSlider(
            shownMs = shown,
            durationMs = durationMs,
            onDrag = { value ->
                // One interaction per drag, not per frame: a touch restarts the hide timer.
                if (!scrub.dragging) onInteract()
                scrub.onDrag(value, isPlaying, extras.onPause, extras.onRequestPreview)
            },
            onDragEnd = { scrub.onDragEnd(onSeek, extras.onPlay) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp)
                .height(24.dp)
                .onPlaced { scrub.placedSlider(it) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${elapsedLabel(shown)} / ${VideoFormat.duration(durationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = OnVideoMuted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeedButton(
                    speed = speed,
                    onSetSpeed = {
                        onInteract()
                        extras.onSetSpeed(it)
                    },
                    initialOpen = initialSpeedMenuOpen,
                    onMenuOpenChange = onSpeedMenuOpenChange,
                )
                IconButton(onClick = onFullscreen) {
                    Icon(
                        imageVector = Icons.Rounded.Fullscreen,
                        contentDescription = stringResource(R.string.video_fullscreen),
                        tint = OnVideo,
                    )
                }
            }
        }
    }
}
