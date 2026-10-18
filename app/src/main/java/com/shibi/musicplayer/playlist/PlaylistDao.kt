package com.shibi.musicplayer.playlist

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Query("SELECT trackId FROM favorites WHERE favorite = 1")
    fun observeFavoriteIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Query("SELECT * FROM favorites WHERE trackId = :trackId LIMIT 1")
    suspend fun findFavorite(trackId: Long): FavoriteEntity?

    @Query("UPDATE favorites SET playCount = playCount + 1 WHERE trackId = :trackId")
    suspend fun incrementPlayCount(trackId: Long): Int

    @Query("SELECT trackId FROM recent_plays ORDER BY playedAt DESC, id DESC")
    fun observeRecentIds(): Flow<List<Long>>

    @Query("DELETE FROM recent_plays WHERE trackId = :trackId")
    suspend fun deleteRecentForTrack(trackId: Long)

    @Insert
    suspend fun insertRecent(play: RecentPlayEntity)

    @Query(
        "DELETE FROM recent_plays WHERE id NOT IN " +
            "(SELECT id FROM recent_plays ORDER BY playedAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun trimRecent(keep: Int)

    @Query("SELECT * FROM app_settings WHERE id = :id")
    fun observeSettings(id: Int): Flow<AppSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: AppSettingsEntity)

    @Query("SELECT `query` FROM recent_searches ORDER BY searchedAt DESC")
    fun observeRecentSearches(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentSearch(search: RecentSearchEntity)

    @Query(
        "DELETE FROM recent_searches WHERE `query` NOT IN " +
            "(SELECT `query` FROM recent_searches ORDER BY searchedAt DESC LIMIT :keep)",
    )
    suspend fun trimRecentSearches(keep: Int)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC, id DESC LIMIT 500")
    fun observeHistory(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC, id DESC LIMIT 1")
    suspend fun latestHistory(): PlayHistoryEntity?

    @Insert
    suspend fun insertHistory(e: PlayHistoryEntity)

    @Query("UPDATE play_history SET playedAt = :playedAt WHERE id = :id")
    suspend fun touchHistory(id: Long, playedAt: Long)

    @Query(
        "DELETE FROM play_history WHERE id NOT IN " +
            "(SELECT id FROM play_history ORDER BY playedAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun trimHistory(keep: Int)

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()
}
