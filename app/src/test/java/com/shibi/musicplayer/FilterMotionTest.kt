package com.shibi.musicplayer

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.LibrarySorter
import com.shibi.musicplayer.domain.MusicCatalog
import com.shibi.musicplayer.domain.MusicView
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.TvEpisode
import com.shibi.musicplayer.domain.TvSeason
import com.shibi.musicplayer.domain.TvShow
import com.shibi.musicplayer.domain.UnknownNames
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.domain.VideoCounts
import com.shibi.musicplayer.domain.VideoFilter
import com.shibi.musicplayer.library.OpenArtist
import com.shibi.musicplayer.library.VideoLibraryUiState
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.FadeThroughState
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.LibraryPageTag
import com.shibi.musicplayer.ui.LocalFadeThroughProbe
import com.shibi.musicplayer.ui.PageMotionTarget
import com.shibi.musicplayer.ui.PageSlide
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.rememberFadeThrough
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import java.text.Collator
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class FilterMotionTest {
    private val durationScale = object : MotionDurationScale {
        override val scaleFactor = 1f
    }

    @get:Rule
    val compose = createComposeRule(effectContext = durationScale)

    private val resolver = RuntimeEnvironment.getApplication().contentResolver

    @Before
    fun enableMotion() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @After
    fun restoreMotion() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test
    fun fadeThroughSwapsAtZeroThenSettles() {
        var target by mutableStateOf("old")
        lateinit var fade: FadeThroughState<String>
        compose.setContent {
            fade = remember { FadeThroughState("old", "old") }
            LaunchedEffect(target) { fade.showLatest(target) { target } }
            Text(fade.value)
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        write { target = "new" }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame() // first animation frame defines time zero
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertEquals("new", fade.value)
            assertEquals(0f, fade.alpha.value, 0.01f)
        }
        compose.mainClock.advanceTimeByFrame() // establishes the fade-in animation's time zero
        compose.mainClock.advanceTimeBy(120)
        compose.runOnIdle { assertEquals(1f, fade.alpha.value, 0.001f) }
    }

    @Test
    fun unchangedKeyShowsLiveTargetAndFirstFrameIsOpaque() {
        var target by mutableStateOf("old")
        lateinit var fade: FadeThroughState<String>
        compose.mainClock.autoAdvance = false
        compose.setContent {
            fade = rememberFadeThrough(target, key = "same")
            Text(fade.value)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1f, fade.alpha.value, 0f) }
        write { target = "live" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("live").assertExists()
        compose.runOnIdle { assertEquals(1f, fade.alpha.value, 0f) }
    }

    @Test
    fun reducedMotionKeyChangeSnapsAfterOneFrame() = checkSnap(reduced = true)

    @Test
    fun storedChoiceBeforeChipsWereShownSnaps() = checkSnap(reduced = false)

    @Test
    fun storedVideoFilterSnapsBeforeTheFirstChipRow() {
        var state by mutableStateOf(videoState().copy(isReady = false))
        compose.setContent {
            MusicPlayerTheme {
                VideosContent(
                    state = state,
                    onVideoClick = {}, onRequestAccess = {}, onOpenSettings = {},
                    contentBottomPadding = 0.dp, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        write {
            state = state.copy(isReady = true, filter = VideoFilter.Movies).listing(listOf(NewVideo))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("New clip").assertExists()
        compose.onNodeWithText("Old clip").assertDoesNotExist()
    }

    @Test
    fun storedMusicViewSnapsBeforeTheFirstChipRow() {
        val tracks = listOf(Track(1L, "Song", "Artist", "Stored album", 180_000L, "content://songs/1", null))
        val catalog = MusicCatalog.build(tracks, UnknownNames("Unknown title", "Unknown artist", "Unknown album"), Collator.getInstance())
        var view by mutableStateOf<MusicView?>(null)
        compose.setContent {
            MusicPlayerTheme {
                LibraryContent(
                    tracks = tracks, catalog = catalog, view = view,
                    nowPlaying = PlayerUiState(), showMiniPlayer = false,
                    onTrackSelected = { _, _ -> }, onOpenPlayer = {}, onTogglePlayPause = {},
                    contentBottomPadding = 0.dp, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        write { view = MusicView.Albums }
        compose.mainClock.advanceTimeByFrame()
        // An album card speaks "title, artist"; its texts carry no semantics of their own.
        compose.onNodeWithContentDescription("Stored album, Artist").assertExists()
        compose.onNodeWithText("Song").assertDoesNotExist()
    }

    private fun checkSnap(reduced: Boolean) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (reduced) 0f else 1f)
        var target by mutableStateOf("initial")
        lateinit var fade: FadeThroughState<String>
        compose.setContent {
            fade = rememberFadeThrough(target, animate = { reduced })
            Text(fade.value)
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        write { target = "stored" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("stored").assertExists()
        compose.runOnIdle { assertEquals(1f, fade.alpha.value, 0f) }
    }

    @Test
    fun videoChipSwitchHidesOutgoingSemanticsAndShowsIncomingList() {
        var state by mutableStateOf(videoState())
        // The screen's own fade, heard through the tests-only probe: the filter body is its one FadeThrough.
        var fade: FadeThroughState<*>? = null
        compose.setContent {
            MusicPlayerTheme {
                CompositionLocalProvider(LocalFadeThroughProbe provides { fade = it }) {
                    VideosContent(
                        state = state,
                        onVideoClick = {}, onRequestAccess = {}, onOpenSettings = {},
                        onFilterChange = { state = state.copy(filter = it).listing(listOf(NewVideo)) },
                        contentBottomPadding = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Movies, 1 video").performClick()
        // The key-change frame already hides the outgoing list, so nothing can find or tap it.
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Old clip").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Old clip").assertDoesNotExist()
        compose.onNodeWithText("New clip").assertDoesNotExist()
        // Out takes 80 ms from the effect's first frame, which starts a frame or two after the tap.
        compose.mainClock.advanceTimeBy(128)
        compose.onNodeWithText("New clip").assertExists()
        compose.mainClock.advanceTimeBy(160)
        compose.onNodeWithText("New clip").assertExists()
        // About 330 ms after the tap the fade-in is over.
        compose.runOnIdle {
            assertEquals(VideoFilter.Movies, requireNotNull(fade).shownKey)
            assertEquals(1f, requireNotNull(fade).alpha.value, 0.001f)
        }
    }

    /**
     * Reduce motion composes one page at a time: the outgoing one leaves composition on the same
     * frame the new one arrives, rather than being drawn, merely hidden, for a frame.
     */
    @Test
    fun reducedPageSlideNeverComposesTwoPages() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val list = PageMotionTarget(null, 0, LazyListState())
        val page = PageMotionTarget("page", 1, LazyListState())
        var target by mutableStateOf(list)
        val composed = mutableSetOf<Int>()
        compose.setContent {
            PageSlide(target) { shown ->
                DisposableEffect(shown.depth) {
                    composed += shown.depth
                    onDispose { composed -= shown.depth }
                }
                Text("depth ${shown.depth}")
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        write { target = page }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(setOf(1), composed) }
        write { target = list }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(setOf(0), composed) }
    }

    @Test
    fun artistPageSlidesAndBackRestoresListPosition() = checkArtistPage(reduced = false)

    @Test
    fun artistPageAndBackSnapWithReduceMotion() = checkArtistPage(reduced = true)

    private fun checkArtistPage(reduced: Boolean) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (reduced) 0f else 1f)
        val tracks = (0..35).map { index ->
            Track(index.toLong(), "Song $index", "Artist $index", "Album $index", 180_000L, "content://songs/$index", null)
        }
        val catalog = MusicCatalog.build(tracks, UnknownNames("Unknown title", "Unknown artist", "Unknown album"), Collator.getInstance())
        val listState = LazyListState(firstVisibleItemIndex = 8)
        var artist by mutableStateOf<OpenArtist?>(null)
        compose.setContent {
            MusicPlayerTheme {
                LibraryContent(
                    tracks = tracks, catalog = catalog, view = MusicView.Artists,
                    openArtist = artist, onOpenArtist = { artist = OpenArtist(it) }, onClosePage = { artist = null },
                    nowPlaying = PlayerUiState(), showMiniPlayer = false,
                    onTrackSelected = { _, _ -> }, onOpenPlayer = {}, onTogglePlayPause = {},
                    listState = listState, contentBottomPadding = 0.dp, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()
        val before = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        val firstRow = listState.layoutInfo.visibleItemsInfo.first().key
        val key = catalog.artists[8].key
        compose.mainClock.autoAdvance = false
        write { artist = OpenArtist(key) }
        // Reduce motion: the very next frame is the resting page. Otherwise, mid-slide.
        if (reduced) compose.mainClock.advanceTimeByFrame() else compose.mainClock.advanceTimeBy(64)
        // Only a page has a Back arrow. The unmerged tree still lists content whose semantics are
        // cleared, so one root there means the list left composition; mid-slide it is only hidden.
        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onAllNodesWithTag(LibraryPageTag, useUnmergedTree = reduced).assertCountEquals(1)
        val page = compose.onNodeWithTag(LibraryPageTag)
        val earlyLeft = page.fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(300)
        val restingLeft = page.fetchSemanticsNode().boundsInRoot.left
        if (reduced) {
            assertEquals(restingLeft, earlyLeft, 0.5f)
        } else {
            assertTrue("page decelerates to rest", earlyLeft > restingLeft + 1f)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        if (reduced) compose.mainClock.advanceTimeByFrame() else compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onAllNodesWithTag(LibraryPageTag, useUnmergedTree = true).assertCountEquals(1)
        compose.runOnIdle {
            assertEquals(before, listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset)
            assertEquals(firstRow, listState.layoutInfo.visibleItemsInfo.first().key)
        }
    }

    @Test
    fun playerIdentitySurvivesChipSwitchAndShowOpen() {
        val episode = TvEpisode(OldVideo, 1, 1, null, null)
        val show = TvShow("show", "Show", listOf(TvSeason(1, listOf(episode))))
        var state by mutableStateOf(videoState().copy(shows = listOf(show)))
        var identities = 0
        compose.setContent {
            MusicPlayerTheme {
                VideosContent(
                    state = state,
                    onVideoClick = {}, onRequestAccess = {}, onOpenSettings = {},
                    onFilterChange = { state = state.copy(filter = it) },
                    onOpenShow = { state = state.copy(openShowKey = it) },
                    player = { remember { identities++ }; Box(Modifier) },
                    contentBottomPadding = 0.dp, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("TV, 1 video").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Show").performClick()
        compose.waitForIdle()
        // The page really opened: its header's Back arrow is there and the show list is not.
        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onNodeWithContentDescription("TV, 1 video").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, identities) }
    }

    /**
     * A write from the test thread reaches the recomposer only once apply notifications are sent;
     * a click sends them itself. Without this, a held clock's next frames see no change.
     */
    private fun write(change: () -> Unit) = compose.runOnIdle {
        change()
        Snapshot.sendApplyNotifications()
    }

    private fun videoState() = VideoLibraryUiState(
        access = VideoAccess.Full, isReady = true,
        counts = VideoCounts(all = 1, movies = 1, tv = 1),
    ).listing(listOf(OldVideo))

    /** The list draws one header and its rows per sort run: without runs it has no rows at all. */
    private fun VideoLibraryUiState.listing(videos: List<Video>): VideoLibraryUiState {
        val sorted = LibrarySorter.videos(
            videos,
            SortSpec(SortField.Name, ascending = true),
            Collator.getInstance(),
            ZoneId.systemDefault(),
            "Unknown title",
        )
        return copy(videos = sorted.items, sort = sorted.spec, runs = sorted.runs)
    }

    private companion object {
        val OldVideo = Video(1L, "Old clip", "Old clip.mp4", 180_000L, 1_000_000L, 1920, 1080, "video/mp4", 1_700_000_000L, "Camera", "content://videos/1")
        val NewVideo = OldVideo.copy(id = 2L, title = "New clip", contentUri = "content://videos/2")
    }
}
