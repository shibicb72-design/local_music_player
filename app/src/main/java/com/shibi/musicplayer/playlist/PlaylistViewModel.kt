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
}
