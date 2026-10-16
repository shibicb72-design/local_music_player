package com.shibi.musicplayer.playlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlaylistRepository(
        PlaylistDatabase.get(application).playlistDao(),
    )

    val state: StateFlow<PlaylistsUiState> = repository.playlists
        .map { PlaylistsUiState(playlists = it, loaded = true) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PlaylistsUiState(),
        )

    val favoriteIds: StateFlow<Set<Long>> = repository.favoriteIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val recentTrackIds: StateFlow<List<Long>> = repository.recentTrackIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentSearches: StateFlow<List<String>> = repository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val darkTheme: StateFlow<Boolean> = repository.darkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun playlist(id: Long): Flow<PlaylistDetail?> = repository.playlist(id)

    fun create(name: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = runCatching { repository.create(name) }.getOrNull() ?: return@launch
            onCreated(id)
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch {
            runCatching { repository.rename(id, name) }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun addSong(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.addSong(playlistId, trackId)
        }
    }

    fun removeSong(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.removeSong(playlistId, trackId)
        }
    }

    fun reorder(playlistId: Long, trackIds: List<Long>) {
        viewModelScope.launch {
            repository.reorder(playlistId, trackIds)
        }
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            val favorite = trackId in favoriteIds.value
            repository.setFavorite(trackId, !favorite)
        }
    }

    fun recordPlay(trackId: Long) {
        viewModelScope.launch {
            repository.recordPlay(trackId)
        }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch {
            repository.setDarkTheme(dark)
        }
    }

    fun recordSearch(query: String) {
        viewModelScope.launch {
            repository.recordSearch(query)
        }
    }
}
