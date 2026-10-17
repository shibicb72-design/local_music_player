package com.shibi.musicplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.AspectMode
import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.library.VideoLibraryUiState
import com.shibi.musicplayer.ui.FullscreenVideoContent
import com.shibi.musicplayer.ui.InlineVideoPlayer
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.SectionRoutes
import com.shibi.musicplayer.ui.SectionScaffold
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.TwoPanePlayerBottomPadding
import com.shibi.musicplayer.ui.VideoPane
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
 * V9: partly watched videos carry a hairline under their thumbnail, in the row's own text
 * colour (dark backdrop and two pastels); "Old holiday tape" has no MediaStore duration and
 * shows the one libVLC reported and we saved. A resumed video shows the solid dark
 * "Resumed at 12:34 · Start over" chip in the box, the two-pane pane and fullscreen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ResumeScreenshotTest {

    @Test
    fun withProgressDark() = captureList(AppBackground.Default, "videos_with_progress_dark.png")

    @Test
    fun withProgressSky() = captureList(AppBackground.Solid("sky"), "videos_with_progress_sky.png")

    @Test
    fun withProgressBlush() = captureList(AppBackground.Solid("blush"), "videos_with_progress_blush.png")

    /** The folded box just after a resume: chip over the title, controls hidden while it plays. */
    @Test
    fun resumeChip() = captureBox(AppBackground.Default, "video_resume_chip.png")

    /** Cover-screen width: the chip still clears the tracks and close buttons. */
    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun resumeChipShort() = captureBox(AppBackground.Solid("sky"), "video_resume_chip_short.png", controls = true)

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun resumeChipPane() {
        val engine = FakeVideoEngine(frameAspect = resumed.aspect ?: InlineVideoBox.DefaultAspect)
        captureRoboImage("build/outputs/roborazzi/video_resume_chip_pane.png") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Solid("blush"), emptyMap(), forceStatic = true) {
                    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                        VideoPane(
                            state = resumed,
                            onTogglePlayPause = {},
                            onSeek = {},
                            onClose = {},
                            surface = { VideoSurface(engine) },
                            onOpenTracks = {},
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = TwoPanePlayerBottomPadding)
                                .clip(RoundedCornerShape(28.dp)),
                        )
                    }
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun resumeChipFullscreen() {
        val engine = FakeVideoEngine(frameAspect = resumed.aspect ?: InlineVideoBox.DefaultAspect)
        captureRoboImage("build/outputs/roborazzi/video_resume_chip_fullscreen.png") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Solid("sky"), emptyMap(), forceStatic = true) {
                    FullscreenVideoContent(
                        state = resumed,
                        brightnessSwipe = true,
                        onTogglePlayPause = {},
                        onSeek = {},
                        onCycleAspect = { AspectMode.Fit },
                        onExit = {},
                        surface = { VideoSurface(engine) },
                        volume = null,
                        brightness = null,
                        onOpenTracks = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    private fun captureList(background: AppBackground, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            Scaffolded(background) { bottom -> Videos(bottom, player = null) }
        }
    }

    private fun captureBox(background: AppBackground, fileName: String, controls: Boolean = false) {
        val engine = FakeVideoEngine(frameAspect = resumed.aspect ?: InlineVideoBox.DefaultAspect)
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            Scaffolded(background) { bottom ->
                Videos(bottom) { maxHeight ->
                    InlineVideoPlayer(
                        state = resumed,
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
                        onOpenTracks = {},
                    )
                }
            }
        }
    }

    @Composable
    private fun Scaffolded(background: AppBackground, content: @Composable (Dp) -> Unit) {
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
                    content(bottom)
                }
            }
        }
    }

    /** Inspection mode only around the list, so the preview handler feeds the thumbnails. */
    @OptIn(ExperimentalCoilApi::class)
    @Composable
    private fun Videos(bottom: Dp, player: (@Composable (Dp) -> Unit)?) {
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
                playingVideoId = if (player != null) concert.id else null,
                savedProgress = saved,
                modifier = Modifier.fillMaxSize(),
                player = player,
            )
        }
    }

    private companion object {
        val list = VideoLibraryUiState(access = VideoAccess.Full, videos = VideosScreenshotTest.videos)
        val concert: Video = VideosScreenshotTest.videos.first { it.id == 2L }

        private fun uri(id: Long) = "content://media/external/video/media/$id"

        /**
         * Beach day half watched, Concert at 12:34, the WMV tape (no MediaStore length) a third
         * in with libVLC's 30:30 saved, the lecture near the end of its 95%, Birthday finished
         * (no line), Screen recording under 5 s (no line), Recital barely started.
         */
        val saved = mapOf(
            uri(1L) to VideoProgress(377_000L, 754_000L),
            uri(2L) to VideoProgress(754_000L, 3_723_000L),
            uri(3L) to VideoProgress(610_000L, 1_830_000L),
            uri(4L) to VideoProgress(5_000_000L, 5_400_000L),
            uri(5L) to VideoProgress(3_000L, 42_000L),
            uri(6L) to VideoProgress(0L, 186_000L),
            uri(9L) to VideoProgress(80_000L, 2_640_000L),
        )

        /** Just resumed at 12:34 and playing, so only the chip shows over the picture. */
        val resumed = VideoUiState(
            video = concert,
            status = VideoStatus.Playing,
            positionMs = 754_000L,
            durationMs = concert.durationMs ?: 0L,
            aspect = InlineVideoBox.displayAspect(concert.width ?: 0, concert.height ?: 0),
            resumedAtMs = 754_000L,
        )
    }
}
