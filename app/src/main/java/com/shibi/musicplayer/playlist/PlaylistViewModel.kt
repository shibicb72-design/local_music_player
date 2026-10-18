package com.shibi.musicplayer.playlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shibi.musicplayer.diagnostics.ErrorLog
import com.shibi.musicplayer.domain.HistoryType
import com.shibi.musicplayer.domain.PlayHistoryTracker
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.video.VideoProgressRepository
import com.shibi.musicplayer.video.VideoUiState
import kotlinx.coroutines.Dispatchers
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

    val history: StateFlow<List<PlayHistoryEntity>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var trackingHistory = false

    /**
     * Records songs and videos into Recently Played once each item has played 5 s,
     * or reached its end if shorter.
     *
     * Root cause this fixes: Recently Played was written only by MusicApp.playQueue when a queue
     * was started from a list; songs reached by next, auto-advance or the notification, and every
     * video, were never recorded.
     */
    fun trackHistory(player: StateFlow<PlayerUiState>, video: StateFlow<VideoUiState>) {
        if (trackingHistory) return
        trackingHistory = true
        viewModelScope.launch {
            val tracker = PlayHistoryTracker()
            player.collect { state ->
                val id = state.currentMediaId?.toLongOrNull()
                val key = id?.let { "song:" + it }
                if (tracker.feed(key, state.positionMs, state.durationMs) != null && id != null) {
                    repository.recordHistory(HistoryType.Song, id, "", state.title)
                }
            }
        }
        viewModelScope.launch {
            val tracker = PlayHistoryTracker()
            video.collect { state ->
                val v = state.video
                val key = v?.let { "video:" + it.id }
                if (tracker.feed(key, state.positionMs, state.durationMs) != null && v != null) {
                    repository.recordHistory(HistoryType.Video, v.id, v.contentUri, v.title)
                }
            }
        }
    }

    private val trashCleanup by lazy {
        TrashCleanup(
            PlaylistDatabase.get(getApplication()).playlistDao(),
            VideoProgressRepository.get(getApplication()),
        )
    }

    /** Drops stored songs the library no longer has (deleted outside the app). Never throws. */
    fun pruneMissingSongs(ids: Set<Long>, loaded: Boolean) {
        if (!loaded) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { trashCleanup.pruneMissingSongs(ids, loaded) }
                .onFailure { logPruneFailure("Prune missing songs", it) }
        }
    }

    /** Drops stored video history and positions for videos the library no longer has. Never throws. */
    fun pruneMissingVideos(videos: List<Video>, loaded: Boolean) {
        if (!loaded) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                trashCleanup.pruneMissingVideos(
                    videos.mapTo(mutableSetOf()) { it.id },
                    videos.mapTo(mutableSetOf()) { it.contentUri },
                    loaded,
                )
            }.onFailure { logPruneFailure("Prune missing videos", it) }
        }
    }

    private fun logPruneFailure(step: String, error: Throwable) {
        ErrorLog.get(getApplication()).log(step = step, error = error, shown = null, media = null)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

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
