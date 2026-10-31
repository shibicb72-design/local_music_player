package com.shibi.musicplayer.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.LibrarySorter
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.SortedList
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.UnknownNames
import java.io.IOException
import java.text.Collator
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
)

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

    init {
        viewModelScope.launch {
            val stored = sortPrefs.musicSort.first()
            // A pick made while the store was still reading wins.
            if (requestedSort.value == null) requestedSort.value = stored
        }
        viewModelScope.launch {
            combine(scan.filterNotNull(), requestedSort.filterNotNull(), ::Pair)
                .collectLatest { (scan, sort) -> publish(scan.tracks, sort) }
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

    private suspend fun publish(tracks: List<Track>, sort: SortSpec) {
        val app = getApplication<Application>()
        val unknown = UnknownNames(
            title = app.getString(R.string.unknown_title),
            artist = app.getString(R.string.unknown_artist),
            album = app.getString(R.string.unknown_album),
        )
        val sorted = withContext(sortDispatcher) {
            LibrarySorter.tracks(tracks, sort, Collator.getInstance(), ZoneId.systemDefault(), unknown)
        }
        _uiState.update { it.copy(songs = sorted) }
    }

    /** One load's list. Compared by identity, so every load sorts again. */
    private class Scan(val tracks: List<Track>)

    private companion object {
        const val CHANGE_SETTLE_MS = 500L
    }
}
