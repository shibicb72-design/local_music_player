package com.shibi.musicplayer.playlist

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: Long,
    val favorite: Boolean,
)

@Entity(tableName = "recent_plays")
data class RecentPlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAt: Long,
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
