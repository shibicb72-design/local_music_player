package com.shibi.musicplayer.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.AspectMode
import com.shibi.musicplayer.domain.BrightnessMath
import com.shibi.musicplayer.domain.FullscreenGestures
import com.shibi.musicplayer.domain.FullscreenOrientation
import com.shibi.musicplayer.domain.PlaybackArbiter
import com.shibi.musicplayer.domain.SwipeTarget
import com.shibi.musicplayer.domain.SwipeVolumeMath
import com.shibi.musicplayer.domain.VideoFormat
import com.shibi.musicplayer.ui.background.ContrastSurface
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoUiState
import kotlinx.coroutines.delay

/** Test tag of the fullscreen player. */
internal const val FullscreenVideoTag = "video_fullscreen"

private const val ControlsAutoHideMs = 3_000L

/** The aspect label is read, not just glanced at, so it stays a little longer. */
private const val AspectLabelHoldMs = 1_200L

private val FullscreenScrim = Color.Black.copy(alpha = 0.55f)

/** What the transient chip shows. */
internal sealed interface FullscreenChip {
    data class Volume(val level: Int, val max: Int) : FullscreenChip
    data class Brightness(val level: Float) : FullscreenChip
    data class Seek(val targetMs: Long, val deltaMs: Long) : FullscreenChip
    data class Aspect(val mode: AspectMode) : FullscreenChip
}

/** The music stream, set with flag 0 so the system volume panel never appears. */
internal interface StreamVolume {
    val max: Int
    fun current(): Int
    fun set(volume: Int)
}

/** The window's own brightness; the system setting is never written. */
internal interface ScreenBrightness {
    fun current(): Float
    fun set(level: Float)
}

/**
 * The `video/fullscreen` route: the picture on black over the whole window, with no carousel
 * or chrome. It hides the system bars (a swipe shows them for a moment), turns wide videos
 * sideways and keeps portrait clips as the phone is held. Back or the exit button leaves;
 * leaving restores the bars, the orientation and the system brightness.
 */
@Composable
internal fun FullscreenVideo(
    state: VideoUiState,
    brightnessSwipe: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleAspect: () -> AspectMode,
    onExit: () -> Unit,
    surface: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    FullscreenWindow(
        activity = activity,
        orientation = PlaybackArbiter.fullscreenOrientation(state.aspect),
        keepScreenOn = state.isPlaying,
    )
    // Composed last in MusicApp, so this callback is the newest and wins over the others.
    BackHandler(onBack = onExit)
    val volume = remember(context) { MusicStreamVolume(context) }
    val brightness = remember(activity) { activity?.let(::WindowBrightness) }
    FullscreenVideoContent(
        state = state,
        brightnessSwipe = brightnessSwipe,
        onTogglePlayPause = onTogglePlayPause,
        onSeek = onSeek,
        onCycleAspect = onCycleAspect,
        onExit = onExit,
        surface = surface,
        volume = volume,
        brightness = brightness,
        modifier = modifier,
    )
}

