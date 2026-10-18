package com.shibi.musicplayer.playlist

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Where each video was left (schema 5). Keyed by the `content://` URI the player opens;
 * [durationMs] is libVLC's length (0 if it never reported one), kept after the video
 * finishes so rows whose MediaStore duration is missing can show it.
 */
@Entity(tableName = "video_progress")
data class VideoProgressEntity(
    @PrimaryKey val uri: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Dao
interface VideoProgressDao {
    @Query("SELECT * FROM video_progress")
    suspend fun getAll(): List<VideoProgressEntity>

    @Query("SELECT * FROM video_progress WHERE uri = :uri")
    suspend fun find(uri: String): VideoProgressEntity?

    @Upsert
    suspend fun upsert(entry: VideoProgressEntity)

    @Query("SELECT * FROM video_progress WHERE uri IN (:uris)")
    suspend fun findAll(uris: List<String>): List<VideoProgressEntity>

    @Query("DELETE FROM video_progress WHERE uri IN (:uris)")
    suspend fun deleteAll(uris: List<String>)

    @Upsert
    suspend fun upsertAll(rows: List<VideoProgressEntity>)
}
