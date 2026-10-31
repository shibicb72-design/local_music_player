package com.shibi.musicplayer

import android.Manifest
import android.os.Looper
import android.provider.MediaStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.library.DataStoreSortPrefs
import com.shibi.musicplayer.library.InMemorySortPrefs
import com.shibi.musicplayer.library.SortPrefs
import com.shibi.musicplayer.library.VideoLibraryViewModel
import com.shibi.musicplayer.library.VideoRepository
import com.shibi.musicplayer.video.DataStoreVideoPrefs
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Each list's own persisted sort, and the Videos view model publishing only in it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SortPrefsTest {
    private val application = RuntimeEnvironment.getApplication()
    private lateinit var provider: FakeMediaProvider

    @Before
    fun setUp() {
        provider = Robolectric.buildContentProvider(FakeMediaProvider::class.java)
            .create(MediaStore.AUTHORITY)
            .get()
        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        // Clip 1: oldest, smallest, longest; clip 3: newest, biggest, no MediaStore length.
        provider.rows += video(1L, "Beach", duration = 3_600_000L, size = 5_000_000L, dateAdded = 1_600_000_000L)
        provider.rows += video(2L, "Concert", duration = 60_000L, size = 50_000_000L, dateAdded = 1_700_000_000L)
        provider.rows += video(3L, "Attic", duration = null, size = 900_000_000L, dateAdded = 1_750_000_000L)
    }

    @Test
    fun theVideosListIsPublishedInTheStoredSort() {
        val viewModel = newViewModel(InMemorySortPrefs(video = SortSpec(SortField.Name, ascending = true)))
        viewModel.refresh()
        idleMain()
        val state = viewModel.uiState.value
        assertEquals(SortSpec(SortField.Name, ascending = true), state.sort)
        assertEquals(listOf("Attic", "Beach", "Concert"), state.videos.map { it.title })
        // One header group per letter, made with the sort.
        assertEquals(listOf("letter_A", "letter_B", "letter_C"), state.runs.map { it.group.key })
    }

    @Test
    fun theStoredSortIsWaitedForSoTheDefaultNeverShows() {
        val prefs = SlowSortPrefs()
        val viewModel = newViewModel(prefs)
        viewModel.refresh()
        idleMain()
        // Scanned, but the store has not answered: still loading, nothing shown in any order yet.
        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.videos.isEmpty())

        prefs.video.value = SortSpec(SortField.Name, ascending = true)
        idleMain()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(SortSpec(SortField.Name, ascending = true), state.sort)
        assertEquals(listOf("Attic", "Beach", "Concert"), state.videos.map { it.title })
    }

    @Test
    fun aPickedSortReordersTheVideosAndIsStoredForTheNextStart() = runBlocking {
        val prefs = InMemorySortPrefs()
        val viewModel = newViewModel(prefs)
        viewModel.refresh()
        idleMain()
        assertEquals(LibrarySorts.VideoDefault, viewModel.uiState.value.sort)
        assertEquals(listOf("Attic", "Concert", "Beach"), viewModel.uiState.value.videos.map { it.title })

        viewModel.setSort(SortSpec(SortField.Size, ascending = true))
        idleMain()

        assertEquals(listOf("Beach", "Concert", "Attic"), viewModel.uiState.value.videos.map { it.title })
        assertEquals(SortSpec(SortField.Size, ascending = true), prefs.videoSort.first())
        val next = newViewModel(prefs)
        next.refresh()
        idleMain()
        assertEquals(SortSpec(SortField.Size, ascending = true), next.uiState.value.sort)
        assertEquals(listOf("Beach", "Concert", "Attic"), next.uiState.value.videos.map { it.title })
    }

    @Test
    fun theSortSurvivesTheAccessDeniedReset() {
        val viewModel = newViewModel(InMemorySortPrefs())
        viewModel.refresh()
        idleMain()
        viewModel.setSort(SortSpec(SortField.Name, ascending = false))
        idleMain()

        shadowOf(application).denyPermissions(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        viewModel.refresh()
        idleMain()
        assertEquals(VideoAccess.Denied, viewModel.uiState.value.access)
        assertTrue(viewModel.uiState.value.videos.isEmpty())
        assertEquals(SortSpec(SortField.Name, ascending = false), viewModel.uiState.value.sort)

        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        viewModel.refresh()
        idleMain()
        assertEquals(listOf("Concert", "Beach", "Attic"), viewModel.uiState.value.videos.map { it.title })
    }

    @Test
    fun theLengthSortUsesTheLengthTheRowShows() {
        val viewModel = newViewModel(InMemorySortPrefs(video = SortSpec(SortField.Length, ascending = false)))
        viewModel.refresh()
        idleMain()
        // Attic has no MediaStore length yet: unknown, so last.
        assertEquals(listOf("Beach", "Concert", "Attic"), viewModel.uiState.value.videos.map { it.title })

        // libVLC saved its length (2 h) once it played; the row shows that, and so sorts by it.
        viewModel.setSavedProgress(mapOf("content://media/external/video/media/3" to VideoProgress(0L, 7_200_000L)))
        idleMain()
        assertEquals(listOf("Attic", "Beach", "Concert"), viewModel.uiState.value.videos.map { it.title })
    }

    @Test
    fun aSongsOnlySortIsNotAVideosSort() {
        val viewModel = newViewModel(InMemorySortPrefs())
        viewModel.refresh()
        idleMain()
        viewModel.setSort(SortSpec(SortField.Artist, ascending = true))
        idleMain()
        assertEquals(LibrarySorts.VideoDefault, viewModel.uiState.value.sort)
    }

    @Test
    fun theStoreKeepsEachListsSortUnderItsOwnKey() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(application.filesDir, "sort_prefs_test.preferences_pb").apply { delete() }
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            val prefs = DataStoreSortPrefs(store)
            assertEquals(LibrarySorts.VideoDefault, prefs.videoSort.first())
            assertEquals(LibrarySorts.MusicDefault, prefs.musicSort.first())

            prefs.setVideoSort(SortSpec(SortField.Length, ascending = true))
            prefs.setMusicSort(SortSpec(SortField.Artist, ascending = false))
            assertEquals(SortSpec(SortField.Length, ascending = true), prefs.videoSort.first())
            assertEquals(SortSpec(SortField.Artist, ascending = false), prefs.musicSort.first())
            val stored = store.data.first()
            assertEquals("length_asc", stored[stringPreferencesKey("video_sort")])
            assertEquals("artist_desc", stored[stringPreferencesKey("music_sort")])

            // An artist sort is not a Videos option, and junk is nobody's: both read as defaults.
            store.edit {
                it[stringPreferencesKey("video_sort")] = "artist_asc"
                it[stringPreferencesKey("music_sort")] = "shuffle"
            }
            assertEquals(LibrarySorts.VideoDefault, prefs.videoSort.first())
            assertEquals(LibrarySorts.MusicDefault, prefs.musicSort.first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun theSortStoreIsNotTheVideoStore() = runBlocking {
        // Two DataStores over one file throw at the first read; the sorts have their own file,
        // so both stores read side by side.
        val sort = SortPrefs.get(application).videoSort.first()
        DataStoreVideoPrefs(application).aspectMode.first()
        assertTrue(sort in LibrarySorts.VideoOptions)
    }

    private fun newViewModel(prefs: SortPrefs) = VideoLibraryViewModel(
        application = application,
        repository = VideoRepository(application),
        ioDispatcher = Dispatchers.Unconfined,
        sortPrefs = prefs,
    )

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun video(id: Long, title: String, duration: Long?, size: Long?, dateAdded: Long?): Array<Any?> =
        arrayOf(id, "$title.mp4", title, duration, size, 1920, 1080, "video/mp4", dateAdded, "Camera")

    /** A store that has not answered yet; [video] stays null until the test sets it. */
    private class SlowSortPrefs : SortPrefs {
        val video = MutableStateFlow<SortSpec?>(null)
        override val videoSort = video.filterNotNull()
        override val musicSort = MutableStateFlow(LibrarySorts.MusicDefault)

        override suspend fun setVideoSort(spec: SortSpec) {
            video.value = spec
        }

        override suspend fun setMusicSort(spec: SortSpec) = Unit
    }
}