/** Immersive bars, orientation, music-stream volume keys and screen-on while fullscreen. */
@Composable
private fun FullscreenWindow(activity: Activity?, orientation: FullscreenOrientation, keepScreenOn: Boolean) {
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val window = activity?.window
        val previousStream = activity?.volumeControlStream
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        // Hardware keys move the same stream as the swipe, even while the video is paused.
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (activity != null && previousStream != null) activity.volumeControlStream = previousStream
            view.keepScreenOn = false
        }
    }
    LaunchedEffect(activity, orientation) {
        activity?.requestedOrientation = when (orientation) {
            FullscreenOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            FullscreenOrientation.Keep -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(view, keepScreenOn) { view.keepScreenOn = keepScreenOn }
}

/**
 * The fullscreen picture, gestures and controls, without touching the window, so
 * screenshots can draw it. Taps toggle the controls (they hide after 3 s while playing);
 * a double-tap on the left or right third seeks 10 s; a horizontal drag seeks with a
 * preview; a vertical drag changes the brightness (left half, if on) or the volume (right).
 */
@Composable
internal fun FullscreenVideoContent(
    state: VideoUiState,
    brightnessSwipe: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleAspect: () -> AspectMode,
    onExit: () -> Unit,
    surface: @Composable () -> Unit,
    volume: StreamVolume?,
    brightness: ScreenBrightness?,
    modifier: Modifier = Modifier,
    initialControlsVisible: Boolean = false,
    initialChip: FullscreenChip? = null,
) {
    ContrastSurface(
        color = Color.Black,
        modifier = modifier
            .fillMaxSize()
            .testTag(FullscreenVideoTag),
    ) {
        var controlsVisible by rememberSaveable { mutableStateOf(initialControlsVisible) }
        var touches by remember { mutableIntStateOf(0) }
        // The chip shown now, and a counter that restarts its fade on every change.
        var chip by remember { mutableStateOf(initialChip) }
        var chipSerial by remember { mutableIntStateOf(0) }
        val playing = state.status == VideoStatus.Playing
        val current by rememberUpdatedState(state)
        val swipeBrightness by rememberUpdatedState(brightnessSwipe)

        fun showChip(next: FullscreenChip) {
            chip = next
            chipSerial++
        }

        LaunchedEffect(controlsVisible, playing, touches) {
            if (controlsVisible && playing) {
                delay(ControlsAutoHideMs)
                controlsVisible = false
            }
        }
        // A chip passed in for a screenshot (serial 0) stays put.
        LaunchedEffect(chipSerial) {
            if (chipSerial == 0) return@LaunchedEffect
            delay(if (chip is FullscreenChip.Aspect) AspectLabelHoldMs else GestureChipHoldMs)
            chip = null
        }

        Box(modifier = Modifier.fillMaxSize()) {
            surface()
            // Above the picture, so the video view never takes a touch.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = !controlsVisible
                                touches++
                            },
                            onDoubleTap = { offset ->
                                val delta = FullscreenGestures.doubleTapSeekMs(offset.x, size.width.toFloat())
                                if (delta != 0L) {
                                    val video = current
                                    val target = (video.positionMs + delta).coerceIn(
                                        0L,
                                        video.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE,
                                    )
                                    onSeek(target)
                                    showChip(FullscreenChip.Seek(target, delta))
                                }
                            },
                        )
                    }
                    .pointerInput(volume, brightness) {
                        var startX = 0f
                        var total = Offset.Zero
                        var axis: DragAxis? = null
                        var startVolume = 0
                        var startBrightness = 0f
                        var startPosition = 0L
                        var seekTarget = 0L
                        detectDragGestures(
                            onDragStart = { offset ->
                                startX = offset.x
                                total = Offset.Zero
                                axis = null
                                startVolume = volume?.current() ?: 0
                                startBrightness = brightness?.current() ?: 0f
                                startPosition = current.positionMs
                                seekTarget = startPosition
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                total += amount
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                // Decided once, from the direction that crossed the touch slop.
                                val dragAxis = axis ?: when {
                                    FullscreenGestures.isHorizontal(amount.x, amount.y) -> DragAxis.Seek
                                    FullscreenGestures.swipeTarget(startX, width) == SwipeTarget.Volume -> DragAxis.Volume
                                    else -> DragAxis.Brightness
                                }.also { axis = it }
                                when (dragAxis) {
                                    DragAxis.Seek -> {
                                        seekTarget = FullscreenGestures.seekPreview(
                                            startPosition,
                                            current.durationMs,
                                            total.x,
                                            width,
                                        )
                                        showChip(FullscreenChip.Seek(seekTarget, seekTarget - startPosition))
                                    }
                                    DragAxis.Volume -> volume?.let { stream ->
                                        val level = SwipeVolumeMath.volumeAfterDrag(
                                            startVolume,
                                            stream.max,
                                            -total.y,
                                            height,
                                        )
                                        if (level != stream.current()) stream.set(level)
                                        showChip(FullscreenChip.Volume(level, stream.max))
                                    }
                                    DragAxis.Brightness -> if (swipeBrightness) {
                                        brightness?.let { window ->
                                            val level = BrightnessMath.afterDrag(startBrightness, -total.y, height)
                                            window.set(level)
                                            showChip(FullscreenChip.Brightness(level))
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                if (axis == DragAxis.Seek && seekTarget != startPosition) onSeek(seekTarget)
                                axis = null
                            },
                            onDragCancel = { axis = null },
                        )
                    },
            )
            if (state.status == VideoStatus.Buffering) {
                CircularProgressIndicator(
                    color = OnVideo,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
                )
            }
            AnimatedVisibility(
                // Paused, ended or failed: the controls stay until something plays.
                visible = controlsVisible || !playing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.matchParentSize(),
            ) {
                FullscreenControls(
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
                    onCycleAspect = {
                        touches++
                        showChip(FullscreenChip.Aspect(onCycleAspect()))
                    },
                    onExit = onExit,
                    // V7 passes the tracks and subtitles sheet here; until then the button is hidden.
                    onOpenTracks = null,
                )
            }
            ChipSlot(
                chip = chip,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
            )
        }
    }
}

private enum class DragAxis { Seek, Volume, Brightness }

/** Fades the chip out with its last content, so the text does not vanish before the pill. */
@Composable
private fun ChipSlot(chip: FullscreenChip?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(chip) }
    if (chip != null) shown = chip
    AnimatedVisibility(
        visible = chip != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        shown?.let { content ->
            val (icon, text) = content.display()
            GestureChip(icon = icon, text = text)
        }
    }
}

