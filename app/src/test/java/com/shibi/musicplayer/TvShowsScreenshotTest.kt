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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.shibi.musicplayer.domain.Classified
import com.shibi.musicplayer.domain.TvFile
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.domain.VideoCounts
import com.shibi.musicplayer.domain.VideoFilter
import com.shibi.musicplayer.domain.VideoIdentity
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.domain.continueEpisode
import com.shibi.musicplayer.domain.shows
import com.shibi.musicplayer.library.VideoLibraryUiState
import com.shibi.musicplayer.ui.ChromeSlot
import com.shibi.musicplayer.ui.ListRow
import com.shibi.musicplayer.ui.ListViewport
import com.shibi.musicplayer.ui.MiniPlayerCarouselGap
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.NowPlayingBar
import com.shibi.musicplayer.ui.PlayerScreen
import com.shibi.musicplayer.ui.SectionRoutes
import com.shibi.musicplayer.ui.SectionScaffold
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.TvShowRow
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import com.shibi.musicplayer.domain.RoutePresentation
import java.text.Collator
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The TV chip's show list (with a Continue line) and a show page, compact and two-pane, the page
 * with the inline player open, the show row at 2x font, and the Continue line on the sky and
 * blush backgrounds over the normal and the playing row fill. A fake state with `isReady = true`.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TvShowsScreenshotTest {

    @Test
    fun showListCompact() = compact(listState, "tv_shows_compact.png")

    @Test
    fun showPageCompact() = compact(pageState, "tv_show_page_compact.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun showListTwoPane() = twoPane(listState, "tv_shows_two_pane.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun showPageTwoPane() = twoPane(pageState, "tv_show_page_two_pane.png")

    /** A video is open inline: the back arrow and the show's name sit right above the episodes. */
    @Test
    fun showPageCompactPlayerOpen() = compact(pageState, "tv_show_page_compact_player.png", playerOpen = true)

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
    fun showListCompactBigFont() = compact(listState, "tv_shows_compact_font2x.png")

    @Test
    fun accentLineOnSky() = accentRows(AppBackground.Solid("sky"), "tv_accent_line_sky.png")

    @Test
    fun accentLineOnBlush() = accentRows(AppBackground.Solid("blush"), "tv_accent_line_blush.png")

    private fun compact(state: VideoLibraryUiState, fileName: String, playerOpen: Boolean = false) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Default, emptyMap(), forceStatic = true) {
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

    /** The show row as the list draws it, then the same Continue line on the playing row fill. */
    @OptIn(ExperimentalCoilApi::class)
    private fun accentRows(background: AppBackground, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    CompositionLocalProvider(
                        LocalInspectionMode provides true,
                        LocalAsyncImagePreviewHandler provides thumbnails,
                    ) {
                        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                            val severance = allShows.first { it.name == "Severance" }
                            TvShowRow(
                                show = severance,
                                next = continueByShow[severance.key],
                                saved = progress[files[1].video.contentUri],
                                onClick = {},
                            )
                            ListRow(
                                title = severance.name,
                                detail = "2 seasons · 5 episodes",
                                fill = LocalBackgroundChrome.current.playingRowFill,
                                isPlaying = true,
                                onClick = {},
                                onLongClick = null,
                                art = { Box(Modifier.size(40.dp).background(Color.Gray)) },
                                accentLine = "Continue S01E02 · 34 min left",
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
                savedProgress = progress,
                // The second episode is open, so its row shows the playing fill.
                playingVideoId = if (state.openShowKey != null) 12L else null,
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
        fun episode(id: Long, show: String, season: Int, number: Int, title: String?, folder: String): TvFile {
            val file = video(id, "$show S0${season}E0$number", 3_000_000L, 900_000_000L, 1920, 1080, folder)
            return checkNotNull(TvFile.of(file, Classified(VideoIdentity.Episode(show, season, number, null, title))))
        }

        val files = listOf(
            episode(11L, "Severance", 1, 1, "Good News About Hell", "Severance"),
            episode(12L, "Severance", 1, 2, "Half Loop", "Severance"),
            episode(13L, "Severance", 1, 3, null, "Severance"),
            episode(14L, "Severance", 2, 1, "Hello, Ms. Cobel", "Severance"),
            episode(15L, "Severance", 2, 2, null, "Severance"),
            episode(21L, "The Office", 1, 1, "Pilot", "The Office"),
            episode(22L, "The Office", 1, 2, "Diversity Day", "The Office"),
            episode(31L, "Andor", 1, 1, null, "Andor"),
        )
        val allShows = shows(files, Collator.getInstance())

        /** Severance stopped 16 min 40 s into S01E02 (50 min): 34 min left. */
        val progress = mapOf(
            files[1].video.contentUri to VideoProgress(1_000_000L, 3_000_000L, updatedAtMs = 10L),
        )
        val continueByShow = allShows.mapNotNull { show -> continueEpisode(show, progress)?.let { show.key to it } }.toMap()

        val listState = VideoLibraryUiState(
            access = VideoAccess.Full,
            videos = allShows.flatMap { show -> show.episodes.map { it.video } },
            filter = VideoFilter.Tv,
            isReady = true,
            counts = VideoCounts(all = 20, movies = 4, tv = files.size, personal = 8),
            shows = allShows,
            continueByShow = continueByShow,
        )
        val pageState = listState.copy(openShowKey = allShows.first { it.name == "Severance" }.key)
    }
}
