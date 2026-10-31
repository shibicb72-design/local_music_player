package com.shibi.musicplayer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.ui.InlineVideoPlayer
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.SectionRoutes
import com.shibi.musicplayer.ui.SectionScaffold
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.VideoSurface
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * V4: a video playing in the compact top-half box above the Videos list. The picture is
 * [FakeVideoEngine]'s placeholder frame (libVLC never loads here). No mini player while a
 * video is active; the carousel stays.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class InlineVideoScreenshotTest {

    @Test
    fun inlineDark() = capture(AppBackground.Default, playing(concert), "video_inline_dark.png")

    @Test
    fun inlinePastel() = capture(AppBackground.Solid("blush"), playing(concert), "video_inline_pastel.png")

    /** Paused, so the controls hold: white on the 55% scrim, never from the sky backdrop. */
    @Test
    fun inlineControls() = capture(
        AppBackground.Solid("sky"),
        playing(concert).copy(status = VideoStatus.Paused),
        "video_inline_controls.png",
        controls = true,
    )

    /** Cover-screen height with a portrait clip: the box caps at half the viewport, pillarboxed. */
    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun inlineShort() = capture(
        AppBackground.Default,
        playing(screenRecording, positionMs = 17_000L),
        "video_inline_short.png",
    )

    private fun capture(
        background: AppBackground,
        video: VideoUiState,
        fileName: String,
        controls: Boolean = false,
    ) {
        val engine = FakeVideoEngine(frameAspect = video.aspect ?: InlineVideoBox.DefaultAspect)
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    SectionScaffold(
                        modifier = Modifier.fillMaxSize(),
                        showMiniPlayer = false,
                        showCarousel = true,
                        contentBottomPadding = SongListBottomPadding,
                        carousel = {
                            NavCarousel(
                                selected = NavSection.Videos,
                                onSelect = {},
                                sections = SectionRoutes.sectionsFor(expanded = false),
                            )
                        },
                    ) { bottom ->
                        Videos(video, bottom, engine, controls)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    @Composable
    private fun Videos(video: VideoUiState, bottom: Dp, engine: FakeVideoEngine, controls: Boolean) {
        // Inspection mode feeds the row thumbnails; the surface below opts out of it again.
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
        ) {
            VideosContent(
                state = list,
                onVideoClick = {},
                onRequestAccess = {},
                onOpenSettings = {},
                contentBottomPadding = bottom,
                playingVideoId = video.video?.id,
                modifier = Modifier.fillMaxSize(),
                player = { maxHeight ->
                    InlineVideoPlayer(
                        state = video,
                        maxHeight = maxHeight,
                        onTogglePlayPause = {},
                        onSeek = {},
                        onClose = {},
                        surface = {
                            CompositionLocalProvider(LocalInspectionMode provides false) {
                                VideoSurface(engine)
                            }
                        },
                        initialControlsVisible = controls,
                        // V7: the box offers the tracks sheet, as in the app.
                        onOpenTracks = {},
                    )
                },
            )
        }
    }

    private companion object {
        val list = VideosScreenshotTest.full
        val concert: Video = VideosScreenshotTest.videos.first { it.id == 2L }
        val screenRecording: Video = VideosScreenshotTest.videos.first { it.id == 5L }

        fun playing(video: Video, positionMs: Long = 192_000L) = VideoUiState(
            video = video,
            status = VideoStatus.Playing,
            positionMs = positionMs,
            durationMs = video.durationMs ?: 0L,
            aspect = InlineVideoBox.displayAspect(video.width ?: 0, video.height ?: 0),
        )
    }
}
