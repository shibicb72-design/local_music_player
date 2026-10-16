package com.shibi.musicplayer.playlist

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        FavoriteEntity::class,
        RecentPlayEntity::class,
        RecentSearchEntity::class,
        AppSettingsEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class PlaylistDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var instance: PlaylistDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` (" +
                        "`trackId` INTEGER NOT NULL, " +
                        "`favorite` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`trackId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recent_plays` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`trackId` INTEGER NOT NULL, " +
                        "`playedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_settings` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`darkTheme` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL("INSERT INTO app_settings (id, darkTheme) VALUES (1, 1)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recent_searches` (" +
                        "`query` TEXT NOT NULL, " +
                        "`searchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`query`))",
                )
            }
        }

        fun get(context: Context): PlaylistDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlaylistDatabase::class.java,
                    "playlists.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
