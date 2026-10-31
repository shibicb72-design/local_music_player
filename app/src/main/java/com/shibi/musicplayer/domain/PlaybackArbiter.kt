package com.shibi.musicplayer.domain

/**
 * Two engines (ExoPlayer for music, libVLC for video), never two sounds.
 * Pure: the caller applies the decision to the music controller and the video view model.
 */
object PlaybackArbiter {

    /** Nothing here ever starts the music: surprise audio is worse than one tap on the mini player. */
    data class Decision(
        val pauseMusic: Boolean = false,
        val closeVideo: Boolean = false,
        val pauseVideo: Boolean = false,
        val exitFullscreen: Boolean = false,
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
     * The video reached its end; there is no auto-next and no music: the music the video paused
     * stays paused. The video stays loaded at its end with "Play again", in the box and in the
     * pane. In two-pane, fullscreen gives way to the pane; folded, fullscreen keeps its own
     * "Play again".
     */
    fun onVideoEnded(twoPane: Boolean = false): Decision = Decision(exitFullscreen = twoPane)

    /**
     * The music mini player shows wherever Now Playing is not on screen, but not while a video
     * plays or waits paused, where a second play button would fight it. Once the video ends the
     * bar comes back with the music as the video left it, paused; only a tap on its play button
     * starts the music, and that closes the video ([onMusicPlayingChanged]).
     */
    fun showMusicBanner(hasMusic: Boolean, onNowPlaying: Boolean, videoActive: Boolean, videoEnded: Boolean): Boolean =
        hasMusic && !onNowPlaying && (!videoActive || videoEnded)

    /** Back in two-pane closes the video pane before it navigates anywhere. */
    fun onBack(videoActive: Boolean, twoPane: Boolean): Decision =
        Decision(closeVideo = videoActive && twoPane)

    /**
     * Leaving Videos pauses the video in both layouts: folded the box lives on the Videos
     * route, unfolded the right pane follows the tab.
     */
    fun onRouteChanged(fromVideos: Boolean, toVideos: Boolean): Decision =
        Decision(pauseVideo = fromVideos && !toVideos)

    /** Unfolded, the right pane shows the video only on the Videos tab (fullscreen counts as Videos); otherwise Now Playing. */
    fun showVideoPane(videoActive: Boolean, onVideos: Boolean): Boolean = videoActive && onVideos

    /**
     * A fold or unfold with a video loaded lands on Videos: folded, that is where the box is;
     * unfolded, the left pane shows the list next to the video pane.
     */
    fun showVideosAfterLayoutChange(videoActive: Boolean): Boolean = videoActive

    /**
     * A tap on the video's notification (or lock-screen card) opens the app where the video
     * is: Videos (folded, the box; unfolded, the list by the pane). Fullscreen already shows it.
     */
    fun showVideosOnSessionTap(videoActive: Boolean, fullscreen: Boolean): Boolean = videoActive && !fullscreen

    /**
     * Turning a phone (or the folded cover screen) from portrait to landscape while the
     * compact box shows a video goes fullscreen, like YouTube. Only that edge counts: a new
     * composition ([wasLandscape] null), the unfolded inner screen ([compactDevice] false)
     * or a phone already in landscape never jumps.
     */
    fun enterFullscreenOnRotate(
        wasLandscape: Boolean?,
        isLandscape: Boolean,
        compactDevice: Boolean,
        videoActive: Boolean,
        onVideos: Boolean,
    ): Boolean = wasLandscape == false && isLandscape && compactDevice && videoActive && onVideos

    /** Wide (and not-yet-known) videos turn sideways; portrait clips keep the phone as it is. */
    fun fullscreenOrientation(aspect: Float?): FullscreenOrientation {
        val ratio = aspect?.takeIf { it.isFinite() && it > 0f } ?: InlineVideoBox.DefaultAspect
        return if (ratio > 1f) FullscreenOrientation.Landscape else FullscreenOrientation.Keep
    }
}

/** What fullscreen asks of the activity: `SENSOR_LANDSCAPE`, or leave it to the user. */
enum class FullscreenOrientation { Landscape, Keep }

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
