package com.shibi.musicplayer

import android.os.Looper
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.AlbumSort
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.library.InMemorySortPrefs
import com.shibi.musicplayer.library.LibraryViewModel
import com.shibi.musicplayer.library.MusicRepository
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
        assertEquals(2, state.catalog!!.albums(AlbumSort.Title).size)
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

    private fun newViewModel() = LibraryViewModel(
        application = application,
        repository = MusicRepository(application),
        ioDispatcher = Dispatchers.Unconfined,
        sortPrefs = InMemorySortPrefs(),
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun row(id: Long, title: String, artist: String, album: String): Array<Any?> =
        arrayOf(id, title, artist, album, 180_000L, 0L, null, null, artist, null, null, null, null, null, null)
}
