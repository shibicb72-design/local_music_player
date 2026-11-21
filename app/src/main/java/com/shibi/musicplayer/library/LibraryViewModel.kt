package com.shibi.musicplayer.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.AlbumSort
import com.shibi.musicplayer.domain.LibrarySorter
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.MusicCatalog
import com.shibi.musicplayer.domain.MusicView
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.SortedList
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.UnknownNames
import java.io.IOException
import java.text.Collator
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    /** The library order (title A-Z): Favorites, Search, Recent and playlists use it. */
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** True once a load has succeeded; a pruning step must not trust an empty list before that. */
    val loaded: Boolean = false,
    /**
     * The Songs list: [tracks] in the stored sort. Null until both have loaded, so the list
     * never flashes the default order.
     */
    val songs: SortedList<Track>? = null,
    /** The library as artists and albums, from the same scan as [songs]; null until they load. */
    val catalog: MusicCatalog? = null,
    /** The chosen chip: the stored one, then the user's picks. */
    val view: MusicView = MusicView.Songs,
    /** True once [songs] and the stored [view] have both loaded, so neither the chips nor the list jump. */
    val isReady: Boolean = false,
    /** The artist page shown in place of the Artists list; null shows the list. Not stored. */
    val openArtistKey: OpenArtist? = null,
    /** The Albums grid's order: the stored one, then the user's picks. */
    val albumSort: AlbumSort = AlbumSort.Title,
    /**
     * The album page shown in place of the grid, or of the artist page it was opened from; null
     * shows what is under it. Not stored.
     */
    val openAlbumKey: String? = null,
)

/** An open artist page. Wraps the key, because the unknown artist's key is null. */
data class OpenArtist(val key: String?)

