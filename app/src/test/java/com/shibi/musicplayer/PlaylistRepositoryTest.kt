package com.shibi.musicplayer

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.playlist.PlaylistDatabase
import com.shibi.musicplayer.playlist.PlaylistRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlaylistRepositoryTest {
    private lateinit var database: PlaylistDatabase
    private lateinit var repository: PlaylistRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, PlaylistDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaylistRepository(database.playlistDao()) { now }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createTrimsTheNameAndListsPlaylistsInCreationOrder() = runBlocking {
        val first = repository.create("  Late night  ")
        now += 10
        val second = repository.create("Focus")
        val playlists = repository.playlists.first()

        assertEquals(listOf(first, second), playlists.map { it.id })
        assertEquals(listOf("Late night", "Focus"), playlists.map { it.name })
        assertEquals(listOf(0, 0), playlists.map { it.songCount })
    }

    @Test
    fun createAndRenameRejectABlankName() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create("   ") }
        }
        val id = runBlocking { repository.create("Keep") }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.rename(id, " ") }
        }
    }

    @Test
    fun renameUpdatesTheStoredName() = runBlocking {
        val id = repository.create("Old")
        repository.rename(id, "  Night drive ")
        assertEquals("Night drive", repository.playlist(id).first()?.name)
    }

    @Test
    fun addSongAppendsAndIgnoresADuplicate() = runBlocking {
        val id = repository.create("Mix")
        repository.addSong(id, 10)
        repository.addSong(id, 20)
        repository.addSong(id, 10)

        val detail = repository.playlist(id).first()
        assertEquals(listOf(10L, 20L), detail?.trackIds)
        assertEquals(listOf(0, 1), database.playlistDao().songs(id).map { it.position })
    }

    @Test
    fun removeSongClosesThePositionGap() = runBlocking {
        val id = repository.create("Mix")
        repository.addSong(id, 10)
        repository.addSong(id, 20)
        repository.addSong(id, 30)
        repository.removeSong(id, 20)

        assertEquals(listOf(10L, 30L), repository.playlist(id).first()?.trackIds)
        assertEquals(listOf(0, 1), database.playlistDao().songs(id).sortedBy { it.position }.map { it.position })
    }

    @Test
    fun moveSongPersistsTheNewOrder() = runBlocking {
        val id = repository.create("Mix")
        repository.addSong(id, 10)
        repository.addSong(id, 20)
        repository.addSong(id, 30)
        repository.moveSong(id, fromIndex = 0, toIndex = 2)

        assertEquals(listOf(20L, 30L, 10L), repository.playlist(id).first()?.trackIds)
        val rows = database.playlistDao().songs(id).sortedBy { it.position }
        assertEquals(listOf(20L, 30L, 10L), rows.map { it.trackId })
        assertEquals(listOf(0, 1, 2), rows.map { it.position })
    }

    @Test
    fun moveSongOutsideTheListLeavesTheOrderAlone() = runBlocking {
        val id = repository.create("Mix")
        repository.addSong(id, 10)
        repository.addSong(id, 20)
        repository.moveSong(id, fromIndex = 0, toIndex = 0)
        repository.moveSong(id, fromIndex = -1, toIndex = 1)
        repository.moveSong(id, fromIndex = 0, toIndex = 4)

        assertEquals(listOf(10L, 20L), repository.playlist(id).first()?.trackIds)
    }

    @Test
    fun reorderWritesTheGivenTrackOrder() = runBlocking {
        val id = repository.create("Mix")
        repository.addSong(id, 10)
        repository.addSong(id, 20)
        repository.addSong(id, 30)
        repository.reorder(id, listOf(30, 10, 20))

        assertEquals(listOf(30L, 10L, 20L), repository.playlist(id).first()?.trackIds)
    }

    @Test
    fun deleteRemovesThePlaylistAndItsSongs() = runBlocking {
        val id = repository.create("Mix")
        repository.addSong(id, 10)
        repository.addSong(id, 20)
        repository.delete(id)

        assertNull(repository.playlist(id).first())
        assertTrue(database.playlistDao().songs(id).isEmpty())
        assertTrue(repository.playlists.first().isEmpty())
    }

    @Test
    fun summaryKeepsTheFirstFourTracksForTheCollage() = runBlocking {
        val id = repository.create("Mix")
        listOf(10L, 20L, 30L, 40L, 50L).forEach { repository.addSong(id, it) }
        val summary = repository.playlists.first().single()

        assertEquals(5, summary.songCount)
        assertEquals(listOf(10L, 20L, 30L, 40L), summary.coverTrackIds)
    }

    @Test
    fun favoriteIsABooleanThatCanBeTurnedOff() = runBlocking {
        assertTrue(repository.favoriteIds.first().isEmpty())
        repository.setFavorite(8L, true)
        assertEquals(setOf(8L), repository.favoriteIds.first())
        repository.setFavorite(8L, false)
        assertFalse(repository.favoriteIds.first().contains(8L))
    }

    @Test
    fun recentPlaysKeepsTheNewestFiftyDistinctSongs() = runBlocking {
        repeat(55) { index ->
            now = 1_000L + index
            repository.recordPlay(index.toLong())
        }
        val ids = repository.recentTrackIds.first()
        assertEquals(50, ids.size)
        assertEquals(54L, ids.first())
        assertEquals(5L, ids.last())

        now += 1
        repository.recordPlay(10L)
        val bumped = repository.recentTrackIds.first()
        assertEquals(50, bumped.size)
        assertEquals(10L, bumped.first())
        assertEquals(1, bumped.count { it == 10L })
    }

    @Test
    fun recentSearchesKeepsEightNewestAndIgnoresBlank() = runBlocking {
        repository.recordSearch("   ")
        assertTrue(repository.recentSearches.first().isEmpty())
        repeat(10) { index ->
            now = 2_000L + index
            repository.recordSearch("q$index")
        }
        val queries = repository.recentSearches.first()
        assertEquals(8, queries.size)
        assertEquals("q9", queries.first())
        assertEquals("q2", queries.last())

        now += 1
        repository.recordSearch("q4")
        val bumped = repository.recentSearches.first()
        assertEquals(8, bumped.size)
        assertEquals("q4", bumped.first())
        assertEquals(1, bumped.count { it == "q4" })
    }

    @Test
    fun darkThemeDefaultsOnAndCanBeSwitched() = runBlocking {
        assertTrue(repository.darkTheme.first())
        repository.setDarkTheme(false)
        assertFalse(repository.darkTheme.first())
    }
}
