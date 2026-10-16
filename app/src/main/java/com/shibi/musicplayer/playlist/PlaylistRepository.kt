package com.shibi.musicplayer.playlist

import com.shibi.musicplayer.domain.PlaylistOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlaylistRepository(
    private val dao: PlaylistDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val playlists: Flow<List<PlaylistSummary>> = dao.observeAll().map { rows ->
        rows.map { it.toSummary() }
    }

    val favoriteIds: Flow<Set<Long>> = dao.observeFavoriteIds().map { it.toSet() }

    val recentTrackIds: Flow<List<Long>> = dao.observeRecentIds()

    val recentSearches: Flow<List<String>> = dao.observeRecentSearches()

    val darkTheme: Flow<Boolean> =
        dao.observeSettings(AppSettingsEntity.SETTINGS_ID).map { it?.darkTheme ?: true }

    fun playlist(id: Long): Flow<PlaylistDetail?> = dao.observeAll().map { rows ->
        rows.firstOrNull { it.playlist.id == id }?.toDetail()
    }

    suspend fun create(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Playlist name is blank" }
        return dao.insertPlaylist(
            PlaylistEntity(name = trimmed, createdAt = now()),
        )
    }

    suspend fun rename(id: Long, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Playlist name is blank" }
        dao.rename(id, trimmed)
    }

    suspend fun delete(id: Long) {
        dao.deletePlaylist(id)
    }

    suspend fun addSong(playlistId: Long, trackId: Long) {
        if (dao.findSong(playlistId, trackId) != null) return
        val position = dao.maxPosition(playlistId) + 1
        dao.insertSong(
            PlaylistSongEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = position,
            ),
        )
    }

    suspend fun removeSong(playlistId: Long, trackId: Long) {
        dao.deleteSong(playlistId, trackId)
        val remaining = dao.songs(playlistId)
            .sortedBy { it.position }
            .map { it.trackId }
        dao.reorder(playlistId, remaining)
    }

    suspend fun moveSong(playlistId: Long, fromIndex: Int, toIndex: Int) {
        val current = dao.songs(playlistId)
            .sortedBy { it.position }
            .map { it.trackId }
        val next = PlaylistOrder.moved(current, fromIndex, toIndex)
        if (next != current) dao.reorder(playlistId, next)
    }

    suspend fun reorder(playlistId: Long, trackIds: List<Long>) {
        dao.reorder(playlistId, trackIds)
    }

    suspend fun setFavorite(trackId: Long, favorite: Boolean) {
        dao.upsertFavorite(FavoriteEntity(trackId = trackId, favorite = favorite))
    }

    suspend fun recordPlay(trackId: Long) {
        dao.deleteRecentForTrack(trackId)
        dao.insertRecent(RecentPlayEntity(trackId = trackId, playedAt = now()))
        dao.trimRecent(RECENT_LIMIT)
    }

    suspend fun setDarkTheme(dark: Boolean) {
        dao.upsertSettings(AppSettingsEntity(darkTheme = dark))
    }

    suspend fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        dao.upsertRecentSearch(RecentSearchEntity(query = trimmed, searchedAt = now()))
        dao.trimRecentSearches(SEARCH_LIMIT)
    }

    private fun PlaylistWithSongs.toSummary(): PlaylistSummary {
        val ordered = songs.sortedBy { it.position }
        return PlaylistSummary(
            id = playlist.id,
            name = playlist.name,
            songCount = ordered.size,
            coverTrackIds = ordered.take(COVER_LIMIT).map { it.trackId },
        )
    }

    private fun PlaylistWithSongs.toDetail(): PlaylistDetail {
        val ordered = songs.sortedBy { it.position }
        return PlaylistDetail(
            id = playlist.id,
            name = playlist.name,
            trackIds = ordered.map { it.trackId },
        )
    }

    private companion object {
        const val COVER_LIMIT = 4
        const val RECENT_LIMIT = 50
        const val SEARCH_LIMIT = 8
    }
}
