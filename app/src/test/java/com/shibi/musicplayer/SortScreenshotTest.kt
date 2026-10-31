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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.playingState
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.LibrarySorter
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.ScrollSection
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.SortedList
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.UnknownNames
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.domain.VideoTimeline
import com.shibi.musicplayer.domain.entryIndexForFraction
import com.shibi.musicplayer.domain.stopFractions
import com.shibi.musicplayer.library.VideoLibraryUiState
import com.shibi.musicplayer.ui.ChromeSlot
import com.shibi.musicplayer.ui.LibraryContent
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The library sort, folded and unfolded: the sort menu open on Videos and on Songs (a popup, so
 * the whole screen is captured), over the dark default and over blush, and the fast scroller
 * mid-drag by name and by size, with the letter or range in the bubble and a dot per group.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SortScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun videosMenuFolded() = menu("sort_videos_menu_folded.png", music = false, expanded = false)

    @Test
    @Config(qualifiers = Unfolded)
    fun videosMenuUnfolded() = menu("sort_videos_menu_unfolded.png", music = false, expanded = true)

    @Test
    fun songsMenuFolded() = menu("sort_songs_menu_folded.png", music = true, expanded = false)

    @Test
    @Config(qualifiers = Unfolded)
    fun songsMenuUnfolded() = menu("sort_songs_menu_unfolded.png", music = true, expanded = true)

    // The menu's fill is the background's sheet colour, so it is shot on a pastel too.

    @Test
    fun videosMenuBlushFolded() = menu("sort_videos_menu_blush_folded.png", music = false, expanded = false, Blush)

    @Test
    @Config(qualifiers = Unfolded)
    fun videosMenuBlushUnfolded() = menu("sort_videos_menu_blush_unfolded.png", music = false, expanded = true, Blush)

    @Test
    fun songsMenuBlushFolded() = menu("sort_songs_menu_blush_folded.png", music = true, expanded = false, Blush)

    @Test
    @Config(qualifiers = Unfolded)
    fun songsMenuBlushUnfolded() = menu("sort_songs_menu_blush_unfolded.png", music = true, expanded = true, Blush)

    @Test
    fun videosNameDragFolded() = drag("sort_videos_name_drag_folded.png", music = false, ByName, expanded = false)

    @Test
    @Config(qualifiers = Unfolded)
    fun videosNameDragUnfolded() = drag("sort_videos_name_drag_unfolded.png", music = false, ByName, expanded = true)

    @Test
    fun videosSizeDragFolded() = drag("sort_videos_size_drag_folded.png", music = false, BySize, expanded = false)

    @Test
    @Config(qualifiers = Unfolded)
    fun videosSizeDragUnfolded() = drag("sort_videos_size_drag_unfolded.png", music = false, BySize, expanded = true)

    @Test
    fun songsNameDragFolded() = drag("sort_songs_name_drag_folded.png", music = true, ByName, expanded = false)

    @Test
    @Config(qualifiers = Unfolded)
    fun songsNameDragUnfolded() = drag("sort_songs_name_drag_unfolded.png", music = true, ByName, expanded = true)

    @Test
    fun songsSizeDragFolded() = drag("sort_songs_size_drag_folded.png", music = true, BySize, expanded = false)

    @Test
    @Config(qualifiers = Unfolded)
    fun songsSizeDragUnfolded() = drag("sort_songs_size_drag_unfolded.png", music = true, BySize, expanded = true)

    private fun menu(
        fileName: String,
        music: Boolean,
        expanded: Boolean,
        background: AppBackground = AppBackground.Default,
    ) = shoot(fileName) {
        Screen(music, expanded, if (music) ByArtist else ByName, menuOpen = true, dragFraction = null, background)
    }

    private fun drag(fileName: String, music: Boolean, sort: SortSpec, expanded: Boolean) = shoot(fileName) {
        Screen(music, expanded, sort, menuOpen = false, dragFraction = dragFraction(music, sort))
    }

    /** The whole screen, so the menu's popup is in the picture too. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun shoot(fileName: String, content: @Composable () -> Unit) {
        compose.setContent(content)
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/$fileName")
    }

    @Composable
    private fun Screen(
        music: Boolean,
        expanded: Boolean,
        sort: SortSpec,
        menuOpen: Boolean,
        dragFraction: Float?,
        background: AppBackground = AppBackground.Default,
    ) {
        MusicPlayerTheme {
            AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                val list: @Composable (Dp) -> Unit = { bottom ->
                    if (music) {
                        Songs(bottom, sort, menuOpen, dragFraction)
                    } else {
                        Videos(bottom, sort, menuOpen, dragFraction)
                    }
                }
                val section = if (music) NavSection.Songs else NavSection.Videos
                if (expanded) {
                    TwoPane(section, list)
                } else {
                    SectionScaffold(
                        modifier = Modifier.fillMaxSize(),
                        showMiniPlayer = true,
                        showCarousel = true,
                        contentBottomPadding = SongListBottomPadding,
                        miniPlayer = { MiniBar() },
                        carousel = {
                            NavCarousel(
                                selected = section,
                                onSelect = {},
                                sections = SectionRoutes.sectionsFor(expanded = false),
                            )
                        },
                    ) { bottom -> list(bottom) }
                }
            }
        }
    }

    @Composable
    private fun TwoPane(section: NavSection, list: @Composable (Dp) -> Unit) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                ListViewport(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                    list(SongListBottomPadding)
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
                NavCarousel(selected = section, onSelect = {}, sections = SectionRoutes.sectionsFor(expanded = true))
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    @Composable
    private fun Videos(bottom: Dp, sort: SortSpec, menuOpen: Boolean, dragFraction: Float?) {
        val sorted = sortedVideos(sort)
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
        ) {
            VideosContent(
                state = VideoLibraryUiState(
                    access = VideoAccess.Full,
                    videos = sorted.items,
                    sort = sort,
                    runs = sorted.runs,
                ),
                onVideoClick = {},
                onRequestAccess = {},
                onOpenSettings = {},
                contentBottomPadding = bottom,
                modifier = Modifier.fillMaxSize(),
                fastScrollDragFraction = dragFraction,
                initialSortMenuOpen = menuOpen,
                // Where a real drag to this fraction leaves the list: the entry under the thumb.
                listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = dragFraction?.let {
                        entryIndexForFraction(it, VideoTimeline.layout(sorted).entries.size)
                    } ?: 0,
                ),
            )
        }
    }

    @Composable
    private fun Songs(bottom: Dp, sort: SortSpec, menuOpen: Boolean, dragFraction: Float?) {
        val sorted = sortedSongs(sort)
        LibraryContent(
            tracks = sorted.items,
            sorted = sorted,
            nowPlaying = playingState,
            showMiniPlayer = false,
            onTrackSelected = { _, _ -> },
            onOpenPlayer = {},
            onTogglePlayPause = {},
            favoriteIds = setOf(sorted.items[1].id),
            contentBottomPadding = bottom,
            initialSortMenuOpen = menuOpen,
            fastScrollDragFraction = dragFraction,
            listState = rememberLazyListState(
                initialFirstVisibleItemIndex = dragFraction?.let { entryIndexForFraction(it, sorted.items.size) } ?: 0,
            ),
            modifier = Modifier.fillMaxSize(),
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

    private companion object {
        const val Unfolded = "w900dp-h600dp-xhdpi"
        val Blush = AppBackground.Solid("blush")
        val ByName = SortSpec(SortField.Name, ascending = true)
        val ByArtist = SortSpec(SortField.Artist, ascending = true)
        val BySize = SortSpec(SortField.Size, ascending = false)
        val zone: ZoneId = ZoneId.systemDefault()

        val names = listOf(
            "Aurora", "Apollo", "Amber Sky", "Blue Hour", "Breaking Glass", "Cinder", "Coastline", "Daybreak",
            "Drift", "Éclair", "Echoes", "Ember", "Firefly", "Golden Hour", "Glass Harbor", "Hollow", "Indigo",
            "Juniper", "Kites", "Lantern", "Midnight Drive", "Moonrise", "Northern Lights", "Ocean Eyes",
            "Paper Planes", "Quiet Storm", "Rivers", "Saltwater", "Static Bloom", "Tidal", "Undertow",
            "Velvet Rain", "Wildflower", "Yesterday", "Zephyr", "1999", "21 Guns",
            "என்னை கொஞ்சம்",
        )
        val artists = listOf("Neon Cascade", "Ella Voss", "Marina Cole", "Northbound", "Rilo Ash", "Hollow Coast", "Unknown artist")

        /** Songs spread over every size and length band, a Tamil title, digits and an unknown artist. */
        val songs: List<Track> = names.mapIndexed { index, name ->
            Track(
                id = index + 1L,
                title = name,
                artist = artists[index % artists.size],
                album = "Album ${index % 5}",
                durationMs = listOf(95_000L, 180_000L, 290_000L, 480_000L, 730_000L)[index % 5],
                contentUri = "content://tracks/${index + 1}",
                albumArtUri = null,
                dateAddedSeconds = 1_700_000_000L + index * 86_400L,
                sizeBytes = listOf(3_200_000L, 7_400_000L, 12_800_000L, 26_000_000L, 64_000_000L)[(index * 3) % 5],
            )
        }

        /** Videos over every size band, with varied names. */
        val videos: List<Video> = names.mapIndexed { index, name ->
            VideosScreenshotTest.video(
                id = 300L + index,
                title = name,
                durationMs = listOf(40_000L, 180_000L, 600_000L, 2_400_000L, 5_400_000L, 9_000_000L)[index % 6],
                sizeBytes = listOf(6_000_000L, 45_000_000L, 420_000_000L, 2_100_000_000L, 6_300_000_000L)[(index * 2) % 5],
                width = 1920,
                height = 1080,
                folder = if (index % 2 == 0) "Camera" else "Movies",
            )
        }

        fun sortedSongs(sort: SortSpec): SortedList<Track> = LibrarySorter.tracks(
            songs,
            sort,
            Collator.getInstance(),
            zone,
            UnknownNames(title = "Unknown title", artist = "Unknown artist", album = "Unknown album"),
        )

        fun sortedVideos(sort: SortSpec): SortedList<Video> =
            LibrarySorter.videos(videos, sort, Collator.getInstance(), zone, "Unknown title")

        /** Just past the third group's dot, so the thumb holds it and the bubble names it. */
        fun dragFraction(music: Boolean, sort: SortSpec): Float {
            val stops = if (music) {
                val sorted = sortedSongs(sort)
                stopFractions(sorted.runs.map { ScrollSection(it.start, it.count) }, sorted.items.size)
            } else {
                val layout = VideoTimeline.layout(sortedVideos(sort))
                stopFractions(layout.sections.map { ScrollSection(it.startIndex, it.count) }, layout.entries.size)
            }
            return stops[2] + 0.005f
        }
    }
}