class LibraryViewModel internal constructor(
    application: Application,
    private val repository: MusicRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val sortPrefs: SortPrefs = InMemorySortPrefs(),
    /** Sorting is CPU work; tests get the same inline dispatcher as [ioDispatcher]. */
    private val sortDispatcher: CoroutineDispatcher = ioDispatcher,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        MusicRepository(application),
        Dispatchers.IO,
        SortPrefs.get(application),
        Dispatchers.Default,
    )

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    /** The last load that worked; a new one sorts again even when nothing changed. */
    private val scan = MutableStateFlow<Scan?>(null)

    /** The sort asked for: the stored one, then the user's picks. Null until the store answers. */
    private val requestedSort = MutableStateFlow<SortSpec?>(null)

    /** Set once the store has answered with the view; nothing is published before. */
    private val viewLoaded = MutableStateFlow(false)

    /** The user picked a view while the store was still reading: the pick wins. */
    private var viewPicked = false

    /** The same for the album sort. */
    private var albumSortPicked = false

    init {
        viewModelScope.launch {
            val stored = sortPrefs.musicSort.first()
            // A pick made while the store was still reading wins.
            if (requestedSort.value == null) requestedSort.value = stored
        }
        viewModelScope.launch {
            val stored = sortPrefs.musicView.first()
            if (!viewPicked) _uiState.update { it.copy(view = stored) }
            val storedAlbumSort = sortPrefs.albumSort.first()
            if (!albumSortPicked) _uiState.update { it.copy(albumSort = storedAlbumSort) }
            viewLoaded.value = true
        }
        viewModelScope.launch {
            combine(scan.filterNotNull(), requestedSort.filterNotNull(), viewLoaded.filter { it }) { loaded, sort, _ -> loaded to sort }
                .collectLatest { (scan, sort) -> publish(scan, sort) }
        }
        viewModelScope.launch {
            repository.changes().collectLatest {
                // A delete or a multi-file copy fires a burst of changes; reload once it settles.
                delay(CHANGE_SETTLE_MS)
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = withContext(ioDispatcher) {
                runCatching { repository.loadTracks() }
            }
            _uiState.update { current ->
                result.fold(
                    onSuccess = { tracks ->
                        current.copy(isLoading = false, tracks = tracks, errorMessage = null, loaded = true)
                    },
                    onFailure = {
                        current.copy(
                            isLoading = false,
                            errorMessage = getApplication<Application>().getString(R.string.library_error),
                        )
                    },
                )
            }
            result.getOrNull()?.let { tracks -> scan.value = Scan(tracks) }
        }
    }

    /** The user picked [spec] in the Songs menu: the list re-sorts, and the pick is stored. */
    fun setSort(spec: SortSpec) {
        if (spec !in LibrarySorts.MusicOptions) return
        requestedSort.value = spec
        viewModelScope.launch {
            try {
                sortPrefs.setMusicSort(spec)
            } catch (_: IOException) {
                // Kept for this session; the next start falls back to the stored sort.
            }
        }
    }

    /** The user picked [view] from the chips: it shows, any open page closes, and the pick is stored. */
    fun setView(view: MusicView) {
        viewPicked = true
        _uiState.update { it.copy(view = view, openArtistKey = null, openAlbumKey = null) }
        viewModelScope.launch {
            try {
                sortPrefs.setMusicView(view)
            } catch (_: IOException) {
                // Kept for this session; the next start falls back to the stored view.
            }
        }
    }

    /** The user picked [sort] in the Albums menu: the grid reorders, and the pick is stored. */
    fun setAlbumSort(sort: AlbumSort) {
        albumSortPicked = true
        _uiState.update { it.copy(albumSort = sort) }
        viewModelScope.launch {
            try {
                sortPrefs.setAlbumSort(sort)
            } catch (_: IOException) {
                // Kept for this session; the next start falls back to the stored sort.
            }
        }
    }

    /** Opens the page of the artist with [key]; null is the unknown artist. */
    fun openArtist(key: String?) {
        _uiState.update { it.copy(openArtistKey = OpenArtist(key)) }
    }

    /** Opens the page of the album with [key], over the grid or over the open artist page. */
    fun openAlbum(key: String) {
        _uiState.update { it.copy(openAlbumKey = key) }
    }

    /** Closes the album page if one is open, so Back returns to the artist it came from; else the artist page. */
    fun closePage() {
        _uiState.update {
            if (it.openAlbumKey != null) it.copy(openAlbumKey = null) else it.copy(openArtistKey = null)
        }
    }

    private suspend fun publish(scan: Scan, sort: SortSpec) {
        val app = getApplication<Application>()
        val unknown = UnknownNames(
            title = app.getString(R.string.unknown_title),
            artist = app.getString(R.string.unknown_artist),
            album = app.getString(R.string.unknown_album),
        )
        // The catalog does not depend on the sort: it is built once per scan, beside the sort.
        val (sorted, catalog) = coroutineScope {
            val catalog = async(sortDispatcher) {
                ensureActive()
                scan.catalog ?: MusicCatalog.build(scan.tracks, unknown, Collator.getInstance()).also { scan.catalog = it }
            }
            val sorted = withContext(sortDispatcher) {
                LibrarySorter.tracks(scan.tracks, sort, Collator.getInstance(), ZoneId.systemDefault(), unknown)
            }
            // A newer scan cancels this one here, not after the catalog has also finished.
            ensureActive()
            sorted to catalog.await()
        }
        _uiState.update { current ->
            current.copy(
                songs = sorted,
                catalog = catalog,
                isReady = true,
                // A rescan without the open artist closes the page.
                openArtistKey = current.openArtistKey?.takeIf { catalog.hasArtist(it.key) },
                // The same for the open album; the artist page under it stays if its artist does.
                openAlbumKey = current.openAlbumKey?.takeIf(catalog::hasAlbum),
            )
        }
    }

    /** One load's list and the catalog built from it. Compared by identity, so every load sorts again. */
    private class Scan(val tracks: List<Track>) {
        @Volatile var catalog: MusicCatalog? = null
    }

    private companion object {
        const val CHANGE_SETTLE_MS = 500L
    }
}
