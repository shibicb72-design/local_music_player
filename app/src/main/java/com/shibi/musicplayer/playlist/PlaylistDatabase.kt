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
        VideoProgressEntity::class,
        PlayHistoryEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class PlaylistDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun videoProgressDao(): VideoProgressDao

    companion object {
        @Volatile
        private var instance: PlaylistDatabase? = null

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
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

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recent_searches` (" +
                        "`query` TEXT NOT NULL, " +
                        "`searchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`query`))",
                )
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `favorites` ADD COLUMN `likedAt` INTEGER")
                db.execSQL(
                    "ALTER TABLE `favorites` ADD COLUMN `playCount` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** V9: video resume positions. Only a new table; nothing existing is touched. */
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `video_progress` (" +
                        "`uri` TEXT NOT NULL, " +
                        "`positionMs` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`uri`))",
                )
            }
        }

        /** V16: play history; the old recent_plays rows are copied over as songs. */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`mediaId` INTEGER NOT NULL, " +
                        "`uri` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`playedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_play_history_playedAt` " +
                        "ON `play_history` (`playedAt`)",
                )
                db.execSQL(
                    "INSERT INTO play_history (type, mediaId, uri, title, playedAt) " +
                        "SELECT 'Song', trackId, '', '', playedAt FROM recent_plays",
                )
            }
        }

        fun get(context: Context): PlaylistDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlaylistDatabase::class.java,
                    "playlists.db",
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
