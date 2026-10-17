package com.shibi.musicplayer

import android.database.sqlite.SQLiteFullException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.diagnostics.ErrorLogger
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.playlist.PlaylistDatabase
import com.shibi.musicplayer.playlist.VideoProgressDao
import com.shibi.musicplayer.playlist.VideoProgressEntity
import com.shibi.musicplayer.video.VideoProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VideoProgressRepositoryTest {
    private lateinit var database: PlaylistDatabase
    private val logged = mutableListOf<String>()
    private val logger = ErrorLogger { step, error, shown, media ->
        logged += "$step|${error?.javaClass?.simpleName}|$shown|${media?.fileName}"
    }
    private var now = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PlaylistDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun repository() = VideoProgressRepository(database.videoProgressDao(), logger, clock = { now })

    private fun row(video: Video): VideoProgressEntity? = runBlocking { database.videoProgressDao().find(video.contentUri) }

    @Test
    fun aCheckpointIsKeptInMemoryAtOnceAndWrittenToRoom() = runBlocking {
        val repository = repository()
        repository.awaitWrites()
        repository.onCheckpoint(beach, positionMs = 754_000L, durationMs = 3_600_000L)
        // Read synchronously by the next open, before the write lands.
        assertEquals(VideoProgress(754_000L, 3_600_000L), repository.saved(beach))
        assertEquals(VideoProgress(754_000L, 3_600_000L), repository.progress.value[beach.contentUri])
        repository.awaitWrites()
        assertEquals(VideoProgressEntity(beach.contentUri, 754_000L, 3_600_000L, 1_000L), row(beach))
    }

    @Test
    fun theLastFivePercentIsStoredAsFinished() = runBlocking {
        val repository = repository()
        repository.onCheckpoint(beach, positionMs = 3_500_000L, durationMs = 3_600_000L)
        repository.awaitWrites()
        assertEquals(0L, row(beach)?.positionMs)
        assertEquals(3_600_000L, row(beach)?.durationMs)
    }

    @Test
    fun finishingClearsThePositionAndKeepsTheLength() = runBlocking {
        val repository = repository()
        repository.onCheckpoint(beach, positionMs = 1_800_000L, durationMs = 3_600_000L)
        now = 2_000L
        repository.onFinished(beach, durationMs = 0L)
        repository.awaitWrites()
        assertEquals(VideoProgressEntity(beach.contentUri, 0L, 3_600_000L, 2_000L), row(beach))
        assertEquals(VideoProgress(0L, 3_600_000L), repository.saved(beach))
    }

    @Test
    fun startOverClearsThePosition() = runBlocking {
        val repository = repository()
        repository.onCheckpoint(beach, positionMs = 600_000L, durationMs = 3_600_000L)
        repository.onStartOver(beach, durationMs = 3_600_000L)
        repository.awaitWrites()
        assertEquals(0L, row(beach)?.positionMs)
    }

    @Test
    fun anUnknownLengthKeepsTheOneSavedBefore() = runBlocking {
        val repository = repository()
        repository.onCheckpoint(beach, positionMs = 60_000L, durationMs = 3_600_000L)
        repository.onCheckpoint(beach, positionMs = 90_000L, durationMs = 0L)
        repository.awaitWrites()
        assertEquals(VideoProgress(90_000L, 3_600_000L), repository.saved(beach))
        assertEquals(3_600_000L, row(beach)?.durationMs)
    }

    @Test
    fun writesLandInTheOrderTheyWereMade() = runBlocking {
        val repository = repository()
        for (second in 1..50) {
            now = second * 1_000L
            repository.onCheckpoint(beach, positionMs = second * 5_000L, durationMs = 3_600_000L)
        }
        repository.awaitWrites()
        assertEquals(250_000L, row(beach)?.positionMs)
        assertEquals(50_000L, row(beach)?.updatedAt)
    }

    @Test
    fun aRestartReadsWhatWasSaved() = runBlocking {
        database.videoProgressDao().upsert(VideoProgressEntity(beach.contentUri, 120_000L, 3_600_000L, 1L))
        val repository = repository()
        repository.awaitWrites()
        assertEquals(VideoProgress(120_000L, 3_600_000L), repository.saved(beach))
        assertNull(repository.saved(clip))
    }

    @Test
    fun aDatabaseFailureIsLoggedOnceAndNeverThrown() = runBlocking {
        val repository = VideoProgressRepository(FailingDao, logger, clock = { now })
        repository.awaitWrites()
        assertEquals(listOf("Read saved video positions|SQLiteFullException|null|null"), logged)
        logged.clear()
        repository.onCheckpoint(beach, positionMs = 60_000L, durationMs = 3_600_000L)
        repository.onCheckpoint(beach, positionMs = 65_000L, durationMs = 3_600_000L)
        repository.awaitWrites()
        // The copy in memory still resumes this run; the log has one entry, no URI, no message shown.
        assertEquals(65_000L, repository.saved(beach)?.positionMs)
        assertEquals(1, logged.size)
        assertTrue(logged.single(), logged.single().startsWith("Save video position|"))
        assertTrue(logged.single().endsWith("|null|beach.mp4"))
        assertFalse(logged.single().contains("content://"))
    }

    /** A full disk: every query fails. */
    private object FailingDao : VideoProgressDao {
        override suspend fun getAll(): List<VideoProgressEntity> = throw SQLiteFullException("database or disk is full")
        override suspend fun find(uri: String): VideoProgressEntity? = throw SQLiteFullException("database or disk is full")
        override suspend fun upsert(entry: VideoProgressEntity) = throw SQLiteFullException("database or disk is full")
    }

    private companion object {
        fun video(id: Long, name: String) = Video(
            id = id,
            title = name,
            displayName = "$name.mp4",
            durationMs = null,
            sizeBytes = null,
            width = null,
            height = null,
            mimeType = "video/mp4",
            dateAddedSeconds = 0L,
            folder = null,
            contentUri = "content://media/external/video/media/$id",
        )

        val beach = video(1L, "beach")
        val clip = video(2L, "clip")
    }
}
