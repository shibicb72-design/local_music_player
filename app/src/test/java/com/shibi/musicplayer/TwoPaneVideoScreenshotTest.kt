package com.shibi.musicplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.library.VideoLibraryUiState
import com.shibi.musicplayer.ui.ChromeSlot
import com.shibi.musicplayer.ui.ListViewport
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.SectionRoutes
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.TwoPaneCarouselPadding
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
 * V5: unfolded, a video plays in the right pane in Now Playing's place. The left pane keeps
 * the Videos list with the playing row marked; no mini player while the video is active, so
 * the carousel sits under the list column. The picture is [FakeVideoEngine]'s frame.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w900dp-h600dp-xhdpi")
class TwoPaneVideoScreenshotTest {

    @Test
    fun twoPaneVideoDark() = capture(AppBackground.Default, playing(concert), "cv5_two_pane_video_dark.png")

    @Test
    fun twoPaneVideoSky() = capture(AppBackground.Solid("sky"), playing(concert), "cv5_two_pane_video_sky.png")

    /** Paused, so the controls hold: white on the 55% scrim over the picture, the pane stays black. */
    @Test
    fun twoPaneVideoControls() = capture(
        AppBackground.Solid("blush"),
        playing(concert).copy(status = VideoStatus.Paused),
        "cv5_two_pane_video_controls.png",
        controls = true,
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
                    // MusicApp's two-pane tree with the mini player hidden.
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                            Column(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                                ListViewport(modifier = Modifier.weight(1f).fillMaxSize()) {
                                    Videos(video)
                                }
                                ChromeSlot {
                                    NavCarousel(
                                        selected = NavSection.Videos,
                                        onSelect = {},
                                        sections = SectionRoutes.sectionsFor(expanded = true),
                                        modifier = Modifier.padding(
                                            top = TwoPaneCarouselPadding,
                                            bottom = TwoPaneCarouselPadding,
                                        ),
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(0.58f)
                                    .fillMaxHeight()
                                    .navigationBarsPadding(),
                            ) {
                                VideoPane(
                                    state = video,
                                    onTogglePlayPause = {},
                                    onSeek = {},
                                    onClose = {},
                                    surface = { VideoSurface(engine) },
                                    initialControlsVisible = controls,
                                    // V7: the pane offers the tracks sheet, as in the app.
                                    onOpenTracks = {},
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = TwoPanePlayerBottomPadding)
                                        .clip(RoundedCornerShape(28.dp)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** Inspection mode only around the list, so the preview handler feeds the thumbnails. */
    @OptIn(ExperimentalCoilApi::class)
    @Composable
    private fun Videos(video: VideoUiState) {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
        ) {
            VideosContent(
                state = list,
                onVideoClick = {},
                onRequestAccess = {},
                onOpenSettings = {},
                contentBottomPadding = SongListBottomPadding,
                playingVideoId = video.video?.id,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private companion object {
        val list = VideoLibraryUiState(access = VideoAccess.Full, videos = VideosScreenshotTest.videos)
        val concert: Video = VideosScreenshotTest.videos.first { it.id == 2L }

        fun playing(video: Video) = VideoUiState(
            video = video,
            status = VideoStatus.Playing,
            positionMs = 192_000L,
            durationMs = video.durationMs ?: 0L,
            aspect = InlineVideoBox.displayAspect(video.width ?: 0, video.height ?: 0),
        )
    }
}
