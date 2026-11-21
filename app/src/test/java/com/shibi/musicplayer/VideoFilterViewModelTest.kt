package com.shibi.musicplayer

import android.Manifest
import android.os.Looper
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.LibrarySorter
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.VideoCounts
import com.shibi.musicplayer.domain.VideoFilter
import com.shibi.musicplayer.domain.VideoIdentity
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.library.InMemorySortPrefs
import com.shibi.musicplayer.library.SortPrefs
import com.shibi.musicplayer.library.VideoLibraryViewModel
import com.shibi.musicplayer.library.VideoRepository
import com.shibi.musicplayer.domain.VideoClassifier
import com.shibi.musicplayer.domain.VideoNameParser
import java.text.Collator
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Videos view model through each filter: All (today's list), Movies, TV and Personal. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VideoFilterViewModelTest {
    private val application = RuntimeEnvironment.getApplication()
    private lateinit var provider: FakeMediaProvider

    @Before
    fun setUp() {
        provider = Robolectric.buildContentProvider(FakeMediaProvider::class.java)
            .create(MediaStore.AUTHORITY)
            .get()
        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        provider.rows += row(1L, "Inception.2010.1080p.mkv", "Inception.2010.1080p", "Movies")
        // A stray metadata title: the movie list shows and sorts by the cleaned file title.
        provider.rows += row(2L, "Arrival (2016).mkv", "ZZZ rip", "Movies")
        provider.rows += row(3L, "Lost.S01E02.mkv", "Lost.S01E02", "Lost")
        provider.rows += row(4L, "Lost.S01E01.Pilot.mkv", "Lost.S01E01.Pilot", "Lost")
        provider.rows += row(5L, "lost.S02E01.mkv", "lost.S02E01", "Lost")
        provider.rows += row(6L, "DJI_20250630052640_0036_D.MP4", "DJI_20250630052640_0036_D", "250630 Munich")
        provider.rows += row(7L, "20250101_120000.mp4", "20250101_120000", "Camera")
        provider.rows += row(8L, "NPA reel.mp4", "NPA reel", "OH edited videos")
    }

    @Test
    fun theDefaultStateIsTodaysList() = runBlocking {
        val viewModel = loaded(InMemorySortPrefs())
        val state = viewModel.uiState.value
        val today = LibrarySorter.videos(
            VideoRepository(application).loadVideos(),
            LibrarySorts.VideoDefault,
            Collator.getInstance(),
            ZoneId.systemDefault(),
            application.getString(R.string.unknown_title),
        )
        assertEquals(VideoFilter.All, state.filter)
        assertEquals(LibrarySorts.VideoDefault, state.sort)
        assertEquals(today.items, state.videos)
        assertEquals(today.runs, state.runs)
        assertEquals(VideoCounts(all = 8, movies = 2, tv = 3, personal = 3), state.counts)
        assertEquals(listOf("Lost"), state.shows.map { it.name })
        assertEquals(VideoIdentity.Movie("Inception", 2010), state.identities[1L])
        assertEquals(VideoIdentity.Personal(VideoIdentity.CameraSource.DJI), state.identities[6L])
    }

    @Test
    fun moviesShowCleanedTitlesInTheMovieSort() {
        val viewModel = loaded(InMemorySortPrefs())
        viewModel.setFilter(VideoFilter.Movies)
        idleMain()
        val state = viewModel.uiState.value
        assertEquals(VideoFilter.Movies, state.filter)
        // The movie sort's default is Name A-Z, by cleaned title.
        assertEquals(SortSpec(SortField.Name, ascending = true), state.sort)
        assertEquals(listOf("Arrival", "Inception"), state.videos.map { it.title })
        assertEquals(listOf(2L, 1L), state.videos.map { it.id })
        assertEquals(listOf("letter_A", "letter_I"), state.runs.map { it.group.key })
        assertEquals(VideoCounts(all = 8, movies = 2, tv = 3, personal = 3), state.counts)
        assertEquals(1, state.shows.size)
    }

    @Test
    fun personalHoldsCameraClipsAndOtherVideosInTheVideoSort() {
        val viewModel = loaded(InMemorySortPrefs(video = SortSpec(SortField.Name, ascending = true)))
        viewModel.setFilter(VideoFilter.Personal)
        idleMain()
        val state = viewModel.uiState.value
        assertEquals(SortSpec(SortField.Name, ascending = true), state.sort)
        assertEquals(listOf(7L, 6L, 8L), state.videos.map { it.id })
        assertEquals(VideoCounts(all = 8, movies = 2, tv = 3, personal = 3), state.counts)
    }

    @Test
    fun tvGroupsEpisodesIntoShowsAndSeasons() {
        val viewModel = loaded(InMemorySortPrefs())
        viewModel.setFilter(VideoFilter.Tv)
        idleMain()
        val state = viewModel.uiState.value
        val show = state.shows.single()
        assertEquals("lost", show.key)
        assertEquals("Lost", show.name)
        assertEquals(listOf(1, 2), show.seasons.map { it.number })
        assertEquals(listOf(4L, 3L, 5L), state.videos.map { it.id })
        assertTrue(state.runs.isEmpty())
        assertEquals(LibrarySorts.VideoDefault, state.sort)
        assertEquals(3, state.counts.tv)
    }

    @Test
    fun continueFollowsSavedProgressWithoutReSorting() {
        val viewModel = loaded(InMemorySortPrefs())
        val before = viewModel.uiState.value.videos
        assertTrue(viewModel.uiState.value.continueByShow.isEmpty())

        // The pilot was watched to the end: continue with S01E02.
        viewModel.setSavedProgress(mapOf(uri(4L) to VideoProgress(0L, 2_000_000L, updatedAtMs = 10L)))
        idleMain()
        assertEquals(3L, viewModel.uiState.value.continueByShow.getValue("lost").video.id)
        assertSame(before, viewModel.uiState.value.videos)
    }

    @Test
    fun theFilterAndMovieSortSurviveANewViewModel() = runBlocking {
        val prefs = InMemorySortPrefs()
        val viewModel = loaded(prefs)
        viewModel.setFilter(VideoFilter.Movies)
        idleMain()
        viewModel.setSort(SortSpec(SortField.Name, ascending = false))
        idleMain()
        assertEquals(listOf("Inception", "Arrival"), viewModel.uiState.value.videos.map { it.title })
        assertEquals(SortSpec(SortField.Name, ascending = false), viewModel.uiState.value.sort)
        // A pick under Movies is the movie sort only.
        assertEquals(LibrarySorts.VideoDefault, prefs.videoSort.first())
        assertEquals(SortSpec(SortField.Name, ascending = false), prefs.movieSort.first())

        val next = loaded(prefs)
        assertEquals(VideoFilter.Movies, next.uiState.value.filter)
        assertEquals(SortSpec(SortField.Name, ascending = false), next.uiState.value.sort)
        assertEquals(listOf("Inception", "Arrival"), next.uiState.value.videos.map { it.title })

        // Back to All: the video sort was never touched.
        next.setFilter(VideoFilter.All)
        idleMain()
        assertEquals(LibrarySorts.VideoDefault, next.uiState.value.sort)
        assertEquals(VideoFilter.All, prefs.videoFilter.first())
    }

    @Test
    fun theFilterSurvivesTheAccessDeniedReset() {
        val viewModel = loaded(InMemorySortPrefs())
        viewModel.setFilter(VideoFilter.Movies)
        idleMain()
        shadowOf(application).denyPermissions(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        viewModel.refresh()
        idleMain()
        assertTrue(viewModel.uiState.value.videos.isEmpty())
        assertEquals(VideoFilter.Movies, viewModel.uiState.value.filter)
    }

    @Test
    fun underAllTheListIsPublishedBeforeClassificationFinishes() {
        val release = CountDownLatch(1)
        val classifier = VideoClassifier { name, folder ->
            release.await(10, TimeUnit.SECONDS)
            VideoNameParser.parse(name, folder)
        }
        val sortThread = Executors.newSingleThreadExecutor()
        try {
            val viewModel = VideoLibraryViewModel(
                application = application,
                repository = VideoRepository(application),
                ioDispatcher = Dispatchers.Unconfined,
                sortPrefs = InMemorySortPrefs(),
                sortDispatcher = sortThread.asCoroutineDispatcher(),
                classifier = classifier,
            )
            viewModel.refresh()
            idleUntil { viewModel.uiState.value.videos.isNotEmpty() }
            // The sorted list is out while the parser is still held: nothing classified yet.
            val first = viewModel.uiState.value
            assertEquals(8, first.videos.size)
            assertTrue(first.isReady)
            assertEquals(VideoCounts(), first.counts)
            assertTrue(first.shows.isEmpty())

            release.countDown()
            idleUntil { viewModel.uiState.value.counts.all == 8 }
            assertEquals(listOf("Lost"), viewModel.uiState.value.shows.map { it.name })
            assertSame(first.videos, viewModel.uiState.value.videos)
        } finally {
            release.countDown()
            sortThread.shutdownNow()
        }
    }

    @Test
    fun nothingIsPublishedUntilTheStoredFilterLoads() {
        val prefs = SlowFilterPrefs()
        val viewModel = loaded(prefs)
        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.videos.isEmpty())
        assertFalse(viewModel.uiState.value.isReady)

        prefs.filter.value = VideoFilter.Movies
        idleMain()
        val state = viewModel.uiState.value
        assertTrue(state.isReady)
        assertEquals(VideoFilter.Movies, state.filter)
        assertEquals(listOf("Arrival", "Inception"), state.videos.map { it.title })
    }

    /** A store whose filter has not answered yet; [filter] stays null until the test sets it. */
    private class SlowFilterPrefs : SortPrefs by InMemorySortPrefs() {
        val filter = MutableStateFlow<VideoFilter?>(null)
        override val videoFilter = filter.filterNotNull()
    }

    private fun idleUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000L
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            idleMain()
            Thread.sleep(5L)
        }
    }

    private fun loaded(prefs: SortPrefs): VideoLibraryViewModel {
        val viewModel = VideoLibraryViewModel(
            application = application,
            repository = VideoRepository(application),
            ioDispatcher = Dispatchers.Unconfined,
            sortPrefs = prefs,
        )
        viewModel.refresh()
        idleMain()
        return viewModel
    }

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun uri(id: Long) = "content://media/external/video/media/$id"

    private fun row(id: Long, displayName: String, title: String, folder: String): Array<Any?> =
        arrayOf(id, displayName, title, 2_000_000L, id * 1_000L, 1920, 1080, "video/mp4", 1_600_000_000L + id, folder)
}
