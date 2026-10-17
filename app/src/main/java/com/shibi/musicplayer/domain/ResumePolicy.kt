package com.shibi.musicplayer.domain

/**
 * A video's saved place. [durationMs] is libVLC's length when it was known (0 if not), so
 * rows whose MediaStore duration is missing can show it.
 */
data class VideoProgress(val positionMs: Long, val durationMs: Long)

/**
 * When a video resumes, what is saved, and how often. A video resumes only past the first
 * 5 s and before its last 5%; reaching the last 5% counts as finished, which clears the
 * position (the length is kept for the list).
 */
object ResumePolicy {
    /** At or before this, starting over costs nothing, so the video starts at 0. */
    const val MinResumeMs = 5_000L

    /** Save while playing at most this often. */
    const val SaveIntervalMs = 5_000L

    /** How long the "Resumed at 12:34 · Start over" chip stays once the video plays. */
    const val NoticeMs = 4_000L

    /**
     * True in the last 5% of a known length. Integer maths (`20 p >= 19 d`), so a 95% boundary
     * never depends on rounding.
     */
    fun isFinished(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0L && positionMs * 20L >= durationMs * 19L

    fun shouldResume(positionMs: Long, durationMs: Long): Boolean =
        positionMs > MinResumeMs && !isFinished(positionMs, durationMs)

    /**
     * Where a video opens: the saved position if it should resume, else 0. The saved length
     * wins over MediaStore's ([knownDurationMs]), which is often missing for MKV/WMV.
     */
    fun startAt(saved: VideoProgress?, knownDurationMs: Long?): Long {
        val position = saved?.positionMs ?: return 0L
        return if (shouldResume(position, durationOf(saved, knownDurationMs))) position else 0L
    }

    /** What a save point stores: the position, or 0 once the video is in its last 5%. */
    fun positionToStore(positionMs: Long, durationMs: Long): Long =
        if (isFinished(positionMs, durationMs)) 0L else positionMs.coerceAtLeast(0L)

    /** The row's hairline: how much is watched, or null when the video would not resume. */
    fun watchedFraction(saved: VideoProgress?, knownDurationMs: Long?): Float? {
        val position = saved?.positionMs ?: return null
        val duration = durationOf(saved, knownDurationMs)
        if (duration <= 0L || !shouldResume(position, duration)) return null
        return (position.toFloat() / duration).coerceIn(0f, 1f)
    }

    /** A playing video saves again once [SaveIntervalMs] passed since the last save. */
    fun isSaveDue(nowMs: Long, lastSavedAtMs: Long): Boolean = nowMs - lastSavedAtMs >= SaveIntervalMs

    /** The length a row shows: MediaStore's, else the one libVLC reported and we saved. */
    fun displayDuration(mediaStoreMs: Long?, saved: VideoProgress?): Long? =
        mediaStoreMs ?: saved?.durationMs?.takeIf { it > 0L }

    private fun durationOf(saved: VideoProgress, knownDurationMs: Long?): Long =
        saved.durationMs.takeIf { it > 0L } ?: knownDurationMs?.takeIf { it > 0L } ?: 0L
}
