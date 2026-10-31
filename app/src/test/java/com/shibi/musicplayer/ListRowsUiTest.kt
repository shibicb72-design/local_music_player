package com.shibi.musicplayer

import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.ListThumbnailTag
import com.shibi.musicplayer.ui.SongFastScrollerTag
import com.shibi.musicplayer.ui.VideoFastScrollerTag
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Songs and Videos list rows: content 16 dp in from the row's edges, 60 dp rows with 40 dp
 * art, a ⋮ menu with a 48 dp touch target, and a Play all pill (a real Button, 48 dp tall) at
 * the top of each list. The values are the spec's, written out rather than read from the code's
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

    // --- Songs ---

    @Test
    fun songRowsAreSixtyDpWithFortyDpArtOnTheSideMargin() {
        songs()
        val row = rowOf("Alpha")
        assertDp("row height", 60.dp, row.height)
        val art = thumbnails().first()
        assertDp("art width", 40.dp, art.width)
        assertDp("art height", 40.dp, art.height)
        assertDp("art from the list's start", 16.dp, art.left - list().left)
        assertDp("art centred in the row", 10.dp, art.top - row.top)
    }

    @Test
    fun songMenuGlyphEndsSixteenDpFromTheRowsEnd() {
        // No sort, no fast scroller: the rows reach the list's end.
        songs(sorted = false)
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
    fun songMenuTouchTargetsEndLeftOfTheScroller() {
        // The thumb only exists while it shows; the tests-only drag holds it on screen.
        songs(dragFraction = 0f)
        val thumb = compose.onNodeWithTag(SongFastScrollerTag).fetchSemanticsNode().boundsInRoot
        val buttons = menuButtons() + compose.onAllNodesWithContentDescription("Favorite")
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
        assertEquals(tracks.size * 2, buttons.size)
        for (button in buttons) {
            val touchRight = button.center.x + px(24.dp)
            assertTrue("touch target ends at $touchRight, thumb starts at ${thumb.left}", touchRight <= thumb.left)
        }
    }

    @Test
    fun songSecondLineIsTheArtistThenTheLength() {
        songs()
        compose.onNodeWithText("Alpha")
            .assert(hasText("Artist One"))
            .assert(hasText("· 01:40", substring = true))
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
        compose.onNodeWithText("Alpha").performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertExists()
        playAll().assertIsNotEnabled()
        compose.onAllNodesWithContentDescription("More").assertCountEquals(0)
        assertDp("row height while selecting", 60.dp, rowOf("Bravo").height)
    }

    // --- Videos ---

    @Test
    fun videoRowsAreSixtyDpWithFortyDpArtOnTheSideMargin() {
        videos()
        val row = rowOf("Beach day")
        assertDp("row height", 60.dp, row.height)
        val art = thumbnails().first()
        assertDp("art width", 40.dp, art.width)
        assertDp("art height", 40.dp, art.height)
        assertDp("art from the list's start", 16.dp, art.left - list().left)
        assertDp("art centred in the row", 10.dp, art.top - row.top)
    }

    @Test
    fun videoMenuTouchTargetsAreFortyEightDpAndEndLeftOfTheScroller() {
        videos(dragFraction = 0f)
        val thumb = compose.onNodeWithTag(VideoFastScrollerTag).fetchSemanticsNode().boundsInRoot
        val menus = menuButtons()
        assertTrue("rows on screen", menus.size >= 4)
        for (index in menus.indices) {
            menuButton(index).assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
            val touchRight = menus[index].center.x + px(24.dp)
            assertTrue("touch target ends at $touchRight, thumb starts at ${thumb.left}", touchRight <= thumb.left)
        }
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
        compose.onNodeWithText("Beach day").performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertExists()
        playAll().assertIsNotEnabled()
        compose.onAllNodesWithContentDescription("More").assertCountEquals(0)
        assertTrue(opened.isEmpty())
    }

    // --- Helpers ---

    private fun songs(
        sorted: Boolean = true,
        onTrackSelected: (List<Track>, Long) -> Unit = { _, _ -> },
        onAddToPlaylist: (Track) -> Unit = {},
        onToggleFavorite: (Track) -> Unit = {},
        onTrashSongs: (List<Track>, Boolean) -> Unit = { _, _ -> },
        dragFraction: Float? = null,
    ) {
        compose.setContent {
            MusicPlayerTheme {
                LibraryContent(
                    tracks = shipped.items,
                    sorted = if (sorted) shipped else null,
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
                )
            }
        }
    }

    /** Inspection mode only around the list, so the preview handler feeds the thumbnails. */
    @OptIn(ExperimentalCoilApi::class)
    private fun videos(
        onVideoClick: (Video) -> Unit = {},
        onTrashVideos: (List<Video>, Boolean) -> Unit = { _, _ -> },
        dragFraction: Float? = null,
    ) {
        compose.setContent {
            MusicPlayerTheme {
                CompositionLocalProvider(
                    LocalInspectionMode provides true,
                    LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
                ) {
                    VideosContent(
                        state = videoState,
                        onVideoClick = onVideoClick,
                        onRequestAccess = {},
                        onOpenSettings = {},
                        contentBottomPadding = 0.dp,
                        onTrashVideos = onTrashVideos,
                        fastScrollDragFraction = dragFraction,
                    )
                }
            }
        }
    }

    /** The row's clickable bounds: the merged node that holds [title]. */
    private fun rowOf(title: String): Rect = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot

    private fun list(): Rect = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot

    private fun thumbnails(): List<Rect> = compose.onAllNodesWithTag(ListThumbnailTag, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .map { it.boundsInRoot }

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
