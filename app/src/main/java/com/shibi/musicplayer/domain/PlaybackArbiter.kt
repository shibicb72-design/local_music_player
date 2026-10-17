package com.shibi.musicplayer.domain

/**
 * Two engines (ExoPlayer for music, libVLC for video), never two sounds.
 * Pure: the caller applies the decision to the music controller and the video view model.
 */
object PlaybackArbiter {

    /**
     * Music stays paused when a video ends. Surprise audio is worse than one tap on the
     * mini player; flip this to resume the music the video paused.
     */
    val ResumeMusicAfterVideo = false

    data class Decision(
        val pauseMusic: Boolean = false,
        val closeVideo: Boolean = false,
        val resumeMusic: Boolean = false,
        val pauseVideo: Boolean = false,
    )

    private val None = Decision()

    /** A video starts: pause (not stop) the music, so its queue and position stay. */
    fun onVideoStart(musicPlaying: Boolean): Decision = Decision(pauseMusic = musicPlaying)

    /** Any song tap, playlist play or mini-player play: the video closes first. */
    fun onMusicStart(videoActive: Boolean): Decision = Decision(closeVideo = videoActive)

    /**
     * Music went from paused to playing by any path (notification, lock screen, earbuds).
     * Only the rising edge counts: the pause the video itself caused never closes it.
     */
    fun onMusicPlayingChanged(wasPlaying: Boolean, isPlaying: Boolean, videoActive: Boolean): Decision =
        if (!wasPlaying && isPlaying) onMusicStart(videoActive) else None

    /**
     * The video reached its end; there is no auto-next and, by default, no music. In two-pane
     * the video pane closes so Now Playing returns; the compact box stays with "Play again".
     */
    fun onVideoEnded(musicPausedByVideo: Boolean, twoPane: Boolean = false): Decision =
        Decision(resumeMusic = ResumeMusicAfterVideo && musicPausedByVideo, closeVideo = twoPane)

    /** Back in two-pane closes the video pane before it navigates anywhere. */
    fun onBack(videoActive: Boolean, twoPane: Boolean): Decision =
        Decision(closeVideo = videoActive && twoPane)

    /**
     * The compact box lives on the Videos route, so leaving it pauses the video (there are no
     * controls elsewhere). Two-pane keeps playing in the right pane while the left pane browses.
     */
    fun onRouteChanged(fromVideos: Boolean, toVideos: Boolean, twoPane: Boolean): Decision =
        Decision(pauseVideo = fromVideos && !toVideos && !twoPane)

    /**
     * A fold or unfold with a video loaded lands on Videos: folded, that is where the box is;
     * unfolded, the left pane shows the list next to the video pane.
     */
    fun showVideosAfterLayoutChange(videoActive: Boolean): Boolean = videoActive
}

/** Audio focus and route events for the video player, free of Android constants. */
enum class VideoAudioEvent { Gain, Loss, LossTransient, LossTransientCanDuck, BecomingNoisy }

/**
 * What the video does on an audio event. [pausedForTransientLoss] is remembered between
 * events so a call that ends resumes the video, but a pause the user chose is never undone.
 */
object VideoFocusPolicy {

    enum class Action { None, Pause, Resume }

    data class Result(val action: Action, val pausedForTransientLoss: Boolean)

    fun decide(event: VideoAudioEvent, isPlaying: Boolean, pausedForTransientLoss: Boolean): Result =
        when (event) {
            VideoAudioEvent.Loss, VideoAudioEvent.BecomingNoisy ->
                Result(if (isPlaying) Action.Pause else Action.None, pausedForTransientLoss = false)
            VideoAudioEvent.LossTransient ->
                if (isPlaying) Result(Action.Pause, pausedForTransientLoss = true)
                else Result(Action.None, pausedForTransientLoss)
            // API 26+ ducks the stream itself; a movie keeps playing under a notification.
            VideoAudioEvent.LossTransientCanDuck -> Result(Action.None, pausedForTransientLoss)
            VideoAudioEvent.Gain ->
                if (pausedForTransientLoss && !isPlaying) Result(Action.Resume, pausedForTransientLoss = false)
                else Result(Action.None, pausedForTransientLoss = false)
        }
}

/** The compact inline player box: `min(width / aspect, half the viewport)`, 16:9 until known. */
object InlineVideoBox {

    const val DefaultAspect = 16f / 9f
    const val MaxViewportShare = 0.5f

    /** [aspect] is width / height of the displayed picture; null or invalid means unknown. */
    fun height(width: Float, viewportHeight: Float, aspect: Float?): Float {
        val ratio = aspect?.takeIf { it.isFinite() && it > 0f } ?: DefaultAspect
        val natural = width / ratio
        return minOf(natural, viewportHeight * MaxViewportShare).coerceAtLeast(0f)
    }

    /** Display aspect from the decoded size, the sample aspect ratio and a 90° rotation. */
    fun displayAspect(width: Int, height: Int, sarNum: Int = 1, sarDen: Int = 1, rotated: Boolean = false): Float? {
        if (width <= 0 || height <= 0) return null
        val sar = if (sarNum > 0 && sarDen > 0) sarNum.toFloat() / sarDen else 1f
        val aspect = width * sar / height
        return if (rotated) 1f / aspect else aspect
    }
}
