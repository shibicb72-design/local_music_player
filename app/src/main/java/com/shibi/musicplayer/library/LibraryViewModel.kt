package com.shibi.musicplayer.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** True once a load has succeeded; a pruning step must not trust an empty list before that. */
    val loaded: Boolean = false,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository(application)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
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
            val result = withContext(Dispatchers.IO) {
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
        }
    }

    private companion object {
        const val CHANGE_SETTLE_MS = 500L
    }
}
