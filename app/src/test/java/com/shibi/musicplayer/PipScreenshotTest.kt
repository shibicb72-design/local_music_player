package com.shibi.musicplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.ui.PipVideoLayout
import com.shibi.musicplayer.ui.VideoSurface
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * V10 picture-in-picture: the small window is black, with only the engine's frame letterboxed
 * inside it, at the legal 1:2.39..2.39:1 shape. The picture is [FakeVideoEngine]'s frame.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w240dp-h135dp")
class PipScreenshotTest {

    /** A wide video fills the 16:9 window. */
    @Test
    fun pip_layout() = capture(concert, "pip_layout.png")

    /** A portrait recording is pillarboxed on the black window. */
    @Test
    fun pip_layout_portrait() = capture(screenRecording, "pip_layout_portrait.png")

    private fun capture(video: Video, fileName: String) {
        val engine = FakeVideoEngine(
            frameAspect = InlineVideoBox.displayAspect(video.width ?: 0, video.height ?: 0)
                ?: InlineVideoBox.DefaultAspect,
        )
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                PipVideoLayout(surface = { VideoSurface(engine) })
            }
        }
    }

    private companion object {
        val concert: Video = VideosScreenshotTest.videos.first { it.id == 2L }
        val screenRecording: Video = VideosScreenshotTest.videos.first { it.id == 5L }
    }
}
