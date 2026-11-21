package com.shibi.musicplayer

import android.Manifest
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoFilter
import com.shibi.musicplayer.library.InMemorySortPrefs
import com.shibi.musicplayer.library.VideoLibraryViewModel
import com.shibi.musicplayer.library.VideoRepository
import com.shibi.musicplayer.ui.VideosScreen
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import com.shibi.musicplayer.ui.videoSubtitle
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Videos filter chips on the real screen: the real view model over a fake MediaStore and
 * in-memory prefs, driven by touches on the nodes where they are drawn.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class VideoFilterUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val application = RuntimeEnvironment.getApplication()

    // --- The prune ---

    @Test
    fun theLibraryPruneSeesTheFullListUnderAllAndNeverAFilteredOne() {
        val loaded = mutableListOf<List<Long>>()
        show(viewModel(InMemorySortPrefs()), onLibraryLoaded = { loaded += it.map(Video::id) })
        assertTrue("called under All", loaded.isNotEmpty())
        chip("Movies, 2 videos").performClick()
        settle()
        compose.onNodeWithText("Inception").assertIsDisplayed()
        chip("All, 9 videos").performClick()
        settle()
        compose.onNodeWithText("Inception.2010.1080p").assertIsDisplayed()
        val everyId = (1L..9L).toSet()
        assertTrue("called again back on All", loaded.size >= 2)
        loaded.forEach { assertEquals("a filtered list reached the prune: $it", everyId, it.toSet()) }
    }

    @Test
    fun aStoredFilterNeverReachesThePruneEither() {
        val loaded = mutableListOf<List<Long>>()
        show(viewModel(InMemorySortPrefs(filter = VideoFilter.Movies)), onLibraryLoaded = { loaded += it.map(Video::id) })
        compose.onNodeWithText("Inception").assertIsDisplayed()
        // A new view model over the same prefs starts on Movies.
        chip("Movies, 2 videos").assertIsSelected()
        assertTrue("nothing pruned from the Movies list: $loaded", loaded.isEmpty())
    }

    // --- Chips ---

    @Test
    fun chipsShowTheirCountsAndTheChosenOneIsSelected() {
        show(viewModel(InMemorySortPrefs()))
        chip("All, 9 videos").assertIsSelected()
        chip("Movies, 2 videos").assertIsNotSelected()
        chip("TV, 4 videos").assertIsNotSelected()
        chip("Personal, 3 videos").assertIsNotSelected()
        compose.onNodeWithText("9 videos · Newest first").assertIsDisplayed()
        chip("Movies, 2 videos").performClick()
        settle()
        chip("Movies, 2 videos").assertIsSelected()
        chip("All, 9 videos").assertIsNotSelected()
    }

    @Test
    fun allIsUnchangedUnderTheChips() {
        show(viewModel(InMemorySortPrefs()))
        // Newest first: the last row added is the first shown.
        assertEquals(ALL_TITLES.reversed(), shownTitles(ALL_TITLES))
    }

    @Test
    fun moviesShowCleanedTitlesYearsAndNoFolder() {
        val clicked = mutableListOf<Long>()
        val prefs = InMemorySortPrefs()
        show(viewModel(prefs), onVideoClick = { clicked += it.id })
        chip("Movies, 2 videos").performClick()
        settle()
        compose.onNodeWithText("2 movies · Name A–Z").assertIsDisplayed()
        compose.onNodeWithText("Arrival").assertIsDisplayed()
        compose.onNodeWithText("Inception.2010.1080p").assertDoesNotExist()
        compose.onNode(hasText("2016 · ", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("2010 · ", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("Downloads", substring = true)).assertDoesNotExist()
        compose.onNodeWithText("Inception").performClick()
        assertEquals(listOf(1L), clicked)
        assertEquals(VideoFilter.Movies, prefs.videoFilter.value)
    }

    @Test
    fun personalShowsTheSourceAndTheOtherVideoKeepsItsFolder() {
        show(viewModel(InMemorySortPrefs()))
        chip("Personal, 3 videos").performClick()
        settle()
        compose.onNodeWithText("3 clips · Newest first").assertIsDisplayed()
        compose.onNode(hasText(" · DJI", substring = true)).assertIsDisplayed()
        compose.onNode(hasText(" · Phone", substring = true)).assertIsDisplayed()
        compose.onNode(hasText(" · OH edited videos", substring = true)).assertIsDisplayed()
        compose.onNode(hasText(" · Camera", substring = true)).assertDoesNotExist()
        compose.onNodeWithText("Inception").assertDoesNotExist()
    }

    @Test
    fun tvShowsThePlaceholderAndNoRows() {
        show(viewModel(InMemorySortPrefs()))
        chip("TV, 4 videos").performClick()
        settle()
        compose.onNodeWithText("4 episodes").assertIsDisplayed()
        compose.onNodeWithText("TV shows are coming in the next update.").assertIsDisplayed()
        compose.onNodeWithText("Lost.S01E02").assertDoesNotExist()
        compose.onNodeWithText("Play all").assertDoesNotExist()
        chip("TV, 4 videos").assertIsSelected()
    }

    @Test
    fun anEmptyFilterShowsItsLineAndKeepsTheChips() {
        val provider = provider()
        provider.rows += row(1L, "Lost.S01E01.mkv", "Lost.S01E01", "Lost")
        provider.rows += row(2L, "DJI_0001.mp4", "DJI_0001", "Camera")
        show(viewModelOver(InMemorySortPrefs()))
        chip("Movies, 0 videos").performClick()
        settle()
        compose.onNodeWithText("No movies found").assertIsDisplayed()
        compose.onNodeWithText("Play all").assertDoesNotExist()
        chip("All, 2 videos").performClick()
        settle()
        compose.onNodeWithText("Lost.S01E01").assertIsDisplayed()
    }

    @Test
    fun theChipsAreHiddenWhileSelectingAndComeBack() {
        show(viewModel(InMemorySortPrefs()))
        chip("All, 9 videos").assertIsDisplayed()
        compose.onNodeWithText("Inception.2010.1080p").performTouchInput { longClick() }
        settle()
        compose.onNodeWithContentDescription("All, 9 videos").assertDoesNotExist()
        compose.onNodeWithContentDescription(application.getString(R.string.cancel_selection)).performClick()
        settle()
        chip("All, 9 videos").assertIsDisplayed()
    }

    @Test
    fun switchingFiltersReturnsTheListToTheTop() {
        val provider = provider()
        NATO.forEachIndexed { index, name ->
            provider.rows += row(index + 1L, "$name.2001.mkv", "$name.2001", "Downloads")
        }
        provider.rows += row(100L, "DJI_0001.mp4", "DJI_0001", "Camera")
        show(viewModelOver(InMemorySortPrefs()))
        chip("Movies, 26 videos").performClick()
        settle()
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        repeat(3) { compose.onNode(hasScrollAction() and !SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).performTouchInput { swipeUp() } }
        settle()
        compose.onNodeWithText("Alpha").assertDoesNotExist()
        chip("Personal, 1 video").performClick()
        settle()
        chip("Movies, 26 videos").performClick()
        settle()
        compose.onNodeWithText("Alpha").assertIsDisplayed()
    }

    @Test
    fun theRowSubtitleDropsOrReplacesTheFolder() {
        val video = Video(
            id = 1L, title = "t", displayName = "t.mp4", durationMs = 1_000L, sizeBytes = 1_400_000_000L,
            width = 1920, height = 1080, mimeType = "video/mp4", dateAddedSeconds = 1L, folder = "Camera",
            contentUri = "content://media/external/video/media/1",
        )
        assertEquals("1.4 GB · 1080p · Camera", videoSubtitle(video))
        assertEquals("1.4 GB · 1080p", videoSubtitle(video, tail = null))
        assertEquals("1.4 GB · 1080p · DJI", videoSubtitle(video, tail = "DJI"))
    }

    // --- Helpers ---

    private fun chip(description: String) = compose.onNodeWithContentDescription(description)

    private fun show(
        viewModel: VideoLibraryViewModel,
        onVideoClick: (Video) -> Unit = {},
        onLibraryLoaded: (List<Video>) -> Unit = {},
    ) {
        compose.setContent {
            MusicPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    VideosScreen(
                        onVideoClick = onVideoClick,
                        contentBottomPadding = 0.dp,
                        viewModel = viewModel,
                        onLibraryLoaded = onLibraryLoaded,
                    )
                }
            }
        }
        settle()
    }

    private fun provider(): FakeMediaProvider {
        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        return Robolectric.buildContentProvider(FakeMediaProvider::class.java)
            .create(MediaStore.AUTHORITY)
            .get()
    }

    /** Two movies, four episodes of one show, two camera clips and one other video. */
    private fun viewModel(prefs: InMemorySortPrefs): VideoLibraryViewModel {
        val provider = provider()
        provider.rows += row(1L, "Inception.2010.1080p.mkv", "Inception.2010.1080p", "Downloads")
        provider.rows += row(2L, "Arrival (2016).mkv", "Arrival (2016)", "Downloads")
        provider.rows += row(3L, "Lost.S01E01.mkv", "Lost.S01E01", "Lost")
        provider.rows += row(4L, "Lost.S01E02.mkv", "Lost.S01E02", "Lost")
        provider.rows += row(5L, "Lost.S01E03.mkv", "Lost.S01E03", "Lost")
        provider.rows += row(6L, "Lost.S02E01.mkv", "Lost.S02E01", "Lost")
        provider.rows += row(7L, "DJI_20250630052640_0036_D.MP4", "DJI_20250630052640_0036_D", "Camera")
        provider.rows += row(8L, "20250101_120000.mp4", "20250101_120000", "Camera")
        provider.rows += row(9L, "NPA reel.mp4", "NPA reel", "OH edited videos")
        return viewModelOver(prefs)
    }

    private fun viewModelOver(prefs: InMemorySortPrefs) = VideoLibraryViewModel(
        application = application,
        repository = VideoRepository(application),
        ioDispatcher = Dispatchers.Unconfined,
        sortPrefs = prefs,
    )

    private fun row(id: Long, displayName: String, title: String, folder: String): Array<Any?> =
        arrayOf(id, displayName, title, 60_000L, 1_000_000L, 1920, 1080, "video/mp4", 1_600_000_000L + id, folder)

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun shownTitles(titles: List<String>): List<String> =
        titles.mapNotNull { title ->
            compose.onAllNodes(hasText(title)).fetchSemanticsNodes().firstOrNull()?.let { title to it.boundsInRoot.top }
        }.sortedBy { it.second }.map { it.first }

    private companion object {
        val ALL_TITLES = listOf(
            "Inception.2010.1080p", "Arrival (2016)", "Lost.S01E01", "Lost.S01E02", "Lost.S01E03",
            "Lost.S02E01", "DJI_20250630052640_0036_D", "20250101_120000", "NPA reel",
        )
        val NATO = listOf(
            "Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India",
            "Juliett", "Kilo", "Lima", "Mike", "November", "Oscar", "Papa", "Quebec", "Romeo",
            "Sierra", "Tango", "Uniform", "Victor", "Whiskey", "Xray", "Yankee", "Zulu",
        )
    }
}
