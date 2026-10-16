package com.shibi.musicplayer.playlist

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val songCount: Int,
    val coverTrackIds: List<Long>,
)

data class PlaylistDetail(
    val id: Long,
    val name: String,
    val trackIds: List<Long>,
)

data class PlaylistsUiState(
    val playlists: List<PlaylistSummary> = emptyList(),
    val loaded: Boolean = false,
)
