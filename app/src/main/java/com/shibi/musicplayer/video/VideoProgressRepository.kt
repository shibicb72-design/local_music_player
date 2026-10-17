package com.shibi.musicplayer.video

import android.content.Context
import com.shibi.musicplayer.diagnostics.ErrorLog
import com.shibi.musicplayer.diagnostics.ErrorLogger
import com.shibi.musicplayer.domain.MediaFacts
import com.shibi.musicplayer.domain.ResumePolicy
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.playlist.PlaylistDatabase
import com.shibi.musicplayer.playlist.VideoProgressDao
import com.shibi.musicplayer.playlist.VideoProgressEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Resume positions in Room's `video_progress`, behind an in-memory copy: the view model reads
 * it synchronously when a video opens, and the Videos list draws its hairlines and missing
 * durations from [progress]. Every change lands in the copy at once and is written in order
 * on one background lane, in a process-wide scope, so a save made as the activity goes away
 * (close, swipe from Recents) still reaches the disk and nothing holds the activity.
 *
 * A database failure never reaches the screen: the video just plays from the start next
 * time. It goes to the error log, once per kind of failure per run, so a broken disk cannot
 * fill the log with a save every 5 s.
 */
class VideoProgressRepository internal constructor(
    private val dao: VideoProgressDao,
    private val errorLogger: ErrorLogger,
    io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : VideoProgressHooks {

    // limitedParallelism(1) runs the writes one at a time in the order they were made.
    private val scope = CoroutineScope(SupervisorJob() + io.limitedParallelism(1))
    private val _progress = MutableStateFlow<Map<String, VideoProgress>>(emptyMap())
    private val loggedSteps = mutableSetOf<String>()
    private var lastWrite: Job? = null

    /** Every saved video by its `content://` URI. */
    override val progress: StateFlow<Map<String, VideoProgress>> = _progress.asStateFlow()

    init {
        enqueue {
            try {
                val stored = dao.getAll().associate { it.uri to VideoProgress(it.positionMs, it.durationMs) }
                // Anything saved while the table was being read is newer than what it held.
                _progress.update { newer -> stored + newer }
            } catch (error: Exception) {
                logOnce("Read saved video positions", error, media = null)
            }
        }
    }

    override fun saved(video: Video): VideoProgress? = _progress.value[video.contentUri]

    override fun onCheckpoint(video: Video, positionMs: Long, durationMs: Long) {
        val keptDuration = durationMs.takeIf { it > 0L } ?: saved(video)?.durationMs ?: 0L
        store(video, VideoProgress(ResumePolicy.positionToStore(positionMs, keptDuration), keptDuration))
    }

    override fun onFinished(video: Video, durationMs: Long) = clearPosition(video, durationMs)

    override fun onStartOver(video: Video, durationMs: Long) = clearPosition(video, durationMs)

    /** Waits for every change made so far to be written (tests). */
    internal suspend fun awaitWrites() {
        lastWrite?.join()
    }

    private fun clearPosition(video: Video, durationMs: Long) {
        val keptDuration = durationMs.takeIf { it > 0L } ?: saved(video)?.durationMs ?: 0L
        store(video, VideoProgress(positionMs = 0L, durationMs = keptDuration))
    }

    private fun store(video: Video, entry: VideoProgress) {
        val uri = video.contentUri
        if (_progress.value[uri] == entry) return
        _progress.update { current -> current + (uri to entry) }
        val row = VideoProgressEntity(uri, entry.positionMs, entry.durationMs, clock())
        enqueue {
            try {
                dao.upsert(row)
            } catch (error: Exception) {
                // Only the file name: the URI and its folder stay out of the log.
                logOnce("Save video position", error, MediaFacts(fileName = video.displayName, mimeType = video.mimeType))
            }
        }
    }

    private fun enqueue(block: suspend () -> Unit) {
        synchronized(this) {
            lastWrite = scope.launch { block() }
        }
    }

    private fun logOnce(step: String, error: Throwable, media: MediaFacts?) {
        val first = synchronized(loggedSteps) { loggedSteps.add(step) }
        if (first) errorLogger.log(step = step, error = error, shown = null, media = media)
    }

    companion object {
        @Volatile
        private var instance: VideoProgressRepository? = null

        /** One per process, like the database it reads. */
        fun get(context: Context): VideoProgressRepository = instance ?: synchronized(this) {
            instance ?: VideoProgressRepository(
                dao = PlaylistDatabase.get(context).videoProgressDao(),
                errorLogger = ErrorLog.get(context),
            ).also { instance = it }
        }
    }
}
