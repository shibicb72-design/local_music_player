package com.shibi.musicplayer.playback

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
)
