package com.shibi.musicplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.playingState
import com.shibi.musicplayer.VideosScreenshotTest.Companion.thumbnails
import com.shibi.musicplayer.VideosScreenshotTest.Companion.video
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.LibrarySorter
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.MovieSortDefault
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.domain.VideoCounts
import com.shibi.musicplayer.domain.VideoFilter
import com.shibi.musicplayer.domain.VideoIdentity
import com.shibi.musicplayer.domain.VideoIdentity.CameraSource
import com.shibi.musicplayer.library.VideoLibraryUiState
import com.shibi.musicplayer.ui.ChromeSlot
import com.shibi.musicplayer.ui.ListViewport
import com.shibi.musicplayer.ui.MiniPlayerCarouselGap
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.NowPlayingBar
import com.shibi.musicplayer.ui.PlayerScreen
import com.shibi.musicplayer.ui.SectionRoutes
import com.shibi.musicplayer.ui.SectionScaffold
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import java.text.Collator
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Videos filter chips over the All, Movies and Personal lists, compact and two-pane (the
 * TV chip's screens are in TvShowsScreenshotTest), plus a 2x font size and the compact layout with the inline player open. The state is a fake
 * with `isReady = true` and real counts; no MediaStore.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class VideoFilterScreenshotTest {

    @Test
    fun allCompact() = compact(allState, "vf_all_compact.png")

    @Test
    fun moviesCompact() = compact(moviesState, "vf_movies_compact.png")

    @Test
    fun personalCompact() = compact(personalState, "vf_personal_compact.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun allTwoPane() = twoPane(allState, "vf_all_two_pane.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun moviesTwoPane() = twoPane(moviesState, "vf_movies_two_pane.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun personalTwoPane() = twoPane(personalState, "vf_personal_two_pane.png")

    /** The chip row at twice the font size: it scrolls sideways and the chips grow, not clip. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
    fun allCompactBigFont() = compact(allState, "vf_all_compact_font2x.png")

    /** A video is open inline: the chips sit directly above the list, under the player. */
    @Test
    fun moviesCompactPlayerOpen() = compact(moviesState, "vf_movies_compact_player.png", playerOpen = true)

    @Test
    fun moviesCompactSky() = compact(moviesState, "vf_movies_compact_sky.png", AppBackground.Solid("sky"))

    private fun compact(
        state: VideoLibraryUiState,
        fileName: String,
        background: AppBackground = AppBackground.Default,
        playerOpen: Boolean = false,
    ) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    SectionScaffold(
                        modifier = Modifier.fillMaxSize(),
                        showMiniPlayer = true,
                        showCarousel = true,
                        contentBottomPadding = SongListBottomPadding,
                        miniPlayer = { MiniBar() },
                        carousel = {
                            NavCarousel(
                                selected = NavSection.Videos,
                                onSelect = {},
                                sections = SectionRoutes.sectionsFor(expanded = false),
                            )
                        },
                    ) { bottom ->
                        Videos(state, bottom, playerOpen)
                    }
                }
            }
        }
    }

    private fun twoPane(state: VideoLibraryUiState, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Default, emptyMap(), forceStatic = true) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                            ListViewport(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                                Videos(state, SongListBottomPadding, playerOpen = false)
                            }
                            Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
                                PlayerScreen(
                                    state = playingState,
                                    onBack = {},
                                    onTogglePlayPause = {},
                                    onNext = {},
                                    onPrevious = {},
                                    onSeek = {},
                                    selectedOutputId = null,
                                    onSelectOutput = {},
                                    showBack = false,
                                    includeNavigationBars = false,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = SongListBottomPadding)
                                        .clip(RoundedCornerShape(28.dp)),
                                )
                            }
                        }
                        ChromeSlot {
                            MiniBar()
                            NavCarousel(
                                selected = NavSection.Videos,
                                onSelect = {},
                                sections = SectionRoutes.sectionsFor(expanded = true),
                            )
                        }
                    }
                }
            }
        }
    }

    /** Inspection mode only around the list, so the preview handler feeds the thumbnails. */
    @OptIn(ExperimentalCoilApi::class)
    @Composable
    private fun Videos(state: VideoLibraryUiState, bottom: Dp, playerOpen: Boolean) {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides thumbnails,
        ) {
            VideosContent(
                state = state,
                onVideoClick = {},
                onRequestAccess = {},
                onOpenSettings = {},
                contentBottomPadding = bottom,
                modifier = Modifier.fillMaxSize(),
                // A stand-in box for the inline player: the layout around it is what is under test.
                player = if (playerOpen) {
                    { Box(Modifier.fillMaxWidth().height(200.dp).background(Color.Black)) }
                } else {
                    null
                },
            )
        }
    }

    @Composable
    private fun MiniBar() {
        NowPlayingBar(
            state = playingState,
            onOpen = {},
            onTogglePlayPause = {},
            route = RoutePresentation.phone("This phone"),
            padForNavigationBars = false,
            bottomPadding = MiniPlayerCarouselGap,
        )
    }

    private companion object {
        val movieFiles = listOf(
            Triple("The Grand Budapest Hotel", 2014, 5_960_000L),
            Triple("Inception", 2010, 8_880_000L),
            Triple("Arrival", 2016, 6_960_000L),
            Triple("Heat", null, 10_200_000L),
        ).mapIndexed { index, (title, _, duration) ->
            video(1L + index, title, duration, 1_400_000_000L + index * 90_000_000L, 1920, 1080, "Movies")
        }
        val movieYears = listOf(2014, 2010, 2016, null)

        val episodeFiles = (1..3).map { number ->
            video(10L + number, "Severance S01E0$number", 3_000_000L, 900_000_000L, 1920, 1080, "Severance")
        }

        val clipFiles = listOf(
            video(21L, "DJI_0001", 312_000L, 1_020_000_000L, 3840, 2160, "Camera") to CameraSource.DJI,
            video(22L, "GX010001", 186_000L, 756_000_000L, 2704, 1520, "Camera") to CameraSource.GOPRO,
            video(23L, "PXL_20250101_120000", 42_000L, 98_000_000L, 1920, 1080, "Camera") to CameraSource.PIXEL,
            video(24L, "Screen_Recording_2025", 61_000L, 18_400_000L, 1080, 2340, "Screen recordings") to
                CameraSource.SCREEN_RECORDING,
            video(25L, "NPA reel", 744_000L, 410_000_000L, 1920, 1080, "OH edited videos") to null,
        )

        val identities: Map<Long, VideoIdentity> =
            movieFiles.mapIndexed { index, file ->
                file.id to VideoIdentity.Movie(file.title, movieYears[index])
            }.toMap() +
                episodeFiles.associate { it.id to VideoIdentity.Episode("Severance", 1, (it.id - 10).toInt(), null, null) } +
                clipFiles.associate { (file, source) ->
                    file.id to (source?.let { VideoIdentity.Personal(it) } ?: VideoIdentity.Other(file.title))
                }

        val counts = VideoCounts(all = 12, movies = 4, tv = 3, personal = 5)

        fun state(filter: VideoFilter, videos: List<Video>, sort: SortSpec, sorted: Boolean = true): VideoLibraryUiState {
            val result = LibrarySorter.videos(videos, sort, Collator.getInstance(), ZoneId.systemDefault(), "Unknown title")
            return VideoLibraryUiState(
                access = VideoAccess.Full,
                videos = if (sorted) result.items else videos,
                sort = result.spec,
                runs = if (sorted) result.runs else emptyList(),
                filter = filter,
                isReady = true,
                counts = counts,
                identities = identities,
            )
        }

        val allState = state(
            VideoFilter.All,
            movieFiles + episodeFiles + clipFiles.map { it.first },
            LibrarySorts.VideoDefault,
        )
        val moviesState = state(VideoFilter.Movies, movieFiles, MovieSortDefault)
        val personalState = state(VideoFilter.Personal, clipFiles.map { it.first }, LibrarySorts.VideoDefault)
    }
}
