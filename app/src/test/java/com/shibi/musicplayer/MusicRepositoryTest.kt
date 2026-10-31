package com.shibi.musicplayer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.library.InMemorySortPrefs
import com.shibi.musicplayer.library.LibraryViewModel
import com.shibi.musicplayer.library.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** MediaStore's audio table through a fake provider, and the Songs list's own sort on top of it. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MusicRepositoryTest {
    private val application = RuntimeEnvironment.getApplication()
    private lateinit var provider: FakeAudioProvider

    @Before
    fun setUp() {
        provider = Robolectric.buildContentProvider(FakeAudioProvider::class.java)
            .create(MediaStore.AUTHORITY)
            .get()
    }

    @Test
    fun loadTracksQueriesMusicWithDateAddedAndSizeInTitleOrder() {
        MusicRepository(application).loadTracks()

        assertEquals(Uri.parse("content://media/external/audio/media"), provider.lastUri)
        assertArrayEquals(
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.SIZE,
            ),
            provider.lastProjection,
        )
        assertEquals("${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.MIME_TYPE} LIKE ?", provider.lastSelection)
        // The library order Favorites and Search keep; the Songs list sorts its own copy.
        assertEquals("${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC", provider.lastSortOrder)
    }

    @Test
    fun loadTracksMapsAFullRowIncludingDateAddedAndSize() {
        provider.rows += row(id = 7L, title = "Aria", artist = "Ensemble", album = "Arias", duration = 200_000L, albumId = 3L, dateAdded = 1_700_000_000L, size = 6_400_000L)

        val tracks = MusicRepository(application).loadTracks()

        assertEquals(
            listOf(
                Track(
                    id = 7L,
                    title = "Aria",
                    artist = "Ensemble",
                    album = "Arias",
                    durationMs = 200_000L,
                    contentUri = "content://media/external/audio/media/7",
                    albumArtUri = "content://media/external/audio/albumart/3",
                    dateAddedSeconds = 1_700_000_000L,
                    sizeBytes = 6_400_000L,
                ),
            ),
            tracks,
        )
    }

    @Test
    fun missingTagsBecomeThePlaceholdersAndMissingNumbersUnknown() {
        provider.rows += row(id = 8L, title = " ", artist = MediaStore.UNKNOWN_STRING, album = null, duration = -5L, albumId = 0L, dateAdded = 0L, size = null)

        val track = MusicRepository(application).loadTracks().single()

        assertEquals(application.getString(R.string.unknown_title), track.title)
        assertEquals(application.getString(R.string.unknown_artist), track.artist)
        assertEquals(application.getString(R.string.unknown_album), track.album)
        assertEquals(0L, track.durationMs)
        assertNull(track.albumArtUri)
        assertEquals(0L, track.dateAddedSeconds)
        assertNull(track.sizeBytes)
    }

    @Test
    fun aProviderWithoutTheNewColumnsStillLoads() {
        provider.withoutDateAndSize = true
        provider.rows += row(id = 9L, title = "Old")

        val track = MusicRepository(application).loadTracks().single()

        assertEquals("Old", track.title)
        assertEquals(0L, track.dateAddedSeconds)
        assertNull(track.sizeBytes)
    }

    @Test
    fun noCursorIsAnEmptyLibrary() {
        provider.returnNull = true
        assertEquals(emptyList<Track>(), MusicRepository(application).loadTracks())
    }

    @Test
    fun theSongsListIsPublishedOnlyInTheStoredSortAndTheLibraryOrderStays() {
        provider.rows += row(id = 1L, title = "Banana", dateAdded = 1_700_000_000L)
        provider.rows += row(id = 2L, title = "Apple", dateAdded = 1_750_000_000L)
        provider.rows += row(id = 3L, title = "Cherry", dateAdded = 1_600_000_000L)
        val prefs = InMemorySortPrefs(music = SortSpec(SortField.Name, ascending = false))
        val viewModel = newViewModel(prefs)

        viewModel.refresh()
        idleMain()

        val state = viewModel.uiState.value
        // The fake provider ignores the sort order: the library list is as MediaStore gave it.
        assertEquals(listOf("Banana", "Apple", "Cherry"), state.tracks.map { it.title })
        val songs = state.songs!!
        assertEquals(SortSpec(SortField.Name, ascending = false), songs.spec)
        assertEquals(listOf("Cherry", "Banana", "Apple"), songs.items.map { it.title })
    }

    @Test
    fun theStoredSortIsWaitedForSoTheDefaultNeverShows() {
        provider.rows += row(id = 1L, title = "Banana", dateAdded = 1_700_000_000L)
        provider.rows += row(id = 2L, title = "Apple", dateAdded = 1_750_000_000L)
        val prefs = SlowSortPrefs()
        val viewModel = newViewModel(prefs)

        viewModel.refresh()
        idleMain()
        // Loaded, but the store has not answered: nothing is shown in any order yet.
        assertEquals(2, viewModel.uiState.value.tracks.size)
        assertNull(viewModel.uiState.value.songs)

        prefs.music.value = SortSpec(SortField.Name, ascending = true)
        idleMain()
        assertEquals(listOf("Apple", "Banana"), viewModel.uiState.value.songs!!.items.map { it.title })
    }

    @Test
    fun aPickedSortReordersTheSongsAndIsStored() = runBlocking {
        provider.rows += row(id = 1L, title = "Banana", dateAdded = 1_700_000_000L)
        provider.rows += row(id = 2L, title = "Apple", dateAdded = 1_750_000_000L)
        provider.rows += row(id = 3L, title = "Cherry", dateAdded = 1_600_000_000L)
        val prefs = InMemorySortPrefs()
        val viewModel = newViewModel(prefs)
        viewModel.refresh()
        idleMain()
        // The default is date added, newest first.
        assertEquals(LibrarySorts.MusicDefault, viewModel.uiState.value.songs!!.spec)
        assertEquals(listOf("Apple", "Banana", "Cherry"), viewModel.uiState.value.songs!!.items.map { it.title })

        viewModel.setSort(SortSpec(SortField.Name, ascending = false))
        idleMain()

        assertEquals(listOf("Cherry", "Banana", "Apple"), viewModel.uiState.value.songs!!.items.map { it.title })
        assertEquals(SortSpec(SortField.Name, ascending = false), prefs.musicSort.first())
        // A new view model (the next app start) opens in the stored sort.
        val next = newViewModel(prefs)
        next.refresh()
        idleMain()
        assertEquals(SortSpec(SortField.Name, ascending = false), next.uiState.value.songs!!.spec)
    }

    @Test
    fun aReloadKeepsThePickedSort() {
        provider.rows += row(id = 1L, title = "Banana")
        val viewModel = newViewModel(InMemorySortPrefs())
        viewModel.refresh()
        idleMain()
        viewModel.setSort(SortSpec(SortField.Name, ascending = true))
        idleMain()
        // A MediaStore change (a download) reloads the library; the Songs list stays in its sort.
        provider.rows += row(id = 2L, title = "Apple")
        viewModel.refresh()
        idleMain()
        val songs = viewModel.uiState.value.songs!!
        assertEquals(SortSpec(SortField.Name, ascending = true), songs.spec)
        assertEquals(listOf("Apple", "Banana"), songs.items.map { it.title })
        assertTrue(songs.runs.all { !it.group.isUnknown })
    }

    private fun newViewModel(prefs: com.shibi.musicplayer.library.SortPrefs) = LibraryViewModel(
        application = application,
        repository = MusicRepository(application),
        ioDispatcher = Dispatchers.Unconfined,
        sortPrefs = prefs,
    )

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** A store that has not answered yet; [music] stays null until the test sets it. */
    private class SlowSortPrefs : com.shibi.musicplayer.library.SortPrefs {
        val music = MutableStateFlow<SortSpec?>(null)
        override val videoSort = MutableStateFlow(LibrarySorts.VideoDefault)
        override val musicSort = music.filterNotNull()

        override suspend fun setVideoSort(spec: SortSpec) = Unit

        override suspend fun setMusicSort(spec: SortSpec) {
            music.value = spec
        }
    }

    private fun row(
        id: Long,
        title: String? = null,
        artist: String? = "Artist",
        album: String? = "Album",
        duration: Long? = 180_000L,
        albumId: Long? = 0L,
        dateAdded: Long? = null,
        size: Long? = null,
    ): Array<Any?> = arrayOf(id, title, artist, album, duration, albumId, dateAdded, size)
}

/** The audio table: columns in a fixed order unlike the projection, so mapping must go by name. */
class FakeAudioProvider : ContentProvider() {
    val rows = mutableListOf<Array<Any?>>()
    var returnNull = false
    var withoutDateAndSize = false
    var lastUri: Uri? = null
    var lastProjection: Array<String>? = null
    var lastSelection: String? = null
    var lastSortOrder: String? = null

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        lastUri = uri
        lastProjection = projection
        lastSelection = selection
        lastSortOrder = sortOrder
        if (returnNull) return null
        val columns = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
        )
        val kept = if (withoutDateAndSize) columns.size - 2 else columns.size
        val cursor = MatrixCursor(columns.take(kept).reversed().toTypedArray())
        rows.forEach { cursor.addRow(it.take(kept).reversed().toTypedArray()) }
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
