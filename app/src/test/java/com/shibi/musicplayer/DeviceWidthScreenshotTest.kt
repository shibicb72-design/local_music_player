package com.shibi.musicplayer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.playingState
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.HistoryType
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.ui.FavoritesScreen
import com.shibi.musicplayer.ui.HistoryRow
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.MiniPlayerCarouselGap
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.NowPlayingBar
import com.shibi.musicplayer.ui.RecentHistoryScreen
import com.shibi.musicplayer.ui.SearchContent
import com.shibi.musicplayer.ui.SectionRoutes
import com.shibi.musicplayer.ui.SectionScaffold
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Songs and Videos lists at six phone sizes ([DeviceProfiles]), for the width bug seen on a
 * Galaxy S24 Ultra: Songs rows stopped short of the right edge while Videos rows filled it.
 * Favorites, Recent and Search are drawn at the same sizes, for their row margins. These are
 * Robolectric renders at the profile's width, height and density, not device or emulator
 * captures. Every list sits on the blush backdrop, where every row draws its translucent fill,
 * so a row's width shows. The compact chrome is the app's: the mini player and the 9-tile
 * carousel. Only shipped APIs are used, so the same file renders an older build.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DeviceWidthScreenshotTest {

    @Test
    @Config(qualifiers = S24_ULTRA)
    fun s24UltraSongs() = songs("s24ultra")

    @Test
    @Config(qualifiers = S24_ULTRA)
    fun s24UltraVideos() = videos("s24ultra")

    @Test
    @Config(qualifiers = S24_ULTRA)
    fun s24UltraFavorites() = favorites("s24ultra")

    @Test
    @Config(qualifiers = S24_ULTRA)
    fun s24UltraRecent() = recent("s24ultra")

    @Test
    @Config(qualifiers = S24_ULTRA)
    fun s24UltraSearch() = search("s24ultra")

    @Test
    @Config(qualifiers = S24_ULTRA_SMALL)
    fun s24UltraSmallSongs() = songs("s24ultra_small")

    @Test
    @Config(qualifiers = S24_ULTRA_SMALL)
    fun s24UltraSmallVideos() = videos("s24ultra_small")

    @Test
    @Config(qualifiers = S24_ULTRA_SMALL)
    fun s24UltraSmallFavorites() = favorites("s24ultra_small")

    @Test
    @Config(qualifiers = S24_ULTRA_SMALL)
    fun s24UltraSmallRecent() = recent("s24ultra_small")

    @Test
    @Config(qualifiers = S24_ULTRA_SMALL)
    fun s24UltraSmallSearch() = search("s24ultra_small")

    @Test
    @Config(qualifiers = S25)
    fun s25Songs() = songs("s25")

    @Test
    @Config(qualifiers = S25)
    fun s25Videos() = videos("s25")

    @Test
    @Config(qualifiers = S25)
    fun s25Favorites() = favorites("s25")

    @Test
    @Config(qualifiers = S25)
    fun s25Recent() = recent("s25")

    @Test
    @Config(qualifiers = S25)
    fun s25Search() = search("s25")

    @Test
    @Config(qualifiers = S25_ULTRA)
    fun s25UltraSongs() = songs("s25ultra")

    @Test
    @Config(qualifiers = S25_ULTRA)
    fun s25UltraVideos() = videos("s25ultra")

    @Test
    @Config(qualifiers = S25_ULTRA)
    fun s25UltraFavorites() = favorites("s25ultra")

    @Test
    @Config(qualifiers = S25_ULTRA)
    fun s25UltraRecent() = recent("s25ultra")

    @Test
    @Config(qualifiers = S25_ULTRA)
    fun s25UltraSearch() = search("s25ultra")

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSongs() = songs("narrow")

    @Test
    @Config(qualifiers = NARROW)
    fun narrowVideos() = videos("narrow")

    @Test
    @Config(qualifiers = NARROW)
    fun narrowFavorites() = favorites("narrow")

    @Test
    @Config(qualifiers = NARROW)
    fun narrowRecent() = recent("narrow")

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSearch() = search("narrow")

    @Test
    @Config(qualifiers = WIDE412)
    fun wide412Songs() = songs("wide412")

    @Test
    @Config(qualifiers = WIDE412)
    fun wide412Videos() = videos("wide412")

    @Test
    @Config(qualifiers = WIDE412)
    fun wide412Favorites() = favorites("wide412")

    @Test
    @Config(qualifiers = WIDE412)
    fun wide412Recent() = recent("wide412")

    @Test
    @Config(qualifiers = WIDE412)
    fun wide412Search() = search("wide412")

    /** The Songs tab as shipped: sorted, so with its fast scroller; one song playing, one liked. */
    private fun songs(profile: String) = capture("devices_${profile}_songs.png", NavSection.Songs) { bottom ->
        val shipped = remember { shippedSongs(tracks) }
        LibraryContent(
            tracks = shipped.items,
            sorted = shipped,
            nowPlaying = playingState,
            showMiniPlayer = false,
            onTrackSelected = { _, _ -> },
            onOpenPlayer = {},
            onTogglePlayPause = {},
            favoriteIds = setOf(5L),
            contentBottomPadding = bottom,
            modifier = Modifier.fillMaxSize(),
        )
    }

    /** Inspection mode only around the list, so the preview handler feeds the thumbnails. */
    @OptIn(ExperimentalCoilApi::class)
    private fun videos(profile: String) = capture("devices_${profile}_videos.png", NavSection.Videos) { bottom ->
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
            )
        }
    }

    /** Favorites in the library's order, the playing song first. */
    private fun favorites(profile: String) = capture("devices_${profile}_favorites.png", NavSection.Favorites) { bottom ->
        FavoritesScreen(
            tracks = tracks,
            favoriteIds = likedIds,
            nowPlaying = playingState,
            onTrackSelected = { _, _ -> },
            onToggleFavorite = {},
            onAddToPlaylist = {},
            contentBottomPadding = bottom,
            modifier = Modifier.fillMaxSize(),
        )
    }

    /** "o" finds eight songs by title or artist, in the library's order, the playing song second. */
    private fun search(profile: String) = capture("devices_${profile}_search.png", NavSection.Search) { bottom ->
        SearchContent(
            tracks = tracks,
            query = "o",
            onQueryChange = {},
            favoriteIds = likedIds,
            nowPlaying = playingState,
            onTrackSelected = { _, _ -> },
            onToggleFavorite = {},
            onAddToPlaylist = {},
            contentBottomPadding = bottom,
            modifier = Modifier.fillMaxSize(),
        )
    }

    /**
     * Songs and videos played over three days, at a fixed now. The rows' times read as clock
     * times, so the zone is pinned; inspection mode lets the preview handler feed the frames.
     */
    @OptIn(ExperimentalCoilApi::class)
    private fun recent(profile: String) {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(Zone))
        try {
            capture("devices_${profile}_recent.png", NavSection.Recent) { bottom ->
                CompositionLocalProvider(
                    LocalInspectionMode provides true,
                    LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
                ) {
                    RecentHistoryScreen(
                        rows = history,
                        onPlay = {},
                        onClearHistory = {},
                        contentBottomPadding = bottom,
                        modifier = Modifier.fillMaxSize(),
                        nowMs = Now,
                        zone = Zone,
                    )
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun capture(fileName: String, section: NavSection, list: @Composable (bottom: Dp) -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Solid("blush"), emptyMap(), forceStatic = true) {
                    SectionScaffold(
                        modifier = Modifier.fillMaxSize(),
                        showMiniPlayer = true,
                        showCarousel = true,
                        contentBottomPadding = SongListBottomPadding,
                        miniPlayer = {
                            NowPlayingBar(
                                state = playingState,
                                onOpen = {},
                                onTogglePlayPause = {},
                                route = RoutePresentation.phone("This phone"),
                                padForNavigationBars = false,
                                bottomPadding = MiniPlayerCarouselGap,
                            )
                        },
                        carousel = {
                            NavCarousel(
                                selected = section,
                                onSelect = {},
                                sections = SectionRoutes.sectionsFor(expanded = false),
                            )
                        },
                    ) { bottom ->
                        list(bottom)
                    }
                }
            }
        }
    }

    private companion object {
        /** Paper Planes (id 2) is the playing song in [playingState]. */
        val tracks = listOf(
            Track(1L, "Afterglow", "Mira Sol", "Coastlines", 214_000L, "content://tracks/1", null),
            Track(2L, "Paper Planes", "Ella Voss", "Night Drive", 198_000L, "content://tracks/2", null),
            Track(3L, "Blue Hour", "The Lanterns", "Harbour Lights", 247_000L, "content://tracks/3", null),
            Track(4L, "City of Glass", "Northbound", "Signals", 189_000L, "content://tracks/4", null),
            Track(5L, "Daybreak", "Kai Rivers", "Tidal", 231_000L, "content://tracks/5", null),
            Track(6L, "Echo Park", "Luma", "Summer Tapes", 205_000L, "content://tracks/6", null),
            Track(7L, "Fireflies", "June Avenue", "Small Hours", 178_000L, "content://tracks/7", null),
            Track(8L, "Golden Line", "Mira Sol", "Coastlines", 262_000L, "content://tracks/8", null),
            Track(9L, "Highway Hum", "The Lanterns", "Harbour Lights", 223_000L, "content://tracks/9", null),
            Track(10L, "Into the Static", "Northbound", "Signals", 301_000L, "content://tracks/10", null),
            Track(11L, "Juniper", "Ella Voss", "Night Drive", 196_000L, "content://tracks/11", null),
            Track(12L, "Kites", "Kai Rivers", "Tidal", 184_000L, "content://tracks/12", null),
        )

        /** Favorites: Paper Planes (playing), Blue Hour, Daybreak, Golden Line, Highway Hum, Juniper. */
        val likedIds = setOf(2L, 3L, 5L, 8L, 9L, 11L)

        val Zone: ZoneId = ZoneId.of("America/New_York")

        /** Sat 2026-10-17 21:30 in New York: Today, Yesterday, then Wednesday. */
        val Now: Long = at(17, 21, 30)

        private fun at(day: Int, hour: Int, minute: Int): Long =
            ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, Zone).toInstant().toEpochMilli()

        private fun song(key: Long, track: Track, playedAt: Long) = HistoryRow(
            key = key,
            type = HistoryType.Song,
            mediaId = track.id,
            title = track.title,
            subtitle = track.artist,
            artUri = null,
            videoUri = null,
            playedAt = playedAt,
        )

        /** Even video ids load a frame ([VideosScreenshotTest.thumbnails]); odd ones show the film tile. */
        private fun video(key: Long, id: Long, title: String, playedAt: Long) = HistoryRow(
            key = key,
            type = HistoryType.Video,
            mediaId = id,
            title = title,
            subtitle = "",
            artUri = null,
            videoUri = "content://media/external/video/media/$id",
            playedAt = playedAt,
        )

        /** Newest first, as the history comes. */
        val history = listOf(
            song(1L, tracks[1], at(17, 21, 6)),
            video(2L, 2L, "Concert encore", at(17, 20, 31)),
            song(3L, tracks[0], at(17, 19, 44)),
            song(4L, tracks[2], at(16, 22, 15)),
            video(5L, 7L, "Drone over the lake", at(16, 18, 2)),
            song(6L, tracks[4], at(16, 9, 2)),
            song(7L, tracks[7], at(14, 17, 26)),
            video(8L, 6L, "Birthday", at(14, 12, 10)),
            song(9L, tracks[10], at(14, 8, 45)),
        )
    }
}
