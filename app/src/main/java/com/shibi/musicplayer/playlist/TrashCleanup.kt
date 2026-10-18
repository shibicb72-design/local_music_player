package com.shibi.musicplayer.playlist

import com.shibi.musicplayer.domain.HistoryType
import com.shibi.musicplayer.domain.MediaKind
import com.shibi.musicplayer.domain.TrashItem
import com.shibi.musicplayer.domain.TrashPlan

/** The saved video positions, as far as trash cleanup needs them. */
interface VideoProgressStore {
    /** Snapshots the rows for [uris], deletes them and drops them from memory; ordered after pending writes. */
    suspend fun removeFor(uris: Collection<String>): List<VideoProgressEntity>

    /** Writes [rows] back and returns them to memory. */
    suspend fun restore(rows: List<VideoProgressEntity>)

    /** Every URI that has a saved position. */
    fun savedUris(): Set<String>
}

/** What [TrashCleanup.remove] took out, so Undo can put it back. */
data class CleanupSnapshot(
    val playlistSongs: List<PlaylistSongEntity> = emptyList(),
    val recentPlays: List<RecentPlayEntity> = emptyList(),
    val history: List<PlayHistoryEntity> = emptyList(),
    val videoProgress: List<VideoProgressEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = playlistSongs.isEmpty() && recentPlays.isEmpty() && history.isEmpty() && videoProgress.isEmpty()
}

/**
 * Removes trashed (or externally deleted) media from playlists, Recently Played, play history
 * and saved video positions. Favorites and like counts are left alone. No schema change.
 */
class TrashCleanup(
    private val dao: PlaylistDao,
    private val progress: VideoProgressStore,
) {
    suspend fun remove(items: List<TrashItem>): CleanupSnapshot {
        val songIds = items.filter { it.kind == MediaKind.Song }.map { it.id }.distinct()
        val videoIds = items.filter { it.kind == MediaKind.Video }.map { it.id }.distinct()
        val videoUris = items.filter { it.kind == MediaKind.Video }.map { it.uri }.distinct()

        val playlistSongs = songIds.chunked(CHUNK).flatMap { dao.songsForTracks(it) }
        val recent = songIds.chunked(CHUNK).flatMap { dao.recentPlaysFor(it) }
        val songHistory = songIds.chunked(CHUNK).flatMap { dao.historyFor(HistoryType.Song.name, it) }
        val videoHistory = videoIds.chunked(CHUNK).flatMap { dao.historyFor(HistoryType.Video.name, it) }

        songIds.chunked(CHUNK).forEach {
            dao.deleteSongsForTracks(it)
            dao.deleteRecentForTracks(it)
            dao.deleteHistoryFor(HistoryType.Song.name, it)
        }
        videoIds.chunked(CHUNK).forEach { dao.deleteHistoryFor(HistoryType.Video.name, it) }
        val removedProgress = progress.removeFor(videoUris)

        return CleanupSnapshot(
            playlistSongs = playlistSongs,
            recentPlays = recent,
            history = songHistory + videoHistory,
            videoProgress = removedProgress,
        )
    }

    suspend fun restore(snapshot: CleanupSnapshot) {
        snapshot.playlistSongs.chunked(CHUNK).forEach { dao.insertSongs(it) }
        snapshot.recentPlays.chunked(CHUNK).forEach { dao.insertRecentPlays(it) }
        snapshot.history.chunked(CHUNK).forEach { dao.insertHistoryRows(it) }
        progress.restore(snapshot.videoProgress)
    }

    suspend fun pruneMissingSongs(librarySongIds: Set<Long>, libraryLoaded: Boolean): Int {
        val stored = dao.allPlaylistTrackIds().toSet() + dao.historyMediaIds(HistoryType.Song.name)
        val missing = TrashPlan.missingFromLibrary(stored, librarySongIds, libraryLoaded).toList()
        if (missing.isEmpty()) return 0
        missing.chunked(CHUNK).forEach {
            dao.deleteSongsForTracks(it)
            dao.deleteRecentForTracks(it)
            dao.deleteHistoryFor(HistoryType.Song.name, it)
        }
        return missing.size
    }

    suspend fun pruneMissingVideos(
        libraryVideoIds: Set<Long>,
        libraryVideoUris: Set<String>,
        libraryLoaded: Boolean,
    ): Int {
        var pruned = 0
        val missingHistory = TrashPlan.missingFromLibrary(
            dao.historyMediaIds(HistoryType.Video.name).toSet(),
            libraryVideoIds,
            libraryLoaded,
        ).toList()
        missingHistory.chunked(CHUNK).forEach { dao.deleteHistoryFor(HistoryType.Video.name, it) }
        pruned += missingHistory.size

        if (libraryLoaded && libraryVideoUris.isNotEmpty()) {
            val missingUris = progress.savedUris()
                .filter { it.startsWith(MEDIASTORE_PREFIX) && it !in libraryVideoUris }
            if (missingUris.isNotEmpty() && missingUris.size <= TrashPlan.MAX_EXTERNAL_PRUNE) {
                progress.removeFor(missingUris)
                pruned += missingUris.size
            }
        }
        return pruned
    }

    private companion object {
        const val CHUNK = 500
        const val MEDIASTORE_PREFIX = "content://media/"
    }
}
