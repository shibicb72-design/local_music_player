package com.shibi.musicplayer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.AspectMode
import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.ui.FullscreenChip
import com.shibi.musicplayer.ui.FullscreenVideoContent
import com.shibi.musicplayer.ui.VideoSurface
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * V6: the `video/fullscreen` route. Black over the whole window on every backdrop (sky here),
 * no carousel or chrome; white controls on the 55% scrim; the swipe feedback is a solid
 * dark chip only, never a bar or slider. The picture is [FakeVideoEngine]'s frame.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Landscape)
class FullscreenVideoScreenshotTest {

    /** The folded cover screen turned sideways, paused, so the controls hold. */
    @Test
    fun landscape() = capture(paused(concert), "video_fullscreen_landscape.png", controls = true)

    /** A portrait screen recording keeps the phone upright. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun portrait() = capture(
        paused(screenRecording, positionMs = 17_000L),
        "video_fullscreen_portrait.png",
        controls = true,
    )

    @Test
    fun volumeChip() = capture(
        playing(concert),
        "video_fullscreen_volume_chip.png",
        chip = FullscreenChip.Volume(level = 8, max = 15),
    )

    @Test
    fun brightnessChip() = capture(
        playing(concert),
        "video_fullscreen_brightness_chip.png",
        chip = FullscreenChip.Brightness(level = 0.6f),
    )

    /** The aspect button was just tapped: the controls are up and the new mode is named. */
    @Test
    fun aspectLabel() = capture(
        paused(concert),
        "video_fullscreen_aspect_label.png",
        controls = true,
        chip = FullscreenChip.Aspect(AspectMode.Fill),
    )

    private fun capture(
        video: VideoUiState,
        fileName: String,
        controls: Boolean = false,
        chip: FullscreenChip? = null,
    ) {
        val engine = FakeVideoEngine(frameAspect = video.aspect ?: InlineVideoBox.DefaultAspect)
        captureRoboImage("build/outputs/roborazzi/$fileName") {
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
                        initialControlsVisible = controls,
                        // V7: the tracks button V6 kept hidden.
                        onOpenTracks = {},
                        initialChip = chip,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    private companion object {
        val concert: Video = VideosScreenshotTest.videos.first { it.id == 2L }
        val screenRecording: Video = VideosScreenshotTest.videos.first { it.id == 5L }

        fun playing(video: Video, positionMs: Long = 192_000L) = VideoUiState(
            video = video,
            status = VideoStatus.Playing,
            positionMs = positionMs,
            durationMs = video.durationMs ?: 0L,
            aspect = InlineVideoBox.displayAspect(video.width ?: 0, video.height ?: 0),
        )

        fun paused(video: Video, positionMs: Long = 192_000L) =
            playing(video, positionMs).copy(status = VideoStatus.Paused)
    }
}

private const val Landscape = "w891dp-h411dp-xxhdpi"
