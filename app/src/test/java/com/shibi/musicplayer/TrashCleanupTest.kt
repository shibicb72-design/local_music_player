package com.shibi.musicplayer

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.diagnostics.ErrorLogger
import com.shibi.musicplayer.domain.HistoryType
import com.shibi.musicplayer.domain.MediaKind
import com.shibi.musicplayer.domain.TrashItem
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.playlist.FavoriteEntity
import com.shibi.musicplayer.playlist.PlayHistoryEntity
import com.shibi.musicplayer.playlist.PlaylistDao
import com.shibi.musicplayer.playlist.PlaylistDatabase
import com.shibi.musicplayer.playlist.PlaylistEntity
import com.shibi.musicplayer.playlist.PlaylistSongEntity
import com.shibi.musicplayer.playlist.RecentPlayEntity
import com.shibi.musicplayer.playlist.TrashCleanup
import com.shibi.musicplayer.video.VideoProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TrashCleanupTest {
    private lateinit var database: PlaylistDatabase
    private lateinit var dao: PlaylistDao
    private lateinit var progress: VideoProgressRepository
    private lateinit var cleanup: TrashCleanup
    private val logger = ErrorLogger { _, _, _, _ -> }

    private val videoUri = "content://media/external/video/media/7"
    private val otherVideoUri = "content://media/external/video/media/8"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PlaylistDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.playlistDao()
        progress = VideoProgressRepository(database.videoProgressDao(), logger)
        cleanup = TrashCleanup(dao, progress)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun song(id: Long) = TrashItem(MediaKind.Song, id, "content://media/external/audio/media/$id", "s$id")
    private fun video(id: Long, uri: String) = TrashItem(MediaKind.Video, id, uri, "v$id")

    private suspend fun seed() {
        val a = dao.insertPlaylist(PlaylistEntity(name = "A", createdAt = 1))
        val b = dao.insertPlaylist(PlaylistEntity(name = "B", createdAt = 2))
        dao.insertSongs(
            listOf(
                PlaylistSongEntity(a, 1, 0),
                PlaylistSongEntity(a, 2, 1),
                PlaylistSongEntity(a, 3, 2),
                PlaylistSongEntity(b, 2, 0),
            ),
        )
        dao.insertRecent(RecentPlayEntity(trackId = 2, playedAt = 10))
        dao.insertRecent(RecentPlayEntity(trackId = 3, playedAt = 11))
        dao.insertHistory(PlayHistoryEntity(type = "Song", mediaId = 2, uri = "", title = "s2", playedAt = 10))
        dao.insertHistory(PlayHistoryEntity(type = "Video", mediaId = 7, uri = videoUri, title = "v7", playedAt = 12))
        dao.insertHistory(PlayHistoryEntity(type = "Video", mediaId = 2, uri = "", title = "other", playedAt = 13))
        dao.upsertFavorite(FavoriteEntity(trackId = 2, favorite = true, likedAt = 5, playCount = 3))
        progress.onCheckpoint(videoEntity(7, videoUri), 5_000L, 100_000L)
        progress.onCheckpoint(videoEntity(8, otherVideoUri), 6_000L, 100_000L)
        progress.awaitWrites()
    }

    private fun videoEntity(id: Long, uri: String) = com.shibi.musicplayer.domain.Video(
        id = id,
        title = "v$id",
        displayName = "v$id.mp4",
        durationMs = 100_000L,
        contentUri = uri,
        mimeType = "video/mp4",
        sizeBytes = 1L,
        width = 1,
        height = 1,
        dateAddedSeconds = 1L,
        folder = null,
    )

    private suspend fun playlistRows() = (1L..2L).flatMap { dao.songs(it) }.sortedWith(compareBy({ it.playlistId }, { it.position }))

    @Test
    fun removeThenRestoreRoundTripsEverything() = runBlocking {
        seed()
        val before = playlistRows()
        val recentBefore = dao.recentPlaysFor(listOf(2, 3))
        val historyBefore = dao.historyFor("Song", listOf(2)) + dao.historyFor("Video", listOf(7, 2))

        val snapshot = cleanup.remove(listOf(song(2), video(7, videoUri)))

        assertTrue(!snapshot.isEmpty)
        assertEquals(listOf(1L, 3L), playlistRows().map { it.trackId })
        assertEquals(listOf(3L), dao.recentPlaysFor(listOf(2, 3)).map { it.trackId })
        assertTrue(dao.historyFor("Song", listOf(2)).isEmpty())
        assertTrue(dao.historyFor("Video", listOf(7)).isEmpty())
        assertEquals(1, dao.historyFor("Video", listOf(2)).size)
        assertEquals(setOf(otherVideoUri), progress.savedUris())
        assertTrue(database.videoProgressDao().find(videoUri) == null)

        cleanup.restore(snapshot)

        assertEquals(before, playlistRows())
        assertEquals(recentBefore.toSet(), dao.recentPlaysFor(listOf(2, 3)).toSet())
        assertEquals(historyBefore.toSet(), (dao.historyFor("Song", listOf(2)) + dao.historyFor("Video", listOf(7, 2))).toSet())
        assertEquals(VideoProgress(5_000L, 100_000L), progress.progress.value[videoUri])
        assertEquals(5_000L, database.videoProgressDao().find(videoUri)?.positionMs)
    }

    @Test
    fun snapshotReadsWhatRemoveWouldTakeAndChangesNothing() = runBlocking {
        seed()
        val items = listOf(song(2), video(7, videoUri))
        val before = playlistRows()

        val snap = cleanup.snapshot(items)

        assertEquals(before, playlistRows())
        assertEquals(setOf(videoUri, otherVideoUri), progress.savedUris())
        assertEquals(2, snap.playlistSongs.size)
        assertEquals(listOf(2L), snap.recentPlays.map { it.trackId })
        assertEquals(2, snap.history.size)
        assertEquals(listOf(videoUri), snap.videoProgress.map { it.uri })
        assertEquals(snap, cleanup.remove(items))
        assertEquals(snap, snap + snap)
    }

    @Test
    fun aSongInTwoPlaylistsIsRemovedFromBothAndKeepsItsPositions() = runBlocking {
        seed()
        val snapshot = cleanup.remove(listOf(song(2)))
        assertEquals(2, snapshot.playlistSongs.size)
        assertEquals(listOf(1L, 3L), playlistRows().map { it.trackId })
        cleanup.restore(snapshot)
        assertEquals(1, dao.songs(1).first { it.trackId == 2L }.position)
        assertEquals(0, dao.songs(2).first { it.trackId == 2L }.position)
    }

    @Test
    fun favoritesAreNotTouched() = runBlocking {
        seed()
        cleanup.remove(listOf(song(2)))
        assertEquals(FavoriteEntity(2, true, 5, 3), dao.findFavorite(2))
    }

    @Test
    fun restoreAfterTheSongWasReAddedKeepsOneRow() = runBlocking {
        seed()
        val snapshot = cleanup.remove(listOf(song(2)))
        dao.insertSongs(listOf(PlaylistSongEntity(1, 2, 5)))
        cleanup.restore(snapshot)
        assertEquals(1, dao.songs(1).count { it.trackId == 2L })
    }

    @Test
    fun removingNothingGivesAnEmptySnapshot() = runBlocking {
        seed()
        assertTrue(cleanup.remove(listOf(song(99))).isEmpty)
    }

    @Test
    fun pruneMissingSongsRemovesOnlyWhatTheLoadedLibraryLacks() = runBlocking {
        seed()
        assertEquals(1, cleanup.pruneMissingSongs(setOf(1L, 3L, 100L), libraryLoaded = true))
        assertEquals(listOf(1L, 3L), playlistRows().map { it.trackId })
        assertTrue(dao.recentPlaysFor(listOf(2)).isEmpty())
        assertTrue(dao.historyFor(HistoryType.Song.name, listOf(2)).isEmpty())
    }

    @Test
    fun pruneMissingSongsDoesNothingWhenNotLoadedOrEmpty() = runBlocking {
        seed()
        assertEquals(0, cleanup.pruneMissingSongs(setOf(1L), libraryLoaded = false))
        assertEquals(0, cleanup.pruneMissingSongs(emptySet(), libraryLoaded = true))
        assertEquals(4, playlistRows().size)
    }

    @Test
    fun pruneMissingSongsDoesNothingWhenMoreThanFiftyAreMissing() = runBlocking {
        val p = dao.insertPlaylist(PlaylistEntity(name = "Big", createdAt = 1))
        dao.insertSongs((1L..60L).map { PlaylistSongEntity(p, it, it.toInt()) })
        assertEquals(0, cleanup.pruneMissingSongs(setOf(1000L), libraryLoaded = true))
        assertEquals(60, dao.songs(p).size)
    }

    @Test
    fun pruneMissingVideosPrunesMediaStoreUrisOnly() = runBlocking {
        seed()
        val fileUri = "file:///sdcard/Movies/x.mp4"
        progress.onCheckpoint(videoEntity(9, fileUri), 4_000L, 100_000L)
        progress.awaitWrites()

        val pruned = cleanup.pruneMissingVideos(setOf(8L), setOf(otherVideoUri), libraryLoaded = true)

        // Video history ids 7 and 2 are missing from {8}; one progress uri (7) is missing.
        assertEquals(3, pruned)
        assertEquals(setOf(otherVideoUri, fileUri), progress.savedUris())
        assertTrue(dao.historyMediaIds("Video").isEmpty())
    }

    @Test
    fun pruneMissingVideosDoesNothingWhenNotLoaded() = runBlocking {
        seed()
        assertEquals(0, cleanup.pruneMissingVideos(setOf(8L), setOf(otherVideoUri), libraryLoaded = false))
        assertEquals(setOf(videoUri, otherVideoUri), progress.savedUris())
    }
}
