package com.shibi.musicplayer.v23

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.shibi.musicplayer.FakeVideoEngine
import com.shibi.musicplayer.VideosScreenshotTest
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.AspectMode
import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.ui.FullscreenVideoContent
import com.shibi.musicplayer.ui.InlineVideoPlayer
import com.shibi.musicplayer.ui.PlayerExtras
import com.shibi.musicplayer.ui.VideoPane
import com.shibi.musicplayer.ui.VideoSurface
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import com.shibi.musicplayer.video.PreviewFrame
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val Folded = "w411dp-h891dp-xxhdpi"
private const val FoldedLandscape = "w891dp-h411dp-xxhdpi"
private const val Unfolded = "w900dp-h600dp-xhdpi"

/** About 20:40 into a 1:02:03 video. */
private const val Scrub = 1_240_000L

private val concert: Video = VideosScreenshotTest.videos.first { it.id == 2L }
private val screenRecording: Video = VideosScreenshotTest.videos.first { it.id == 5L }

private fun paused(video: Video, positionMs: Long = 192_000L) = VideoUiState(
    video = video,
    status = VideoStatus.Paused,
    positionMs = positionMs,
    durationMs = video.durationMs ?: 0L,
    aspect = InlineVideoBox.displayAspect(video.width ?: 0, video.height ?: 0),
)

private fun engineFor(video: VideoUiState) =
    FakeVideoEngine(frameAspect = video.aspect ?: InlineVideoBox.DefaultAspect)

private fun gradientFrame(): Bitmap {
    val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
    val paint = Paint().apply {
        shader = LinearGradient(0f, 0f, 320f, 180f, 0xFFE8743B.toInt(), 0xFF19A7CE.toInt(), Shader.TileMode.CLAMP)
    }
    Canvas(bitmap).drawRect(0f, 0f, 320f, 180f, paint)
    return bitmap
}

private fun extrasWithFrame(bucketMs: Long): PlayerExtras {
    val frame: State<PreviewFrame?> = mutableStateOf(PreviewFrame(bucketMs, gradientFrame()))
    return PlayerExtras(previewFrame = frame)
}

