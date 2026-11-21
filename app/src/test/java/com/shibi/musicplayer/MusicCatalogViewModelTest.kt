package com.shibi.musicplayer

import android.os.Looper
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.AlbumSort
import com.shibi.musicplayer.domain.MusicView
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.library.InMemorySortPrefs
import com.shibi.musicplayer.library.LibraryViewModel
import com.shibi.musicplayer.library.MusicRepository
import com.shibi.musicplayer.library.OpenArtist
import com.shibi.musicplayer.library.SortPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MusicCatalogViewModelTest {
    private val application = RuntimeEnvironment.getApplication()
    private lateinit var provider: FakeAudioProvider

    @Before
    fun setUp() {
        provider = Robolectric.buildContentProvider(FakeAudioProvider::class.java)
            .create(MediaStore.AUTHORITY)
            .get()
    }

    @Test
    fun theCatalogIsPublishedWithTheSongs() {
        provider.rows += row(1L, "One", "Queen", "Opera")
        provider.rows += row(2L, "Two", "Abba", "Gold")
        val viewModel = newViewModel()
        assertNull(viewModel.uiState.value.catalog)

        viewModel.refresh()
        idle()

        val state = viewModel.uiState.value
        assertNotNull(state.songs)
        assertEquals(listOf("abba", "queen"), state.catalog!!.artists.map { it.key })
        assertEquals(2, state.catalog.albums(AlbumSort.Title).size)
        // The songs and the catalog come from the same scan.
        val catalog = state.catalog
        val catalogIds = catalog.artists.flatMap { catalog.artistPage(it.key)!!.songs }.map { it.id }.toSet()
        assertEquals(state.songs!!.items.map { it.id }.toSet(), catalogIds)
    }

    @Test
    fun aSortChangeKeepsTheSameCatalog() {
        provider.rows += row(1L, "One", "Queen", "Opera")
        provider.rows += row(2L, "Two", "Abba", "Gold")
        val viewModel = newViewModel()
        viewModel.refresh()
        idle()
        val before = viewModel.uiState.value.catalog

        viewModel.setSort(SortSpec(SortField.Name, ascending = false))
        idle()

        assertEquals(SortSpec(SortField.Name, ascending = false), viewModel.uiState.value.songs!!.spec)
        assertSame(before, viewModel.uiState.value.catalog)
    }

    @Test
    fun aLibraryChangeRebuildsTheCatalog() {
        provider.rows += row(1L, "One", "Queen", "Opera")
        val viewModel = newViewModel()
        viewModel.refresh()
        idle()
        val before = viewModel.uiState.value.catalog

        provider.rows += row(2L, "Two", "Abba", "Gold")
        viewModel.refresh()
        idle()

        val after = viewModel.uiState.value.catalog
        assertNotSame(before, after)
        assertEquals(listOf("abba", "queen"), after!!.artists.map { it.key })
        assertEquals(2, viewModel.uiState.value.songs!!.items.size)
    }

    // --- The chosen view ---

    @Test
    fun nothingIsPublishedUntilTheStoredViewHasLoaded() {
        provider.rows += row(1L, "One", "Queen", "Opera")
        val prefs = SlowViewPrefs()
        val viewModel = newViewModel(prefs)
        viewModel.refresh()
        idle()
        assertNull(viewModel.uiState.value.songs)
        assertFalse(viewModel.uiState.value.isReady)

        prefs.view.value = MusicView.Artists
        idle()

        val state = viewModel.uiState.value
        assertTrue(state.isReady)
        assertEquals(MusicView.Artists, state.view)
        assertNotNull(state.songs)
        assertNotNull(state.catalog)
    }

    @Test
    fun setViewIsStoredForTheNextStart() {
        val prefs = InMemorySortPrefs()
        val viewModel = newViewModel(prefs)
        idle()
        assertEquals(MusicView.Songs, viewModel.uiState.value.view)

        viewModel.setView(MusicView.Artists)
        idle()

        assertEquals(MusicView.Artists, viewModel.uiState.value.view)
        assertEquals(MusicView.Artists, prefs.musicView.value)
        val next = newViewModel(prefs)
        idle()
        assertEquals(MusicView.Artists, next.uiState.value.view)
    }

    // --- The open artist page ---

    @Test
    fun openArtistAndClosePageSetTheOpenArtist() {
        val viewModel = loaded()
        assertNull(viewModel.uiState.value.openArtistKey)
        viewModel.openArtist("queen")
        assertEquals(OpenArtist("queen"), viewModel.uiState.value.openArtistKey)
        // The unknown artist's key is null: still an open page.
        viewModel.openArtist(null)
        assertEquals(OpenArtist(null), viewModel.uiState.value.openArtistKey)
        viewModel.closePage()
        assertNull(viewModel.uiState.value.openArtistKey)
    }

    @Test
    fun changingTheViewClosesThePage() {
        val viewModel = loaded()
        viewModel.setView(MusicView.Artists)
        viewModel.openArtist("queen")
        viewModel.setView(MusicView.Songs)
        idle()
        assertNull(viewModel.uiState.value.openArtistKey)
    }

    @Test
    fun aRescanWithoutTheOpenArtistClosesItsPage() {
        val viewModel = loaded()
        viewModel.setView(MusicView.Artists)
        viewModel.openArtist("abba")
        // A rescan that keeps the artist keeps the page.
        viewModel.refresh()
        idle()
        assertEquals(OpenArtist("abba"), viewModel.uiState.value.openArtistKey)

        provider.rows.removeAll { it[0] == 2L }
        viewModel.refresh()
        idle()

        assertNull(viewModel.uiState.value.openArtistKey)
        assertEquals(MusicView.Artists, viewModel.uiState.value.view)
    }

    // --- The album sort and the open album page ---

    @Test
    fun setAlbumSortIsStoredForTheNextStart() {
        val prefs = InMemorySortPrefs()
        val viewModel = newViewModel(prefs)
        idle()
        assertEquals(AlbumSort.Title, viewModel.uiState.value.albumSort)

        viewModel.setAlbumSort(AlbumSort.YearNewest)
        idle()

        assertEquals(AlbumSort.YearNewest, viewModel.uiState.value.albumSort)
        assertEquals(AlbumSort.YearNewest, prefs.albumSort.value)
        val next = newViewModel(prefs)
        idle()
        assertEquals(AlbumSort.YearNewest, next.uiState.value.albumSort)
    }

    @Test
    fun anAlbumSortPickedWhileTheStoreReadsWins() {
        val prefs = SlowAlbumSortPrefs()
        provider.rows += row(1L, "One", "Queen", "Opera")
        val viewModel = newViewModel(prefs)
        viewModel.refresh()
        idle()
        // The stored sort is read before anything is published.
        assertFalse(viewModel.uiState.value.isReady)

        viewModel.setAlbumSort(AlbumSort.Artist)
        prefs.sort.value = AlbumSort.YearNewest
        idle()

        assertTrue(viewModel.uiState.value.isReady)
        assertEquals(AlbumSort.Artist, viewModel.uiState.value.albumSort)
    }

    @Test
    fun closePageClosesTheAlbumFirstThenTheArtist() {
        val viewModel = loaded()
        val gold = albumKey(viewModel, "Gold")
        viewModel.setView(MusicView.Artists)
        viewModel.openArtist("abba")
        viewModel.openAlbum(gold)
        assertEquals(gold, viewModel.uiState.value.openAlbumKey)

        viewModel.closePage()
        assertNull(viewModel.uiState.value.openAlbumKey)
        assertEquals(OpenArtist("abba"), viewModel.uiState.value.openArtistKey)

        viewModel.closePage()
        assertNull(viewModel.uiState.value.openArtistKey)
    }

    @Test
    fun changingTheViewClosesTheAlbumAndTheArtist() {
        val viewModel = loaded()
        viewModel.setView(MusicView.Artists)
        viewModel.openArtist("abba")
        viewModel.openAlbum(albumKey(viewModel, "Gold"))
        viewModel.setView(MusicView.Albums)
        idle()
        assertNull(viewModel.uiState.value.openAlbumKey)
        assertNull(viewModel.uiState.value.openArtistKey)
    }

    @Test
    fun aRescanWithoutTheOpenAlbumClosesItAndKeepsTheArtist() {
        provider.rows += row(3L, "Three", "Abba", "Arrival")
        val viewModel = loaded()
        viewModel.setView(MusicView.Artists)
        viewModel.openArtist("abba")
        val arrival = albumKey(viewModel, "Arrival")
        viewModel.openAlbum(arrival)
        // A rescan that keeps the album keeps the page.
        viewModel.refresh()
        idle()
        assertEquals(arrival, viewModel.uiState.value.openAlbumKey)

        provider.rows.removeAll { it[0] == 3L }
        viewModel.refresh()
        idle()

        assertNull(viewModel.uiState.value.openAlbumKey)
        assertEquals(OpenArtist("abba"), viewModel.uiState.value.openArtistKey)
    }

    private fun albumKey(viewModel: LibraryViewModel, title: String): String =
        viewModel.uiState.value.catalog!!.albums(AlbumSort.Title).first { it.title == title }.key

    /** A store whose album sort has not answered yet; [sort] stays null until the test sets it. */
    private class SlowAlbumSortPrefs : SortPrefs by InMemorySortPrefs() {
        val sort = MutableStateFlow<AlbumSort?>(null)
        override val albumSort = sort.filterNotNull()
    }

    /** Queen and Abba, loaded. */
    private fun loaded(): LibraryViewModel {
        provider.rows += row(1L, "One", "Queen", "Opera")
        provider.rows += row(2L, "Two", "Abba", "Gold")
        val viewModel = newViewModel()
        viewModel.refresh()
        idle()
        assertTrue(viewModel.uiState.value.isReady)
        return viewModel
    }

    /** A store whose view has not answered yet; [view] stays null until the test sets it. */
    private class SlowViewPrefs : SortPrefs by InMemorySortPrefs() {
        val view = MutableStateFlow<MusicView?>(null)
        override val musicView = view.filterNotNull()
    }

    private fun newViewModel(prefs: SortPrefs = InMemorySortPrefs()) = LibraryViewModel(
        application = application,
        repository = MusicRepository(application),
        ioDispatcher = Dispatchers.Unconfined,
        sortPrefs = prefs,
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun row(id: Long, title: String, artist: String, album: String): Array<Any?> =
        arrayOf(id, title, artist, album, 180_000L, 0L, null, null, artist, null, null, null, null, null, null)
}
