package com.shibi.musicplayer.playlist

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class PlaylistWithSongs(
    @Embedded val playlist: PlaylistEntity,
    @Relation(parentColumn = "id", entityColumn = "playlistId")
    val songs: List<PlaylistSongEntity>,
)

@Dao
interface PlaylistDao {
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<PlaylistWithSongs>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteSongs(playlistId: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistRow(id: Long)

    @Query(
        "SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId",
    )
    suspend fun maxPosition(playlistId: Long): Int

    @Query(
        "SELECT * FROM playlist_songs WHERE playlistId = :playlistId AND trackId = :trackId LIMIT 1",
    )
    suspend fun findSong(playlistId: Long, trackId: Long): PlaylistSongEntity?

    @Insert
    suspend fun insertSong(song: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteSong(playlistId: Long, trackId: Long)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun songs(playlistId: Long): List<PlaylistSongEntity>

    @Query(
        "UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId",
    )
    suspend fun updatePosition(playlistId: Long, trackId: Long, position: Int)

    @Transaction
    suspend fun deletePlaylist(id: Long) {
        deleteSongs(id)
        deletePlaylistRow(id)
    }

    @Transaction
    suspend fun reorder(playlistId: Long, trackIds: List<Long>) {
        trackIds.forEachIndexed { index, trackId ->
            updatePosition(playlistId, trackId, index)
        }
    }
}
