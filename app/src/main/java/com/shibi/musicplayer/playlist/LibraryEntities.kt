package com.shibi.musicplayer.playlist

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per song the listener has liked or played. [likedAt] and [playCount]
 * are stored for a future like-weighted shuffle; that shuffle is not implemented.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: Long,
    val favorite: Boolean,
    val likedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val playCount: Int = 0,
)

@Entity(tableName = "recent_plays")
data class RecentPlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAt: Long,
)

/** One entry per playthrough of a song or video (V16): played 5 s or to its end; consecutive replays update playedAt. */
@Entity(tableName = "play_history", indices = [Index("playedAt")])
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val mediaId: Long,
    val uri: String,
    val title: String,
    val playedAt: Long,
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = SETTINGS_ID,
    val darkTheme: Boolean,
) {
    companion object {
        const val SETTINGS_ID = 1
    }
}