@Composable
private fun FullscreenChip.display(): Pair<ImageVector, String> = when (this) {
    is FullscreenChip.Volume ->
        (if (level == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp) to
            SwipeVolumeMath.label(level, max)
    is FullscreenChip.Brightness -> Icons.Rounded.BrightnessMedium to BrightnessMath.label(level)
    is FullscreenChip.Seek -> {
        val icon = when {
            deltaMs == FullscreenGestures.DoubleTapSeekMs -> Icons.Rounded.Forward10
            deltaMs == -FullscreenGestures.DoubleTapSeekMs -> Icons.Rounded.Replay10
            deltaMs < 0L -> Icons.Rounded.FastRewind
            else -> Icons.Rounded.FastForward
        }
        icon to "${elapsedLabel(targetMs)}  (${FullscreenGestures.seekDelta(deltaMs)})"
    }
    is FullscreenChip.Aspect -> Icons.Rounded.AspectRatio to stringResource(mode.labelRes())
}

internal fun AspectMode.labelRes(): Int = when (this) {
    AspectMode.Fit -> R.string.aspect_fit
    AspectMode.Fill -> R.string.aspect_fill
    AspectMode.Stretch -> R.string.aspect_stretch
    AspectMode.Ratio16x9 -> R.string.aspect_16_9
    AspectMode.Ratio4x3 -> R.string.aspect_4_3
    AspectMode.Original -> R.string.aspect_original
}

/** White on the 55% scrim; colours are constants, never from the backdrop or theme. */
@Composable
private fun FullscreenControls(
    state: VideoUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onInteract: () -> Unit,
    onCycleAspect: () -> Unit,
    onExit: () -> Unit,
    onOpenTracks: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FullscreenScrim)
            .windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = OnVideo,
                )
            }
            TrackTitle(
                text = state.video?.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = OnVideo,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            if (onOpenTracks != null) {
                IconButton(onClick = onOpenTracks) {
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = stringResource(R.string.video_tracks),
                        tint = OnVideo,
                    )
                }
            }
            IconButton(onClick = onCycleAspect) {
                Icon(
                    imageVector = Icons.Rounded.AspectRatio,
                    contentDescription = stringResource(R.string.video_aspect),
                    tint = OnVideo,
                )
            }
        }
        val error = state.error
        if (state.status == VideoStatus.Error && error != null) {
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodyLarge,
                color = OnVideo,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 48.dp),
            )
        } else if (state.status != VideoStatus.Buffering) {
            CenterButton(
                status = state.status,
                onClick = onTogglePlayPause,
                buttonSize = 80.dp,
                iconSize = 52.dp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (state.status != VideoStatus.Error) {
            FullscreenSeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = onSeek,
                onInteract = onInteract,
                onExit = onExit,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
            )
        }
    }
}

/** Elapsed on the left, remaining on the right; a position bar, never a volume control. */
@Composable
private fun FullscreenSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onInteract: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }
    val shown = if (dragging) dragPosition else positionMs
    Column(modifier = modifier) {
        PositionSlider(
            shownMs = shown,
            durationMs = durationMs,
            onDrag = { value ->
                dragging = true
                dragPosition = value
                onInteract()
            },
            onDragEnd = {
                onSeek(dragPosition)
                dragging = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp)
                .height(28.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = elapsedLabel(shown),
                style = MaterialTheme.typography.labelLarge,
                color = OnVideo,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = remainingLabel(shown, durationMs),
                style = MaterialTheme.typography.labelLarge,
                color = OnVideoMuted,
            )
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.Rounded.FullscreenExit,
                    contentDescription = stringResource(R.string.video_fullscreen_exit),
                    tint = OnVideo,
                )
            }
        }
    }
}

/** `-58:51`; a dash while the length is unknown. */
internal fun remainingLabel(positionMs: Long, durationMs: Long): String =
    if (durationMs <= 0L) VideoFormat.MISSING else "-" + elapsedLabel((durationMs - positionMs).coerceAtLeast(0L))

private class MusicStreamVolume(context: Context) : StreamVolume {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    override val max: Int = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

    override fun current(): Int = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

    override fun set(volume: Int) {
        // Flag 0: no system volume panel. Do Not Disturb can refuse; the chip still shows the ask.
        runCatching { audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0) }
    }
}

private class WindowBrightness(private val activity: Activity) : ScreenBrightness {
    override fun current(): Float = BrightnessMath.start(
        windowBrightness = activity.window.attributes.screenBrightness,
        systemSetting = runCatching {
            Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull(),
    )

    override fun set(level: Float) {
        val window = activity.window
        window.attributes = window.attributes.apply { screenBrightness = level }
    }
}
