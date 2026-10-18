package com.shibi.musicplayer

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.playlist.PlaylistDatabase
import com.shibi.musicplayer.playlist.VideoProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlaylistMigrationTest {
    private val name = "playlists-migration.db"
    private var database: PlaylistDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        RuntimeEnvironment.getApplication().deleteDatabase(name)
    }

    @Test
    fun migrationFrom3KeepsFavoritesAndSongs() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createVersion3(db)

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )
        helper.writableDatabase.apply {
            execSQL("INSERT INTO playlists (id, name, createdAt) VALUES (1, 'Late night', 10)")
            execSQL(
                "INSERT INTO playlist_songs (playlistId, trackId, position) VALUES (1, 8, 0)",
            )
            execSQL("INSERT INTO favorites (trackId, favorite) VALUES (8, 1)")
            execSQL("INSERT INTO app_settings (id, darkTheme) VALUES (1, 1)")
        }
        helper.close()

        val opened = Room.databaseBuilder(context, PlaylistDatabase::class.java, name)
            .addMigrations(
                PlaylistDatabase.MIGRATION_1_2,
                PlaylistDatabase.MIGRATION_2_3,
                PlaylistDatabase.MIGRATION_3_4,
                PlaylistDatabase.MIGRATION_4_5,
                PlaylistDatabase.MIGRATION_5_6,
            )
            .allowMainThreadQueries()
            .build()
        database = opened

        val favorite = opened.playlistDao().findFavorite(8L)
        assertTrue(favorite?.favorite == true)
        assertNull(favorite?.likedAt)
        assertEquals(0, favorite?.playCount)
        val playlist = opened.playlistDao().observeAll().first().single()
        assertEquals("Late night", playlist.playlist.name)
        assertEquals(8L, playlist.songs.single().trackId)
    }

    @Test
    fun migrationFrom4AddsVideoProgressAndKeepsEverything() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion3(db)
                        PlaylistDatabase.MIGRATION_3_4.migrate(db)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )
        helper.writableDatabase.apply {
            execSQL("INSERT INTO playlists (id, name, createdAt) VALUES (1, 'Late night', 10)")
            execSQL(
                "INSERT INTO playlist_songs (playlistId, trackId, position) VALUES (1, 8, 0)",
            )
            execSQL("INSERT INTO favorites (trackId, favorite, likedAt, playCount) VALUES (8, 1, 77, 5)")
            execSQL("INSERT INTO recent_searches (`query`, searchedAt) VALUES ('rain', 3)")
            execSQL("INSERT INTO app_settings (id, darkTheme) VALUES (1, 0)")
        }
        helper.close()

        // Only the newer steps are given: Room must not need the older ones or a destructive fallback.
        val opened = Room.databaseBuilder(context, PlaylistDatabase::class.java, name)
            .addMigrations(PlaylistDatabase.MIGRATION_4_5, PlaylistDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        database = opened

        val favorite = opened.playlistDao().findFavorite(8L)
        assertEquals(77L, favorite?.likedAt)
        assertEquals(5, favorite?.playCount)
        val playlist = opened.playlistDao().observeAll().first().single()
        assertEquals("Late night", playlist.playlist.name)
        assertEquals(8L, playlist.songs.single().trackId)

        // The new table starts empty and takes rows (Room validated its schema on open).
        val progress = opened.videoProgressDao()
        assertTrue(progress.getAll().isEmpty())
        val entry = VideoProgressEntity("content://media/external/video/media/3", 754_000L, 3_600_000L, 9L)
        progress.upsert(entry)
        progress.upsert(entry.copy(positionMs = 760_000L, updatedAt = 10L))
        assertEquals(listOf(entry.copy(positionMs = 760_000L, updatedAt = 10L)), progress.getAll())
    }

    @Test
    fun migrationFrom5AddsPlayHistoryAndCopiesRecentPlays() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createVersion3(db)
                        PlaylistDatabase.MIGRATION_3_4.migrate(db)
                        PlaylistDatabase.MIGRATION_4_5.migrate(db)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build(),
        )
        helper.writableDatabase.apply {
            execSQL("INSERT INTO recent_plays (trackId, playedAt) VALUES (8, 100)")
            execSQL("INSERT INTO recent_plays (trackId, playedAt) VALUES (3, 200)")
        }
        helper.close()

        // Only 5 -> 6 is given: Room must not need the older steps or a destructive fallback.
        val opened = Room.databaseBuilder(context, PlaylistDatabase::class.java, name)
            .addMigrations(PlaylistDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        database = opened

        val history = opened.playlistDao().observeHistory().first()
        assertEquals(listOf(3L, 8L), history.map { it.mediaId })
        assertEquals(listOf("Song", "Song"), history.map { it.type })
        assertEquals(listOf(200L, 100L), history.map { it.playedAt })
        assertTrue(history.all { it.uri.isEmpty() && it.title.isEmpty() })
    }

    private fun createVersion3(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_songs` (" +
                "`playlistId` INTEGER NOT NULL, " +
                "`trackId` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`playlistId`, `trackId`), " +
                "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlistId` " +
                "ON `playlist_songs` (`playlistId`)",
        )
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
            "CREATE TABLE IF NOT EXISTS `recent_searches` (" +
                "`query` TEXT NOT NULL, " +
                "`searchedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`query`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_settings` (" +
                "`id` INTEGER NOT NULL, " +
                "`darkTheme` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}
