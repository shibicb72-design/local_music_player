package com.shibi.musicplayer

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.playlist.PlaylistDatabase
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
