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
                MediaStore.Audio.Media.ALBUM_ARTIST,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.GENRE,
                MediaStore.Audio.Media.RELATIVE_PATH,
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
    fun theNewTagsAreDecoded() {
        provider.rows += row(
            id = 1L, title = "Aria", albumArtist = "Various Artists", track = 2005, year = 1999,
            displayName = "05 Aria.mp3", genre = "Opera", relativePath = "Music/Artist/Album/", data = "/storage/emulated/0/ignored/x.mp3",
        )

        val track = MusicRepository(application).loadTracks().single()

        assertEquals("Various Artists", track.albumArtist)
        assertEquals(2, track.discNumber)
        assertEquals(5, track.trackNumber)
        assertEquals(1999, track.year)
        assertEquals("Opera", track.genre)
        assertEquals("Music/Artist/Album", track.folder)
        assertEquals("05 Aria.mp3", track.fileName)
    }

    @Test
    fun aPlainTrackNumberHasNoDiscAndZeroOrNullValuesAreAbsent() {
        provider.rows += row(id = 1L, title = "A", track = 7, year = 0, relativePath = "/")
        provider.rows += row(id = 2L, title = "B", track = 0, year = null, albumArtist = " ", genre = null, relativePath = null)

        val (plain, empty) = MusicRepository(application).loadTracks()

        assertEquals(7, plain.trackNumber)
        assertNull(plain.discNumber)
        assertNull(plain.year)
        assertNull(plain.folder)
        assertNull(empty.trackNumber)
        assertNull(empty.discNumber)
        assertNull(empty.albumArtist)
        assertNull(empty.genre)
        assertNull(empty.folder)
        assertNull(empty.fileName)
    }

    @Test
    @Config(sdk = [28])
    fun beforeApi29TheFolderIsTheParentOfDataAndGenreAndRelativePathAreNotQueried() {
        provider.rows += row(id = 1L, title = "A", genre = "Rock", relativePath = "Wrong/", data = "/storage/emulated/0/Music/Artist/Album/a.mp3")
        provider.rows += row(id = 2L, title = "B", data = "/storage/1234-ABCD/Podcasts/b.mp3")
        provider.rows += row(id = 3L, title = "C", data = "/storage/emulated/0/c.mp3")
        provider.rows += row(id = 4L, title = "D", data = "/sdcard/Music/x.mp3")

        val tracks = MusicRepository(application).loadTracks()

        val projection = provider.lastProjection!!.toList()
        assertTrue(MediaStore.Audio.Media.DATA in projection)
        assertTrue(MediaStore.Audio.Media.GENRE !in projection)
        assertTrue(MediaStore.Audio.Media.RELATIVE_PATH !in projection)
        assertEquals(listOf("Music/Artist/Album", "Podcasts", null, "Music"), tracks.map { it.folder })
        assertNull(tracks[0].genre)
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
        provider.withoutColumns = setOf(MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.SIZE)
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
        albumArtist: String? = null,
        track: Int? = null,
        year: Int? = null,
        displayName: String? = null,
        genre: String? = null,
        relativePath: String? = null,
        data: String? = null,
    ): Array<Any?> = arrayOf(id, title, artist, album, duration, albumId, dateAdded, size, albumArtist, track, year, displayName, genre, relativePath, data)
}

/** The audio table: columns in a fixed order unlike the projection, so mapping must go by name. */
class FakeAudioProvider : ContentProvider() {
    val rows = mutableListOf<Array<Any?>>()
    var returnNull = false
    var withoutColumns: Set<String> = emptySet()
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
        // Only the projected columns that exist, in reverse order: mapping must go by name.
        val picked = COLUMNS.indices.filter { COLUMNS[it] in projection.orEmpty() && COLUMNS[it] !in withoutColumns }.reversed()
        val cursor = MatrixCursor(picked.map { COLUMNS[it] }.toTypedArray())
        rows.forEach { row -> cursor.addRow(picked.map { row.getOrNull(it) }.toTypedArray()) }
        return cursor
    }

    private companion object {
        /** The order of a row's values, see MusicRepositoryTest.row. */
        val COLUMNS = listOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATA,
        )
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
