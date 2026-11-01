package com.shibi.musicplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.shibi.musicplayer.domain.HistoryType
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.FavoritesContent
import com.shibi.musicplayer.ui.HistoryRow
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.RecentHistoryScreen
import com.shibi.musicplayer.ui.SearchContent
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The width bug from a Galaxy S24 Ultra: Songs rows stopped short of the right edge (they left
 * room for the fast-scroll thumb) while Videos rows filled the width. At each phone size
 * ([DeviceProfiles]) a Songs row and a Videos row, both lists as shipped (sorted, so with their
 * fast scrollers), must have the same bounds, with the same margin on the left and the right.
 * Favorites, Recent and Search rows must have the Songs row's bounds too.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RowWidthUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = S24_ULTRA)
    fun s24UltraSongsRowsMatchVideosRows() = assertRowsMatch()

    @Test
    @Config(qualifiers = S24_ULTRA)
    fun s24UltraFavoritesRowsMatchSongsRows() = assertMatchesSongs(Screen.Favorites)

    @Test
    @Config(qualifiers = S24_ULTRA)
    fun s24UltraRecentRowsMatchSongsRows() = assertMatchesSongs(Screen.Recent)

    @Test
    @Config(qualifiers = S24_ULTRA)
    fun s24UltraSearchRowsMatchSongsRows() = assertMatchesSongs(Screen.Search)

    @Test
    @Config(qualifiers = S24_ULTRA_SMALL)
    fun s24UltraSmallSongsRowsMatchVideosRows() = assertRowsMatch()

    @Test
    @Config(qualifiers = S24_ULTRA_SMALL)
    fun s24UltraSmallFavoritesRowsMatchSongsRows() = assertMatchesSongs(Screen.Favorites)

    @Test
    @Config(qualifiers = S24_ULTRA_SMALL)
    fun s24UltraSmallRecentRowsMatchSongsRows() = assertMatchesSongs(Screen.Recent)

    @Test
    @Config(qualifiers = S24_ULTRA_SMALL)
    fun s24UltraSmallSearchRowsMatchSongsRows() = assertMatchesSongs(Screen.Search)

    @Test
    @Config(qualifiers = S25)
    fun s25SongsRowsMatchVideosRows() = assertRowsMatch()

    @Test
    @Config(qualifiers = S25)
    fun s25FavoritesRowsMatchSongsRows() = assertMatchesSongs(Screen.Favorites)

    @Test
    @Config(qualifiers = S25)
    fun s25RecentRowsMatchSongsRows() = assertMatchesSongs(Screen.Recent)

    @Test
    @Config(qualifiers = S25)
    fun s25SearchRowsMatchSongsRows() = assertMatchesSongs(Screen.Search)

    @Test
    @Config(qualifiers = S25_ULTRA)
    fun s25UltraSongsRowsMatchVideosRows() = assertRowsMatch()

    @Test
    @Config(qualifiers = S25_ULTRA)
    fun s25UltraFavoritesRowsMatchSongsRows() = assertMatchesSongs(Screen.Favorites)

    @Test
    @Config(qualifiers = S25_ULTRA)
    fun s25UltraRecentRowsMatchSongsRows() = assertMatchesSongs(Screen.Recent)

    @Test
    @Config(qualifiers = S25_ULTRA)
    fun s25UltraSearchRowsMatchSongsRows() = assertMatchesSongs(Screen.Search)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSongsRowsMatchVideosRows() = assertRowsMatch()

    @Test
    @Config(qualifiers = NARROW)
    fun narrowFavoritesRowsMatchSongsRows() = assertMatchesSongs(Screen.Favorites)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowRecentRowsMatchSongsRows() = assertMatchesSongs(Screen.Recent)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSearchRowsMatchSongsRows() = assertMatchesSongs(Screen.Search)

    @Test
    @Config(qualifiers = WIDE412)
    fun wide412SongsRowsMatchVideosRows() = assertRowsMatch()

    @Test
    @Config(qualifiers = WIDE412)
    fun wide412FavoritesRowsMatchSongsRows() = assertMatchesSongs(Screen.Favorites)

    @Test
    @Config(qualifiers = WIDE412)
    fun wide412RecentRowsMatchSongsRows() = assertMatchesSongs(Screen.Recent)

    @Test
    @Config(qualifiers = WIDE412)
    fun wide412SearchRowsMatchSongsRows() = assertMatchesSongs(Screen.Search)

    /** Songs above Videos, each list full width; their first rows are compared. */
    @OptIn(ExperimentalCoilApi::class)
    private fun assertRowsMatch() {
        val shipped = shippedSongs(tracks)
        compose.setContent {
            MusicPlayerTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LibraryContent(
                            tracks = shipped.items,
                            sorted = shipped,
                            nowPlaying = PlayerUiState(),
                            showMiniPlayer = false,
                            onTrackSelected = { _, _ -> },
                            onOpenPlayer = {},
                            onTogglePlayPause = {},
                            contentBottomPadding = 0.dp,
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CompositionLocalProvider(
                            LocalInspectionMode provides true,
                            LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
                        ) {
                            VideosContent(
                                state = VideosScreenshotTest.full,
                                onVideoClick = {},
                                onRequestAccess = {},
                                onOpenSettings = {},
                                contentBottomPadding = 0.dp,
                            )
                        }
                    }
                }
            }
        }
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val song = rowOf("Alpha")
        val video = rowOf("Beach day")
        assertEquals("Songs and Videos rows start together", video.left, song.left, 0.5f)
        assertEquals("Songs and Videos rows end together", video.right, song.right, 0.5f)
        // Each side rounds 4 dp to whole pixels on its own, so allow a pixel between them.
        assertEquals("even margins (left ${song.left} px)", song.left, width - song.right, 1f)
        assertEquals("the rows' 4 dp fill inset", with(compose.density) { 4.dp.toPx() }, song.left, 1f)
    }

    /**
     * The Songs list as shipped, then [other] in the same window, as the app swaps one screen for
     * the next. Alpha leads each list; the [other] row must have the Songs row's bounds.
     */
    @OptIn(ExperimentalCoilApi::class)
    private fun assertMatchesSongs(other: Screen) {
        val shipped = shippedSongs(tracks)
        var screen by mutableStateOf(Screen.Songs)
        compose.setContent {
            MusicPlayerTheme {
                when (screen) {
                    Screen.Songs -> LibraryContent(
                        tracks = shipped.items,
                        sorted = shipped,
                        nowPlaying = PlayerUiState(),
                        showMiniPlayer = false,
                        onTrackSelected = { _, _ -> },
                        onOpenPlayer = {},
                        onTogglePlayPause = {},
                        contentBottomPadding = 0.dp,
                    )
                    Screen.Favorites -> FavoritesContent(
                        tracks = tracks,
                        favoriteIds = tracks.map { it.id }.toSet(),
                        nowPlaying = PlayerUiState(),
                        onTrackSelected = { _, _ -> },
                        onToggleFavorite = {},
                        onAddToPlaylist = {},
                        contentBottomPadding = 0.dp,
                    )
                    Screen.Recent -> CompositionLocalProvider(
                        LocalInspectionMode provides true,
                        LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
                    ) {
                        RecentHistoryScreen(
                            rows = history,
                            onPlay = {},
                            onClearHistory = {},
                            contentBottomPadding = 0.dp,
                            nowMs = now,
                            zone = zone,
                        )
                    }
                    // "a" finds all three songs, Alpha first.
                    Screen.Search -> SearchContent(
                        tracks = tracks,
                        query = "a",
                        onQueryChange = {},
                        favoriteIds = emptySet(),
                        nowPlaying = PlayerUiState(),
                        onTrackSelected = { _, _ -> },
                        onToggleFavorite = {},
                        onAddToPlaylist = {},
                        contentBottomPadding = 0.dp,
                    )
                }
            }
        }
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val song = rowOf("Alpha")
        screen = other
        compose.waitForIdle()
        val row = rowOf("Alpha")
        assertEquals("$other and Songs rows start together", song.left, row.left, 0.5f)
        assertEquals("$other and Songs rows end together", song.right, row.right, 0.5f)
        assertEquals("even margins (left ${row.left} px)", row.left, width - row.right, 1f)
        assertEquals("the rows' 4 dp fill inset", with(compose.density) { 4.dp.toPx() }, row.left, 1f)
    }

    /** The row's clickable bounds: the merged node that holds [title]. */
    private fun rowOf(title: String): Rect = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot

    private enum class Screen { Songs, Favorites, Recent, Search }

    private companion object {
        val tracks = listOf(
            Track(1L, "Alpha", "Artist One", "Album A", 100_000L, "content://tracks/1", null),
            Track(2L, "Bravo", "Artist Two", "Album B", 110_000L, "content://tracks/2", null),
            Track(3L, "Charlie", "Artist Three", "Album C", 120_000L, "content://tracks/3", null),
        )

        val zone: ZoneId = ZoneId.of("America/New_York")

        private fun at(day: Int, hour: Int, minute: Int): Long =
            ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

        val now = at(17, 21, 30)

        /** Alpha played last; a video and a song from yesterday below it. */
        val history = listOf(
            HistoryRow(1L, HistoryType.Song, 1L, "Alpha", "Artist One", null, null, at(17, 21, 6)),
            HistoryRow(
                key = 2L,
                type = HistoryType.Video,
                mediaId = 2L,
                title = "Concert encore",
                subtitle = "",
                artUri = null,
                videoUri = "content://media/external/video/media/2",
                playedAt = at(16, 19, 44),
            ),
            HistoryRow(3L, HistoryType.Song, 2L, "Bravo", "Artist Two", null, null, at(16, 9, 2)),
        )
    }
}
