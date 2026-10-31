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
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.FakeVideoEngine
import com.shibi.musicplayer.VideosScreenshotTest
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.AspectMode
import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.domain.TapSide
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.ui.FullscreenVideoContent
import com.shibi.musicplayer.ui.InlineVideoPlayer
import com.shibi.musicplayer.ui.PlayerExtras
import com.shibi.musicplayer.ui.SwipePreview
import com.shibi.musicplayer.ui.TapPreview
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

/** A swipe from 20:00 to 20:45 of a 1:02:03 video; the frame is the one for the target. */
private val Swipe = SwipePreview(startMs = 1_200_000L, targetMs = 1_245_000L)
private val Forward30 = TapPreview(TapSide.Forward, 30_000L)
private val Back10 = TapPreview(TapSide.Back, -10_000L)

private val concert: Video = VideosScreenshotTest.videos.first { it.id == 2L }

private fun playing(video: Video) = VideoUiState(
    video = video,
    status = VideoStatus.Playing,
    positionMs = Swipe.startMs,
    durationMs = video.durationMs ?: 0L,
    aspect = InlineVideoBox.displayAspect(video.width ?: 0, video.height ?: 0),
)

private fun engineFor(state: VideoUiState) =
    FakeVideoEngine(frameAspect = state.aspect ?: InlineVideoBox.DefaultAspect)

private fun gradientFrame(): Bitmap {
    val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
    val paint = Paint().apply {
        shader = LinearGradient(0f, 0f, 320f, 180f, 0xFFE8743B.toInt(), 0xFF19A7CE.toInt(), Shader.TileMode.CLAMP)
    }
    Canvas(bitmap).drawRect(0f, 0f, 320f, 180f, paint)
    return bitmap
}

private fun extras(): PlayerExtras {
    val frame: State<PreviewFrame?> = mutableStateOf(PreviewFrame(Swipe.targetMs, gradientFrame()))
    return PlayerExtras(previewFrame = frame)
}

@Composable
private fun ThemedSurface(dark: Boolean, content: @Composable () -> Unit) {
    MusicPlayerTheme(darkTheme = dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/**
 * V23 h03: the swipe-seek bubble and the double-tap ripple, drawn from test-only initial
 * parameters (no gestures), with a gradient bitmap as the preview frame.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class VideoGesturesScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shoot(fileName: String, content: @Composable () -> Unit) {
        compose.setContent(content)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$fileName")
    }

    @Test
    @Config(qualifiers = FoldedLandscape)
    fun swipeFullscreenFolded() = fullscreen("v23_swipe_seek_fullscreen_folded.png", swipe = Swipe)

    @Test
    @Config(qualifiers = Unfolded)
    fun swipeFullscreenUnfolded() = fullscreen("v23_swipe_seek_fullscreen_unfolded.png", swipe = Swipe)

    @Test
    @Config(qualifiers = Folded)
    fun swipeInlineFoldedLight() = inline("v23_swipe_seek_inline_folded_light.png", dark = false, swipe = Swipe)

    @Test
    @Config(qualifiers = Folded)
    fun swipeInlineFoldedDark() = inline("v23_swipe_seek_inline_folded_dark.png", dark = true, swipe = Swipe)

    @Test
    @Config(qualifiers = Unfolded)
    fun swipePaneUnfoldedLight() = pane("v23_swipe_seek_pane_unfolded_light.png", dark = false, swipe = Swipe)

    @Test
    @Config(qualifiers = Unfolded)
    fun swipePaneUnfoldedDark() = pane("v23_swipe_seek_pane_unfolded_dark.png", dark = true, swipe = Swipe)

    @Test
    @Config(qualifiers = FoldedLandscape)
    fun doubleTapFullscreenFolded() = fullscreen("v23_double_tap_fullscreen_folded.png", tap = Forward30)

    @Test
    @Config(qualifiers = Unfolded)
    fun doubleTapFullscreenUnfolded() = fullscreen("v23_double_tap_fullscreen_unfolded.png", tap = Forward30)

    @Test
    @Config(qualifiers = Folded)
    fun doubleTapInlineFoldedLight() = inline("v23_double_tap_inline_folded_light.png", dark = false, tap = Back10)

    @Test
    @Config(qualifiers = Folded)
    fun doubleTapInlineFoldedDark() = inline("v23_double_tap_inline_folded_dark.png", dark = true, tap = Back10)

    private fun fullscreen(fileName: String, swipe: SwipePreview? = null, tap: TapPreview? = null) {
        val video = playing(concert)
        val engine = engineFor(video)
        val extras = extras()
        shoot(fileName) {
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
                        extras = extras,
                        initialSwipe = swipe,
                        initialTap = tap,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    private fun inline(
        fileName: String,
        dark: Boolean,
        swipe: SwipePreview? = null,
        tap: TapPreview? = null,
    ) {
        val video = playing(concert)
        val engine = engineFor(video)
        val extras = extras()
        shoot(fileName) {
            ThemedSurface(dark) {
                InlineVideoPlayer(
                    state = video,
                    maxHeight = 420.dp,
                    onTogglePlayPause = {},
                    onSeek = {},
                    onClose = {},
                    surface = { VideoSurface(engine) },
                    extras = extras,
                    initialSwipe = swipe,
                    initialTap = tap,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }
    }

    private fun pane(fileName: String, dark: Boolean, swipe: SwipePreview? = null) {
        val video = playing(concert)
        val engine = engineFor(video)
        val extras = extras()
        shoot(fileName) {
            ThemedSurface(dark) {
                Box(Modifier.fillMaxHeight().width(522.dp)) {
                    VideoPane(
                        state = video,
                        onTogglePlayPause = {},
                        onSeek = {},
                        onClose = {},
                        surface = { VideoSurface(engine) },
                        extras = extras,
                        initialSwipe = swipe,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                            .clip(RoundedCornerShape(28.dp)),
                    )
                }
            }
        }
    }
}