@Composable
private fun ThemedSurface(dark: Boolean, content: @Composable () -> Unit) {
    MusicPlayerTheme(darkTheme = dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}

@Composable
private fun Fullscreen(
    video: VideoUiState,
    extras: PlayerExtras = PlayerExtras(),
    scrubMs: Long? = null,
    speedMenuOpen: Boolean = false,
) {
    val engine = engineFor(video)
    MusicPlayerTheme {
        AppBackgroundHost(AppBackground.Solid("sky"), emptyMap(), forceStatic = true) {
            FullscreenVideoContent(
                state = video,
                brightnessSwipe = true,
                onTogglePlayPause = {},
                onSeek = {},
                onCycleAspect = { AspectMode.Fill },
                onExit = {},
                surface = { VideoSurface(engine) },
                volume = null,
                brightness = null,
                initialControlsVisible = true,
                onOpenTracks = {},
                extras = extras,
                initialScrubMs = scrubMs,
                initialSpeedMenuOpen = speedMenuOpen,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * V23 h02: the seek-preview bubble above the thumb while dragging, and the speed label. The
 * bubble is drawn from a test-only parameter (no gestures) with a gradient bitmap as the frame;
 * the picture behind it is [FakeVideoEngine]'s placeholder.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SeekPreviewScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The bubble needs a second layout pass (the slider is placed after the first), so wait for
     * idle before capturing; the content-lambda overload of captureRoboImage shoots too early.
     */
    private fun shoot(path: String, content: @Composable () -> Unit) {
        compose.setContent(content)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(path)
    }

    @Test
    @Config(qualifiers = Folded)
    fun inlineFoldedDark() = inline("v23_seek_preview_inline_folded_dark.png", dark = true)

    @Test
    @Config(qualifiers = Folded)
    fun inlineFoldedLight() = inline("v23_seek_preview_inline_folded_light.png", dark = false)

    /** The cover screen's short box: the bubble must shrink to fit above the bar, not clip. */
    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun inlineShort() = inline(
        "v23_seek_preview_inline_short.png",
        dark = true,
        video = paused(screenRecording, positionMs = 17_000L),
        scrubMs = 21_000L,
    )

    @Test
    @Config(qualifiers = Unfolded)
    fun paneUnfoldedDark() = pane("v23_seek_preview_pane_unfolded_dark.png", dark = true)

    @Test
    @Config(qualifiers = Unfolded)
    fun paneUnfoldedLight() = pane("v23_seek_preview_pane_unfolded_light.png", dark = false)

    @Test
    @Config(qualifiers = FoldedLandscape)
    fun fullscreenFolded() = fullscreen("v23_seek_preview_fullscreen_folded.png")

    @Test
    @Config(qualifiers = Unfolded)
    fun fullscreenUnfolded() = fullscreen("v23_seek_preview_fullscreen_unfolded.png")

    /** The picture for this second is not here yet: the last one is shown blurred. */
    @Test
    @Config(qualifiers = FoldedLandscape)
    fun loadingFullscreenFolded() = fullscreen(
        "v23_seek_preview_loading_fullscreen_folded.png",
        scrubMs = 1_500_000L,
        frameBucketMs = 0L,
    )

    /** The top bar names the current speed (1.5x) between Subtitles and Picture shape. */
    @Test
    @Config(qualifiers = FoldedLandscape)
    fun speedLabelFullscreenFolded() {
        val video = paused(concert).copy(speed = 1.5f)
        shoot("build/outputs/roborazzi/v23_speed_label_fullscreen_folded.png") {
            Fullscreen(video)
        }
    }

    private fun inline(
        fileName: String,
        dark: Boolean,
        video: VideoUiState = paused(concert),
        scrubMs: Long = Scrub,
    ) {
        val engine = engineFor(video)
        val extras = extrasWithFrame(scrubMs)
        shoot("build/outputs/roborazzi/$fileName") {
            ThemedSurface(dark) {
                InlineVideoPlayer(
                    state = video,
                    maxHeight = 420.dp,
                    onTogglePlayPause = {},
                    onSeek = {},
                    onClose = {},
                    surface = { VideoSurface(engine) },
                    onOpenTracks = {},
                    extras = extras,
                    initialScrubMs = scrubMs,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }
    }

    private fun pane(fileName: String, dark: Boolean) {
        val video = paused(concert)
        val engine = engineFor(video)
        val extras = extrasWithFrame(Scrub)
        shoot("build/outputs/roborazzi/$fileName") {
            ThemedSurface(dark) {
                Box(Modifier.fillMaxHeight().width(522.dp)) {
                    VideoPane(
                        state = video,
                        onTogglePlayPause = {},
                        onSeek = {},
                        onClose = {},
                        surface = { VideoSurface(engine) },
                        onOpenTracks = {},
                        extras = extras,
                        initialScrubMs = Scrub,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                            .clip(RoundedCornerShape(28.dp)),
                    )
                }
            }
        }
    }

    private fun fullscreen(fileName: String, scrubMs: Long = Scrub, frameBucketMs: Long = scrubMs) {
        val extras = extrasWithFrame(frameBucketMs)
        shoot("build/outputs/roborazzi/$fileName") {
            Fullscreen(paused(concert), extras = extras, scrubMs = scrubMs)
        }
    }
}

/**
 * V23 h02: the speed menu open. A popup is its own window, so the whole screen is captured
 * ([captureScreenRoboImage]); the compose root alone would miss it.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SpeedMenuScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = Folded)
    fun inlineFoldedDark() = inline("v23_speed_menu_inline_folded_dark.png", dark = true)

    @Test
    @Config(qualifiers = Folded)
    fun inlineFoldedLight() = inline("v23_speed_menu_inline_folded_light.png", dark = false)

    @Test
    @Config(qualifiers = Unfolded)
    fun fullscreenUnfolded() {
        val video = paused(concert).copy(speed = 1.5f)
        compose.setContent { Fullscreen(video, speedMenuOpen = true) }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/v23_speed_menu_fullscreen_unfolded.png")
    }

    private fun inline(fileName: String, dark: Boolean) {
        val video = paused(concert).copy(speed = 1.5f)
        val engine = engineFor(video)
        compose.setContent {
            ThemedSurface(dark) {
                InlineVideoPlayer(
                    state = video,
                    maxHeight = 420.dp,
                    onTogglePlayPause = {},
                    onSeek = {},
                    onClose = {},
                    surface = { VideoSurface(engine) },
                    onOpenTracks = {},
                    initialSpeedMenuOpen = true,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/$fileName")
    }
}
