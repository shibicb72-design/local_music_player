package com.shibi.musicplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.playingState
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.songs
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.tracks
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.Selection
import com.shibi.musicplayer.ui.ChromeSlot
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.ListViewport
import com.shibi.musicplayer.ui.MiniPlayerCarouselGap
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.NowPlayingBar
import com.shibi.musicplayer.ui.PlayerScreen
import com.shibi.musicplayer.ui.PlaylistDetailContent
import com.shibi.musicplayer.ui.PlaylistSongRow
import com.shibi.musicplayer.ui.SectionRoutes
import com.shibi.musicplayer.ui.SectionScaffold
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** V19 multi-select: two rows selected in the Songs list, the Videos list and a playlist. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SelectionScreenshotTest {

    @Test
    fun songsCompact() {
        captureRoboImage("build/outputs/roborazzi/cv19_songs_selection_compact.png") {
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
                                selected = NavSection.Songs,
                                onSelect = {},
                                sections = SectionRoutes.sectionsFor(expanded = false),
                            )
                        },
                    ) { bottom ->
                        Songs(bottom)
                    }
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun songsExpanded() {
        captureRoboImage("build/outputs/roborazzi/cv19_songs_selection_expanded.png") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Default, emptyMap(), forceStatic = true) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                            ListViewport(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                                Songs(SongListBottomPadding)
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
                                selected = NavSection.Songs,
                                onSelect = {},
                                sections = SectionRoutes.sectionsFor(expanded = true),
                            )
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun videosCompact() {
        captureRoboImage("build/outputs/roborazzi/cv19_videos_selection_compact.png") {
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
                        CompositionLocalProvider(
                            LocalInspectionMode provides true,
                            LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
                        ) {
                            VideosContent(
                                state = VideosScreenshotTest.full,
                                onVideoClick = {},
                                onRequestAccess = {},
                                onOpenSettings = {},
                                contentBottomPadding = bottom,
                                modifier = Modifier.fillMaxSize(),
                                initialSelection = Selection().start(2L).toggle(4L),
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun playlistCompact() {
        val rows = tracks.map { track ->
            PlaylistSongRow(track.id, track.title, track.artist, track.album, null, false)
        }
        captureRoboImage("build/outputs/roborazzi/cv19_playlist_selection_compact.png") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Default, emptyMap(), forceStatic = true) {
                    PlaylistDetailContent(
                        name = "Road trip",
                        songs = rows,
                        onBack = {},
                        onPlayAll = {},
                        onShuffle = {},
                        onRenameClick = {},
                        onDeleteClick = {},
                        onRemove = {},
                        onReorder = {},
                        onPlayFrom = {},
                        contentBottomPadding = SongListBottomPadding,
                        initialSelection = Selection().start(2L).toggle(3L),
                    )
                }
            }
        }
    }

    @Composable
    private fun Songs(bottom: Dp) {
        LibraryContent(
            tracks = songs.items,
            sorted = songs,
            nowPlaying = playingState,
            showMiniPlayer = false,
            onTrackSelected = { _, _ -> },
            onOpenPlayer = {},
            onTogglePlayPause = {},
            favoriteIds = setOf(3L),
            contentBottomPadding = bottom,
            listState = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            initialSelection = Selection().start(1L).toggle(3L),
        )
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
}
