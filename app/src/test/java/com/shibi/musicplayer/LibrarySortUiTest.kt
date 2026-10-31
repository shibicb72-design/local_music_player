package com.shibi.musicplayer

import android.Manifest
import android.os.Looper
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.ScrollSection
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.thinStops
import com.shibi.musicplayer.library.InMemorySortPrefs
import com.shibi.musicplayer.library.LibraryViewModel
import com.shibi.musicplayer.library.MusicRepository
import com.shibi.musicplayer.library.VideoLibraryViewModel
import com.shibi.musicplayer.library.VideoRepository
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.LibraryScreen
import com.shibi.musicplayer.ui.SongFastScrollerTag
import com.shibi.musicplayer.ui.SortButtonTag
import com.shibi.musicplayer.ui.VideosScreen
import com.shibi.musicplayer.ui.sortOptionTag
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The sort menu and the Songs fast scroller on the real screens, with the real view models over
 * a fake MediaStore and in-memory sort prefs. Every step is a touch on the node where it is drawn.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LibrarySortUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val application = RuntimeEnvironment.getApplication()
    private lateinit var view: View

    // --- Videos ---

    @Test
    fun pickingASortOnVideosReordersJumpsToTheTopAndIsStored() {
        val prefs = InMemorySortPrefs()
        val viewModel = videosViewModel(prefs)
        var shown by mutableStateOf(true)
        compose.setContent {
            MusicPlayerTheme {
                // Like the NavHost on the way to and back from fullscreen: the screen leaves and
                // comes back with its saved state.
                val holder = rememberSaveableStateHolder()
                if (shown) {
                    holder.SaveableStateProvider("videos") {
                        VideosScreen(onVideoClick = {}, contentBottomPadding = 0.dp, viewModel = viewModel)
                    }
                }
            }
        }
        settle()
        // Newest first: Zulu was added last.
        compose.onNodeWithText("Zulu").assertIsDisplayed()
        compose.onNodeWithText("26 videos · Newest first").assertIsDisplayed()
        scrollDown()
        assertGone("Zulu")

        compose.onNodeWithTag(SortButtonTag).performClick()
        settle()
        compose.onNodeWithTag(sortOptionTag(SortSpec(SortField.Name, ascending = false))).assertIsDisplayed()
        compose.onNodeWithTag(sortOptionTag(SortSpec(SortField.Name, ascending = true))).performClick()
        settle()

        // Reordered by name and back at the top: the A header, then Alpha.
        compose.onNodeWithText("26 videos · Name A–Z").assertIsDisplayed()
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithText("A").assertIsDisplayed()
        assertTrue(top("Alpha") < top("Bravo"))
        assertEquals(SortSpec(SortField.Name, ascending = true), runBlocking { prefs.videoSort.first() })

        // Scrolled again, then away and back (fullscreen): no jump to the top this time.
        scrollDown()
        assertGone("Alpha")
        val firstShown = shownTitles(NatoNames).first()
        shown = false
        settle()
        shown = true
        settle()
        assertGone("Alpha")
        compose.onNodeWithText(firstShown).assertIsDisplayed()
    }

    @Test
    fun theSortMenuIsHiddenWhileSelecting() {
        val viewModel = videosViewModel(InMemorySortPrefs())
        compose.setContent {
            MusicPlayerTheme { VideosScreen(onVideoClick = {}, contentBottomPadding = 0.dp, viewModel = viewModel) }
        }
        settle()
        compose.onNodeWithTag(SortButtonTag).assertIsDisplayed()
        compose.onNodeWithText("Zulu").performTouchInput { longClick() }
        settle()
        compose.onNodeWithTag(SortButtonTag).assertDoesNotExist()
    }

    // --- Songs ---

    @Test
    fun pickingASortOnSongsReordersJumpsToTheTopIsStoredAndTheQueueFollowsIt() {
        val audio = audioProvider()
        NatoNames.forEachIndexed { index, name ->
            // Added in name order, so newest first is Z to A.
            audio.rows += song(index + 1L, name, artist = "Band", dateAdded = 1_600_000_000L + index * 86_400L)
        }
        val prefs = InMemorySortPrefs()
        var queue: List<Track> = emptyList()
        setSongs(libraryViewModel(prefs)) { tracks, _ -> queue = tracks }
        compose.onNodeWithText("Zulu").assertIsDisplayed()
        compose.onNodeWithText("26 songs · Newest first").assertIsDisplayed()
        scrollDown()
        assertGone("Zulu")

        compose.onNodeWithTag(SortButtonTag).performClick()
        settle()
        compose.onNodeWithTag(sortOptionTag(SortSpec(SortField.Name, ascending = true))).performClick()
        settle()

        compose.onNodeWithText("26 songs · Name A–Z").assertIsDisplayed()
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        assertTrue(top("Alpha") < top("Bravo"))
        assertEquals(SortSpec(SortField.Name, ascending = true), runBlocking { prefs.musicSort.first() })

        // The queue is the list as shown.
        compose.onNodeWithText("Bravo").performClick()
        settle()
        assertEquals(NatoNames, queue.map { it.title })
    }

    @Test
    fun draggingTheSongsScrollerOntoALetterHoldsTheListThereNamesItAndTicks() {
        val audio = audioProvider()
        var id = 1L
        for (word in listOf("Apple", "Mango", "Zest")) {
            for (n in 1..10) audio.rows += song(id++, "$word $n")
        }
        setSongs(libraryViewModel(InMemorySortPrefs(music = SortSpec(SortField.Name, ascending = true)))) { _, _ -> }
        // Scroll a little and back, so the thumb shows at the top; the clock is held, so it
        // does not fade before the drag.
        compose.mainClock.autoAdvance = false
        flingAndSettle { swipeUp(durationMillis = 300) }
        flingAndSettle { swipeDown(durationMillis = 300) }
        val thumb = compose.onNodeWithTag(SongFastScrollerTag)
        thumb.assertIsDisplayed()
        assertEquals(
            listOf("Scroll by name"),
            thumb.fetchSemanticsNode().config[SemanticsProperties.ContentDescription],
        )

        // M starts at entry 10 of 30: its dot is at 10/29 of the track (the list less the thumb).
        val listHeight = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot.height
        val dotY = (listHeight - with(compose.density) { 56.dp.toPx() }) * 10f / 29f
        thumb.performTouchInput {
            down(center)
            moveBy(Offset(0f, viewConfiguration.touchSlop))
            moveBy(Offset(0f, dotY))
        }
        compose.mainClock.advanceTimeBy(100)

        // The bubble, by the thumb on the right (the rows' art tiles also show initials, on the left).
        val list = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the bubble names M",
            compose.onAllNodesWithText("M").fetchSemanticsNodes().any { it.boundsInRoot.left > list.center.x },
        )
        val listTop = list.top
        assertTrue("Mango 1 heads the list", top("Mango 1") - listTop < with(compose.density) { 72.dp.toPx() })
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK, shadowOf(view).lastHapticFeedbackPerformed())
        thumb.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test
    fun underTheDateSortTheSongsScrollerKeepsTwoCloseBusyWeeks() {
        // Newest first: six quiet weeks of 20 songs, a busy week of 25 and a busier one of 40
        // right after it, then six quiet weeks. Thinning would keep only the 40's dot.
        val audio = audioProvider()
        val newest = LocalDate.of(2025, 6, 25).atTime(12, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
        var id = 1L
        val counts = List(6) { 20 } + listOf(25, 40) + List(6) { 20 }
        counts.forEachIndexed { week, count ->
            // Wednesdays a week apart; a second apart inside a week, so song 1 is its newest.
            for (n in 1..count) audio.rows += song(id++, "Week $week song $n", dateAdded = newest - week * 604_800L - n)
        }
        setSongs(libraryViewModel(InMemorySortPrefs()), modifier = Modifier.height(260.dp)) { _, _ -> }
        compose.mainClock.autoAdvance = false
        flingAndSettle { swipeUp(durationMillis = 300) }
        flingAndSettle { swipeDown(durationMillis = 300) }
        val thumb = compose.onNodeWithTag(SongFastScrollerTag)
        thumb.assertIsDisplayed()

        // The busy weeks start at songs 120 and 145 of 305: their dots are inside one snap radius.
        val list = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        val travel = list.height - with(compose.density) { 56.dp.toPx() }
        val stops = listOf(ScrollSection(120, 25), ScrollSection(145, 40))
        assertEquals(
            "the dots are inside one snap radius on a ${list.height} px list",
            listOf(stops[1]),
            thinStops(stops, 305, travel, with(compose.density) { 16.dp.toPx() }),
        )
        val start = (thumb.fetchSemanticsNode().boundsInRoot.top - list.top) / travel
        thumb.performTouchInput {
            down(center)
            moveBy(Offset(0f, viewConfiguration.touchSlop))
            moveBy(Offset(0f, (120f / 304f - start) * travel))
        }
        compose.mainClock.advanceTimeBy(100)

        // As on Videos: the smaller week keeps its dot, so the thumb holds the list at its start.
        assertTrue("Week 6 song 1 heads the list", top("Week 6 song 1") - list.top < with(compose.density) { 72.dp.toPx() })
        thumb.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test
    fun theSongsScrollerNeverCoversARowsHeartOrMenu() {
        val audio = audioProvider()
        NatoNames.forEachIndexed { index, name -> audio.rows += song(index + 1L, name) }
        setSongs(libraryViewModel(InMemorySortPrefs())) { _, _ -> }
        compose.mainClock.autoAdvance = false
        flingAndSettle { swipeUp(durationMillis = 300) }
        val thumb = compose.onNodeWithTag(SongFastScrollerTag).fetchSemanticsNode().boundsInRoot
        val buttons = listOf(R.string.favorite, R.string.unfavorite, R.string.more_actions).flatMap { res ->
            compose.onAllNodes(hasContentDescription(application.getString(res)))
                .fetchSemanticsNodes()
                .map { it.boundsInRoot }
        }
        assertTrue("rows on screen", buttons.size >= 4)
        for (button in buttons) {
            assertFalse("thumb $thumb covers a row button at $button", thumb.overlaps(button))
            assertTrue("the button ends left of the thumb", button.right <= thumb.left)
        }
    }

    // --- Helpers ---

    private fun videosViewModel(prefs: InMemorySortPrefs): VideoLibraryViewModel {
        val provider = Robolectric.buildContentProvider(FakeMediaProvider::class.java)
            .create(MediaStore.AUTHORITY)
            .get()
        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        NatoNames.forEachIndexed { index, name ->
            // Ten days apart in name order: newest first is Z to A, across several months.
            provider.rows += arrayOf<Any?>(
                index + 1L, "$name.mp4", name, 60_000L, 1_000_000L, 1920, 1080, "video/mp4",
                1_600_000_000L + index * 864_000L, "Camera",
            )
        }
        return VideoLibraryViewModel(
            application = application,
            repository = VideoRepository(application),
            ioDispatcher = Dispatchers.Unconfined,
            sortPrefs = prefs,
        )
    }

    private fun audioProvider(): FakeAudioProvider {
        shadowOf(application).grantPermissions(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        return Robolectric.buildContentProvider(FakeAudioProvider::class.java)
            .create(MediaStore.AUTHORITY)
            .get()
    }

    private fun libraryViewModel(prefs: InMemorySortPrefs) = LibraryViewModel(
        application = application,
        repository = MusicRepository(application),
        ioDispatcher = Dispatchers.Unconfined,
        sortPrefs = prefs,
    )

    private fun setSongs(
        viewModel: LibraryViewModel,
        modifier: Modifier = Modifier,
        onTrackSelected: (List<Track>, Long) -> Unit,
    ) {
        compose.setContent {
            view = LocalView.current
            MusicPlayerTheme {
                // The box takes the window's tight constraints, so [modifier] can size the screen.
                Box(Modifier.fillMaxSize()) {
                    LibraryScreen(
                        viewModel = viewModel,
                        nowPlaying = PlayerUiState(),
                        onTrackSelected = onTrackSelected,
                        onOpenPlayer = {},
                        onTogglePlayPause = {},
                        contentBottomPadding = 0.dp,
                        modifier = modifier,
                    )
                }
            }
        }
        settle()
    }

    private fun song(id: Long, title: String, artist: String = "Artist", dateAdded: Long? = null): Array<Any?> =
        arrayOf(id, title, artist, "Album", 180_000L, 0L, dateAdded, 4_000_000L)

    private fun scrollDown() {
        repeat(2) { compose.onNode(hasScrollAction()).performTouchInput { swipeUp() } }
        settle()
    }

    /** A swipe on the list with the clock held, then 1.2 s for the fling: inside the thumb's 2 s. */
    private fun flingAndSettle(swipe: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
        compose.onNode(hasScrollAction()).performTouchInput(swipe)
        repeat(24) { compose.mainClock.advanceTimeBy(50) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun top(text: String): Float = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    private fun assertGone(text: String) {
        val visible = compose.onAllNodesWithText(text).fetchSemanticsNodes().any { node ->
            val bounds = node.boundsInRoot
            bounds.height > 0f && bounds != Rect.Zero
        }
        assertFalse("\"$text\" is still on screen", visible)
    }

    /** The titles on screen, top to bottom. */
    private fun shownTitles(titles: List<String>): List<String> =
        titles.mapNotNull { title ->
            compose.onAllNodesWithText(title).fetchSemanticsNodes().firstOrNull()?.let { title to it.boundsInRoot.top }
        }.sortedBy { it.second }.map { it.first }

    private companion object {
        val NatoNames = listOf(
            "Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India",
            "Juliett", "Kilo", "Lima", "Mike", "November", "Oscar", "Papa", "Quebec", "Romeo",
            "Sierra", "Tango", "Uniform", "Victor", "Whiskey", "Xray", "Yankee", "Zulu",
        )
    }
}
