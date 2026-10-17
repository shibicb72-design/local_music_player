package com.shibi.musicplayer.playback

import com.shibi.musicplayer.domain.RepeatMode
import com.shibi.musicplayer.domain.ShuffleContext

data class PlayerUiState(
    val currentMediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasMedia: Boolean = false,
    val isConnected: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val shuffleContext: ShuffleContext = ShuffleContext.ALL_SONGS,
    val repeatMode: RepeatMode = RepeatMode.OFF,
)
