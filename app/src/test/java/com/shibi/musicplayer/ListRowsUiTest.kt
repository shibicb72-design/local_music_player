package com.shibi.musicplayer

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.shibi.musicplayer.domain.HistoryType
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.library.VideoLibraryUiState
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.FavoritesContent
import com.shibi.musicplayer.ui.HistoryRow
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.ListThumbnailTag
import com.shibi.musicplayer.ui.PlaylistDetailContent
import com.shibi.musicplayer.ui.PlaylistSongRow
import com.shibi.musicplayer.ui.RecentHistoryScreen
import com.shibi.musicplayer.ui.SearchContent
import com.shibi.musicplayer.ui.SongFastScrollerTag
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Songs and Videos list rows: content 16 dp in from the row's edges, 60 dp rows with 40 dp
 * art, a ⋮ menu with a 48 dp touch target, and a Play all pill (a real Button, 48 dp tall) at
 * the top of each list. Favorites, Search and Recent share the rows, in their own order and
 * with no pill. The values are the spec's, written out rather than read from the code's
 * constants, so a change to either shows up here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ListRowsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val tracks = listOf(
        Track(1L, "Alpha", "Artist One", "Album A", 100_000L, "content://tracks/1", null),
        Track(2L, "Bravo", "Artist Two", "Album B", 110_000L, "content://tracks/2", null),
        Track(3L, "Charlie", "Artist Three", "Album C", 120_000L, "content://tracks/3", null),
        Track(4L, "Delta", "Artist Four", "Album D", 130_000L, "content://tracks/4", null),
    )
    private val shipped = shippedSongs(tracks)
    private val videoState = VideosScreenshotTest.full

    private val zone = ZoneId.of("America/New_York")
    private val now = at(17, 21, 30)

    /** Today's song and video, newest first, then yesterday's song; the video loads a frame. */
    private val history = listOf(
        HistoryRow(1L, HistoryType.Song, 1L, "Alpha", "Artist One", null, null, at(17, 21, 6)),
        HistoryRow(
            key = 2L,
            type = HistoryType.Video,
            mediaId = 902L,
            title = "Beach day",
            subtitle = "",
            artUri = null,
            videoUri = "content://media/external/video/media/902",
            playedAt = at(17, 19, 44),
        ),
        HistoryRow(3L, HistoryType.Song, 2L, "Bravo", "Artist Two", null, null, at(16, 9, 2)),
    )

    private fun at(day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    // --- Songs ---

    @Test
    fun songRowsAreSixtyDpWithFortyDpArtOnTheSideMargin() {
        songs()
        val row = rowOf("Alpha")
        assertDp("row height", 60.dp, row.height)
        val art = artOf(row)
        assertDp("art width", 40.dp, art.width)
        assertDp("art height", 40.dp, art.height)
        assertDp("art from the list's start", 16.dp, art.left - list().left)
        assertDp("art centred in the row", 10.dp, art.top - row.top)
    }

    @Test
    fun songMenuGlyphEndsSixteenDpFromTheRowsEnd() {
        // No sort, no fast scroller: the rows reach the list's end.
        songs(withSort = false)
        val list = list()
        val menus = menuButtons()
        assertEquals(tracks.size, menus.size)
        for (index in menus.indices) {
            menuButton(index).assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
            // The 24 dp glyph is centred in its touch target.
            assertDp("glyph to the row's end", 16.dp, list.right - (menus[index].center.x + px(12.dp)))
        }
    }

    @Test
    fun withTheScrollerTheSongMenuGlyphStillEndsSixteenDpFromTheListEnd() {
        // As shipped: sorted, so with the fast scroller; the rows still reach the list's end.
        songs()
        val listEnd = list().right
        val menus = menuButtons()
        assertEquals(tracks.size, menus.size)
        for (menu in menus) {
            assertDp("glyph to the list's end", 16.dp, listEnd - (menu.center.x + px(12.dp)))
        }
    }

    @Test
    fun songHeartTouchTargetsEndLeftOfTheScroller() {
        // The thumb only exists while it shows; the tests-only drag holds it on screen. It may
        // show over a ⋮ (rows keep the full width, as on Videos), never over a heart.
        songs(dragFraction = 0f)
        val thumb = compose.onNodeWithTag(SongFastScrollerTag).fetchSemanticsNode().boundsInRoot
        val hearts = compose.onAllNodesWithContentDescription("Favorite")
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
        assertEquals(tracks.size, hearts.size)
        for (heart in hearts) {
            val touchRight = heart.center.x + px(24.dp)
            assertTrue("touch target ends at $touchRight, thumb starts at ${thumb.left}", touchRight <= thumb.left)
        }
    }

    @Test
    fun songSecondLineIsTheArtistThenTheLength() {
        songs()
        // The dot between them is decoration: the row reads out "01:40" alone.
        compose.onNodeWithText("Alpha")
            .assert(hasText("Artist One"))
            .assert(hasText("01:40"))
    }

    @Test
    fun songPlayAllIsAFortyEightDpButtonThatPlaysTheListAsShown() {
        var played: Pair<List<Track>, Long>? = null
        songs(onTrackSelected = { queue, startId -> played = queue to startId })
        val pill = playAll()
        pill.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertIsEnabled()
        val bounds = pill.fetchSemanticsNode().boundsInRoot
        assertDp("pill height", 48.dp, bounds.height)
        assertDp("pill from the list's start", 16.dp, bounds.left - list().left)
        assertTrue("pill above the list", bounds.bottom <= list().top)
        pill.performClick()
        assertEquals(shipped.items to shipped.items.first().id, played)
    }

    @Test
    fun songPlayAllStartsFromTheFirstSongShownNotTheFirstInserted() {
        val zToA = SortSpec(SortField.Name, ascending = false)
        val shown = shippedSongs(tracks, zToA)
        assertEquals("fixture: Delta (id 4) heads Z-A", 4L, shown.items.first().id)
        var played: Pair<List<Track>, Long>? = null
        songs(sort = zToA, onTrackSelected = { queue, startId -> played = queue to startId })
        playAll().performClick()
        assertEquals(shown.items to 4L, played)
    }

    @Test
    fun anEmptySongsListHasNoPlayAll() {
        // An empty library never reaches LibraryContent in the app; the pill guards it anyway.
        songs(list = emptyList())
        playAll().assertDoesNotExist()
    }

    @Test
    fun songRowTapStillPlaysFromThatSong() {
        var played: Pair<List<Track>, Long>? = null
        songs(onTrackSelected = { queue, startId -> played = queue to startId })
        compose.onNodeWithText("Charlie").performClick()
        assertEquals(shipped.items to 3L, played)
    }

    @Test
    fun songMenuAddsToAPlaylistOrMovesTheSongToTrash() {
        val added = mutableListOf<Track>()
        var trashed: Pair<List<Track>, Boolean>? = null
        songs(
            onAddToPlaylist = { added += it },
            onTrashSongs = { picked, whole -> trashed = picked to whole },
        )
        openMenuOf("Bravo")
        compose.onNodeWithText("Add to playlist").performClick()
        assertEquals(listOf(tracks[1]), added)
        compose.onNodeWithText("Add to playlist").assertDoesNotExist()
        openMenuOf("Bravo")
        compose.onNodeWithText("Move to trash").performClick()
        assertEquals(listOf(tracks[1]) to false, trashed)
        assertEquals("trash does not add", 1, added.size)
    }

    @Test
    fun songHeartStillTogglesTheFavorite() {
        val toggled = mutableListOf<Track>()
        songs(onToggleFavorite = { toggled += it })
        compose.onAllNodesWithContentDescription("Favorite")[1].performClick()
        assertEquals(listOf(tracks[1]), toggled)
    }

    @Test
    fun whileSelectingSongsPlayAllIsOffAndRowsHaveNoMenu() {
        songs()
        compose.onAllNodesWithContentDescription("More").assertCountEquals(tracks.size)
        compose.onNodeWithText("Alpha").performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertExists()
        playAll().assertIsNotEnabled()
        compose.onAllNodesWithContentDescription("More").assertCountEquals(0)
        assertDp("row height while selecting", 60.dp, rowOf("Bravo").height)
        compose.onNodeWithContentDescription("Cancel selection").performClick()
        playAll().assertIsEnabled()
        compose.onAllNodesWithContentDescription("More").assertCountEquals(tracks.size)
    }

    @Test
    fun aOneSongListTrashesItsWholeList() {
        var trashed: Pair<List<Track>, Boolean>? = null
        songs(list = tracks.take(1), onTrashSongs = { picked, whole -> trashed = picked to whole })
        openMenuOf("Alpha")
        compose.onNodeWithText("Move to trash").performClick()
        assertEquals(listOf(tracks[0]) to true, trashed)
    }

    @Test
    fun withoutTheSystemTrashTheSongMenuOnlyAddsToAPlaylist() {
        // Below Android 11 there is no system trash (MediaTrash.isSupported()).
        songs(canTrash = false)
        openMenuOf("Alpha")
        compose.onNodeWithText("Add to playlist").assertExists()
        compose.onNodeWithText("Move to trash").assertDoesNotExist()
    }

    // --- Favorites, Search and Recent ---

    @Test
    fun favoriteRowsAreListRowsInTheirOwnOrder() {
        // Favorites keep the order they come in (the library's), not the Songs sort.
        val liked = listOf(tracks[2], tracks[0], tracks[3])
        var played: Pair<List<Track>, Long>? = null
        favorites(list = liked, onTrackSelected = { queue, startId -> played = queue to startId })
        assertListRows(listOf("Charlie", "Alpha", "Delta"))
        playAll().assertDoesNotExist()
        compose.onNodeWithText("Alpha").performClick()
        assertEquals(liked to 1L, played)
    }

    @Test
    fun favoriteMenuOnlyAddsToAPlaylistAndTheHeartStillToggles() {
        val added = mutableListOf<Track>()
        val toggled = mutableListOf<Track>()
        favorites(onAddToPlaylist = { added += it }, onToggleFavorite = { toggled += it })
        openMenuOf("Bravo")
        compose.onNodeWithText("Move to trash").assertDoesNotExist()
        compose.onNodeWithText("Add to playlist").performClick()
        assertEquals(listOf(tracks[1]), added)
        compose.onAllNodesWithContentDescription("Remove favorite")[2].performClick()
        assertEquals(listOf(tracks[2]), toggled)
    }

    @Test
    fun aFavoriteLongPressStillAddsToAPlaylist() {
        // Favorites, like Search, has no selection: a long press adds the song to a playlist.
        val added = mutableListOf<Track>()
        favorites(onAddToPlaylist = { added += it })
        compose.onNodeWithText("Charlie").performTouchInput { longClick() }
        assertEquals(listOf(tracks[2]), added)
        compose.onNodeWithText("1 selected").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("More").assertCountEquals(tracks.size)
    }

    @Test
    fun searchRowsAreListRowsInTheSearchOrder() {
        // LibrarySearch keeps the library's order: "o" finds Delta, Bravo and Alpha (by artist).
        val library = listOf(tracks[3], tracks[1], tracks[0], tracks[2])
        var played: Pair<List<Track>, Long>? = null
        search(library = library, query = "o", onTrackSelected = { queue, startId -> played = queue to startId })
        assertListRows(listOf("Delta", "Bravo", "Alpha"))
        compose.onNodeWithText("Charlie").assertDoesNotExist()
        playAll().assertDoesNotExist()
        compose.onNodeWithText("Bravo").performClick()
        assertEquals(listOf(tracks[3], tracks[1], tracks[0]) to 2L, played)
    }

    @Test
    fun searchMenuOnlyAddsToAPlaylist() {
        val added = mutableListOf<Track>()
        search(query = "a", onAddToPlaylist = { added += it })
        openMenuOf("Charlie")
        compose.onNodeWithText("Move to trash").assertDoesNotExist()
        compose.onNodeWithText("Add to playlist").performClick()
        assertEquals(listOf(tracks[2]), added)
    }

    @Test
    fun recentRowsAreListRowsInTheirOrder() {
        recent()
        val titles = history.map { it.title }
        assertEquals("rows top to bottom", titles, titles.sortedBy { rowOf(it).top })
        val list = list()
        for (title in titles) {
            val row = rowOf(title)
            assertDp("$title row height", 60.dp, row.height)
            assertDp("$title row from the list's start", 4.dp, row.left - list.left)
            assertDp("$title row to the list's end", 4.dp, list.right - row.right)
            val art = artOf(row)
            assertDp("$title art width", 40.dp, art.width)
            assertDp("$title art height", 40.dp, art.height)
            assertDp("$title art from the list's start", 16.dp, art.left - list.left)
            assertDp("$title art centred in the row", 10.dp, art.top - row.top)
        }
    }

    @Test
    fun recentRowsKeepTheirBadgeAndTimeWithTheTimeOnTheSideMargin() {
        recent()
        compose.onNodeWithText("Alpha").assert(hasText("Song")).assert(hasText("Artist One"))
        compose.onNodeWithText("Beach day").assert(hasText("Video"))
        // As before, no ⋮: a history row only plays.
        compose.onAllNodesWithContentDescription("More").assertCountEquals(0)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val list = list()
        for (row in history) {
            val time = DateFormat.getTimeFormat(context).format(Date(row.playedAt))
            val bounds = compose.onNodeWithText(time, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertDp("${row.title}'s time to the list's end", 16.dp, list.right - bounds.right)
        }
    }

    @Test
    fun recentRowTapStillPlaysThatEntry() {
        val played = mutableListOf<HistoryRow>()
        recent(onPlay = { played += it })
        compose.onNodeWithText("Beach day").performClick()
        compose.onNodeWithText("Bravo").performClick()
        assertEquals(listOf(history[1], history[2]), played)
    }

    // --- Videos ---

    @Test
    fun videoRowsAreSixtyDpWithFortyDpArtOnTheSideMargin() {
        videos()
        val row = rowOf("Beach day")
        assertDp("row height", 60.dp, row.height)
        val header = compose.onNodeWithText("October 2025").fetchSemanticsNode().boundsInRoot
        assertDp("month header on the margin", 16.dp, header.left - list().left)
        val art = artOf(row)
        assertDp("art width", 40.dp, art.width)
        assertDp("art height", 40.dp, art.height)
        assertDp("art from the list's start", 16.dp, art.left - list().left)
        assertDp("art centred in the row", 10.dp, art.top - row.top)
    }

    @Test
    fun videoMenuTouchTargetsAreFortyEightDp() {
        videos()
        val menus = menuButtons()
        assertTrue("rows on screen", menus.size >= 4)
        for (index in menus.indices) {
            menuButton(index).assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        }
    }

    @Test
    fun videoMenuGlyphEndsSixteenDpFromTheListEnd() {
        // Videos always have the fast scroller; the rows still reach the list's end.
        videos()
        val listEnd = list().right
        val menus = menuButtons()
        assertEquals("a menu on every row", thumbnails().size, menus.size)
        for (menu in menus) {
            assertDp("glyph to the list's end", 16.dp, listEnd - (menu.center.x + px(12.dp)))
        }
    }

    @Test
    fun withoutTheSystemTrashVideoRowsHaveNoMenu() {
        videos(canTrash = false)
        compose.onAllNodesWithContentDescription("More").assertCountEquals(0)
        assertDp("row height", 60.dp, rowOf("Beach day").height)
    }

    @Test
    fun theInlinePlayerLayoutHasNoPlayAll() {
        // A video plays in the box above the list: no title row, so no pill either.
        videos(player = { Box(Modifier.fillMaxWidth().height(200.dp)) })
        compose.onNodeWithText("Beach day").assertExists()
        playAll().assertDoesNotExist()
    }

    @Test
    fun videoSecondLineLeadsWithTheLength() {
        videos()
        compose.onNodeWithText("Beach day").assert(hasText("12:34 · 1.4 GB · 1080p · Camera"))
    }

    @Test
    fun videoMenuMovesTheVideoToTrash() {
        var trashed: Pair<List<Video>, Boolean>? = null
        videos(onTrashVideos = { picked, whole -> trashed = picked to whole })
        openMenuOf("Concert encore")
        compose.onNodeWithText("Add to playlist").assertDoesNotExist()
        compose.onNodeWithText("Move to trash").performClick()
        assertEquals(listOf(videoState.videos.first { it.title == "Concert encore" }) to false, trashed)
    }

    @Test
    fun videoPlayAllIsAFortyEightDpButtonThatOpensTheFirstVideoAsSorted() {
        val opened = mutableListOf<Video>()
        videos(onVideoClick = { opened += it })
        val pill = playAll()
        pill.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertIsEnabled()
        val bounds = pill.fetchSemanticsNode().boundsInRoot
        assertDp("pill height", 48.dp, bounds.height)
        assertDp("pill from the list's start", 16.dp, bounds.left - list().left)
        assertTrue("pill above the list", bounds.bottom <= list().top)
        pill.performClick()
        assertEquals(listOf(videoState.videos.first()), opened)
    }

    @Test
    fun videoPlayAllOpensTheFirstVideoOfTheSortShown() {
        val zToA = VideosScreenshotTest.sortedState(
            VideosScreenshotTest.videos,
            sort = SortSpec(SortField.Name, ascending = false),
        )
        assertTrue("fixture: Z-A leads with another video", zToA.videos.first() != videoState.videos.first())
        val opened = mutableListOf<Video>()
        videos(state = zToA, onVideoClick = { opened += it })
        playAll().performClick()
        assertEquals(listOf(zToA.videos.first()), opened)
    }

    @Test
    fun anEmptyVideosListHasNoPlayAll() {
        videos(state = VideoLibraryUiState(access = VideoAccess.Full))
        compose.onNodeWithText("No videos found on this device.").assertExists()
        playAll().assertDoesNotExist()
    }

    @Test
    fun videoSecondLineTakesTheSavedLengthLeavesOutMissingValuesAndHasNoBadge() {
        val noLength = VideosScreenshotTest.video(10L, "Tape two", null, 812_000_000L, 720, 576, "Archive", "video/x-ms-wmv")
        // MediaStore has no length for the WMV tape; libVLC's from an earlier play is saved.
        val saved = mapOf("content://media/external/video/media/3" to VideoProgress(610_000L, 1_830_000L))
        videos(state = VideosScreenshotTest.sortedState(VideosScreenshotTest.videos + noLength), saved = saved)
        compose.onNodeWithText("Old holiday tape").assert(hasText("30:30 · 812 MB · 480p · Archive"))
        compose.onNodeWithText("Tape two").assert(hasText("812 MB · 480p · Archive"))
        compose.onNodeWithText("Clip").assert(hasText("0:09 · WhatsApp Video"))
        compose.onNodeWithText("Birthday").assert(hasText("3:06 · 356 MB · 1080p"))
        // The 16:9 frame's length badge is gone: no text is a bare length.
        compose.onAllNodesWithText("12:34", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "33% watched"),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        assertDp("row with a watched hairline", 60.dp, rowOf("Old holiday tape").height)
    }

    @Test
    fun playlistPlayAllStillPlaysAndIsOffWhenNothingCanPlay() {
        // The playlist header shares the pill (PlayAllButton) with the lists.
        var played = 0
        var rows by mutableStateOf(listOf(PlaylistSongRow(10L, "One", "Someone", null, null, false)))
        compose.setContent {
            MusicPlayerTheme {
                PlaylistDetailContent(
                    name = "Mix",
                    songs = rows,
                    onBack = {},
                    onPlayAll = { played++ },
                    onShuffle = {},
                    onRenameClick = {},
                    onDeleteClick = {},
                    onRemove = {},
                    onReorder = {},
                    onPlayFrom = {},
                    contentBottomPadding = 0.dp,
                )
            }
        }
        playAll().assertIsEnabled().performClick()
        assertEquals(1, played)
        rows = emptyList()
        compose.waitForIdle()
        playAll().assertIsNotEnabled()
    }

    @Test
    fun videoRowTapStillOpensThatVideo() {
        val opened = mutableListOf<Video>()
        videos(onVideoClick = { opened += it })
        compose.onNodeWithText("Concert encore").performClick()
        assertEquals(listOf(videoState.videos.first { it.title == "Concert encore" }), opened)
    }

    @Test
    fun whileSelectingVideosPlayAllIsOffAndRowsHaveNoMenu() {
        val opened = mutableListOf<Video>()
        videos(onVideoClick = { opened += it })
        assertTrue("rows have a menu before", menuButtons().isNotEmpty())
        compose.onNodeWithText("Beach day").performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertExists()
        playAll().assertIsNotEnabled()
        compose.onAllNodesWithContentDescription("More").assertCountEquals(0)
        assertTrue(opened.isEmpty())
    }

    // --- Helpers ---

    private fun songs(
        list: List<Track> = tracks,
        withSort: Boolean = true,
        sort: SortSpec = SortSpec(SortField.Name, ascending = true),
        onTrackSelected: (List<Track>, Long) -> Unit = { _, _ -> },
        onAddToPlaylist: (Track) -> Unit = {},
        onToggleFavorite: (Track) -> Unit = {},
        onTrashSongs: (List<Track>, Boolean) -> Unit = { _, _ -> },
        dragFraction: Float? = null,
        canTrash: Boolean = true,
    ) {
        val sorted = shippedSongs(list, sort)
        compose.setContent {
            MusicPlayerTheme {
                LibraryContent(
                    tracks = sorted.items,
                    sorted = if (withSort) sorted else null,
                    nowPlaying = PlayerUiState(),
                    showMiniPlayer = false,
                    onTrackSelected = onTrackSelected,
                    onOpenPlayer = {},
                    onTogglePlayPause = {},
                    onAddToPlaylist = onAddToPlaylist,
                    onToggleFavorite = onToggleFavorite,
                    onTrashSongs = onTrashSongs,
                    contentBottomPadding = 0.dp,
                    fastScrollDragFraction = dragFraction,
                    canTrash = canTrash,
                )
            }
        }
    }

    /** Every song in [list] is a favorite. */
    private fun favorites(
        list: List<Track> = tracks,
        onTrackSelected: (List<Track>, Long) -> Unit = { _, _ -> },
        onAddToPlaylist: (Track) -> Unit = {},
        onToggleFavorite: (Track) -> Unit = {},
    ) {
        compose.setContent {
            MusicPlayerTheme {
                FavoritesContent(
                    tracks = list,
                    favoriteIds = list.map { it.id }.toSet(),
                    nowPlaying = PlayerUiState(),
                    onTrackSelected = onTrackSelected,
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    contentBottomPadding = 0.dp,
                )
            }
        }
    }

    private fun search(
        query: String,
        library: List<Track> = tracks,
        onTrackSelected: (List<Track>, Long) -> Unit = { _, _ -> },
        onAddToPlaylist: (Track) -> Unit = {},
    ) {
        compose.setContent {
            MusicPlayerTheme {
                SearchContent(
                    tracks = library,
                    query = query,
                    onQueryChange = {},
                    favoriteIds = emptySet(),
                    nowPlaying = PlayerUiState(),
                    onTrackSelected = onTrackSelected,
                    onToggleFavorite = {},
                    onAddToPlaylist = onAddToPlaylist,
                    contentBottomPadding = 0.dp,
                )
            }
        }
    }

    /** [history] at a fixed now; inspection mode, so the preview handler feeds the video frame. */
    @OptIn(ExperimentalCoilApi::class)
    private fun recent(onPlay: (HistoryRow) -> Unit = {}) {
        compose.setContent {
            MusicPlayerTheme {
                CompositionLocalProvider(
                    LocalInspectionMode provides true,
                    LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
                ) {
                    RecentHistoryScreen(
                        rows = history,
                        onPlay = onPlay,
                        onClearHistory = {},
                        contentBottomPadding = 0.dp,
                        nowMs = now,
                        zone = zone,
                    )
                }
            }
        }
    }

    /**
     * [titles] top to bottom as 60 dp list rows: 40 dp art on the side margin, centred, and a ⋮
     * with a 48 dp touch target whose glyph ends 16 dp from the list's end.
     */
    private fun assertListRows(titles: List<String>) {
        assertEquals("rows top to bottom", titles, titles.sortedBy { rowOf(it).top })
        val list = list()
        for (title in titles) {
            val row = rowOf(title)
            assertDp("$title row height", 60.dp, row.height)
            val art = artOf(row)
            assertDp("$title art width", 40.dp, art.width)
            assertDp("$title art height", 40.dp, art.height)
            assertDp("$title art from the list's start", 16.dp, art.left - list.left)
            assertDp("$title art centred in the row", 10.dp, art.top - row.top)
        }
        val menus = menuButtons()
        assertEquals("a ⋮ on every row", titles.size, menus.size)
        for (index in menus.indices) {
            menuButton(index).assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
            assertDp("glyph to the list's end", 16.dp, list.right - (menus[index].center.x + px(12.dp)))
        }
    }

    /** Inspection mode only around the list, so the preview handler feeds the thumbnails. */
    @OptIn(ExperimentalCoilApi::class)
    private fun videos(
        state: VideoLibraryUiState = videoState,
        saved: Map<String, VideoProgress> = emptyMap(),
        onVideoClick: (Video) -> Unit = {},
        onTrashVideos: (List<Video>, Boolean) -> Unit = { _, _ -> },
        dragFraction: Float? = null,
        canTrash: Boolean = true,
        player: (@Composable (maxHeight: Dp) -> Unit)? = null,
    ) {
        compose.setContent {
            MusicPlayerTheme {
                CompositionLocalProvider(
                    LocalInspectionMode provides true,
                    LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
                ) {
                    VideosContent(
                        state = state,
                        onVideoClick = onVideoClick,
                        onRequestAccess = {},
                        onOpenSettings = {},
                        contentBottomPadding = 0.dp,
                        savedProgress = saved,
                        player = player,
                        onTrashVideos = onTrashVideos,
                        fastScrollDragFraction = dragFraction,
                        canTrash = canTrash,
                    )
                }
            }
        }
    }

    /** The row's clickable bounds: the merged node that holds [title]. */
    private fun rowOf(title: String): Rect = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot

    /** The lazy list (an open menu also scrolls, but has no scroll-to-index action). */
    private fun list(): Rect = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot

    /** The placed rows' art, top to bottom. */
    private fun thumbnails(): List<Rect> = compose.onAllNodesWithTag(ListThumbnailTag, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .map { it.boundsInRoot }
        .filter { it.height > 0f }
        .sortedBy { it.top }

    /** The art inside [row], found by position rather than tree order. */
    private fun artOf(row: Rect): Rect = thumbnails().first { it.center.y in row.top..row.bottom }

    private fun playAll(): SemanticsNodeInteraction = compose.onNodeWithText("Play all")

    /** The ⋮ buttons' bounds, in row order. */
    private fun menuButtons(): List<Rect> = compose.onAllNodesWithContentDescription("More")
        .fetchSemanticsNodes()
        .map { it.boundsInRoot }

    private fun menuButton(index: Int): SemanticsNodeInteraction =
        compose.onAllNodesWithContentDescription("More")[index]

    /** Opens the ⋮ menu of the row that shows [title]. */
    private fun openMenuOf(title: String) {
        val row = rowOf(title)
        val index = menuButtons().indexOfFirst { it.center.y in row.top..row.bottom }
        assertTrue("$title has a menu", index >= 0)
        menuButton(index).performClick()
        compose.waitForIdle()
    }

    private fun px(dp: Dp): Float = with(compose.density) { dp.toPx() }

    private fun assertDp(what: String, expected: Dp, actualPx: Float) =
        assertEquals("$what (px)", px(expected), actualPx, 0.5f)
}
