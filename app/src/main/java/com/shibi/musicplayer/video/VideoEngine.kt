package com.shibi.musicplayer.video

import android.content.Context
import android.view.View
import com.shibi.musicplayer.domain.AspectMode
import java.io.Closeable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

enum class VideoStatus { Idle, Buffering, Playing, Paused, Ended, Error }

/** Why playback failed; the UI maps each to a readable string. */
enum class VideoError {
    /** The file could not be opened (moved, deleted, or access revoked). */
    CannotOpen,

    /** The engine opened the file but could not decode or play it. */
    Playback,
}

data class VideoEngineState(
    val status: VideoStatus = VideoStatus.Idle,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /** Display width / height, after the sample aspect ratio and rotation; null until known. */
    val aspect: Float? = null,
    val error: VideoError? = null,
    /** The exception behind [error], for the error log; never shown on screen. */
    val failureCause: Throwable? = null,
)

/** libVLC reports a playback failure as an event with no exception; the log gets this one. */
class VideoPlaybackException(message: String) : Exception(message)

/** Saved engine settings from the tracks sheet, for every video; a change reopens the open one in place. */
data class EngineOptions(
    val hardwareDecoding: Boolean = true,
    val subtitleTextSize: SubtitleTextSize = SubtitleTextSize.Default,
)

/** libVLC's counters for the open video since it started; null when unknown. */
data class PlaybackStats(
    val decodedVideo: Int,
    val displayedPictures: Int,
    val lostPictures: Int,
    /** Audio frames decoded since the video opened. */
    val decodedAudio: Int = 0,
    /** Audio buffers handed to the output since the video opened. */
    val playedAudioBuffers: Int = 0,
    /** Audio buffers dropped since the video opened. */
    val lostAudioBuffers: Int = 0,
    /** The decoder in use, e.g. "MediaCodec (mediacodec_ndk, software fallback allowed)". */
    val decoder: String = "",
    /** The audio output and its current route, e.g. "android_audiotrack to bluetooth". */
    val audioOutput: String = "",
)

/**
 * The video player behind the UI. The app uses [VlcVideoEngine]; tests use a fake that draws
 * a placeholder frame, so Robolectric never loads libVLC's native code. Nothing here names
 * libVLC: composables only see this interface.
 */
interface VideoEngine {
    val state: StateFlow<VideoEngineState>

    /** Audio and subtitle tracks, the selection and the delays; empty until the video starts. */
    val tracks: StateFlow<TrackSnapshot>

    /**
     * Opens a `content://` (or file) URI and starts playing once a view is attached, at
     * [startMs] when resuming. The state reports [startMs] as the position from the start.
     */
    fun load(uri: String, startMs: Long = 0L)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)

    /** How the picture fills its view; kept across views, so a re-attach keeps it. */
    fun setAspectMode(mode: AspectMode)

    /**
     * Hardware decoding and subtitle text size. Kept for the next [load]; when a video is
     * already open and they change, it is reopened at the same position and tracks.
     */
    fun configure(options: EngineOptions)

    fun selectAudioTrack(id: Int)

    /** [TrackMapping.DisabledId] turns subtitles off. */
    fun selectSubtitleTrack(id: Int)

    /** Positive delays play the track later. */
    fun setAudioDelay(delayMs: Long)
    fun setSubtitleDelay(delayMs: Long)

    /**
     * Adds a file to the open video and selects it. [location] is a URI libVLC opens itself
     * (`file://` or `fd://`); [resource] (the descriptor behind an `fd://`) stays open until
     * the next [load] or [release]. False if libVLC refused it.
     */
    fun addExternalTrack(kind: ExternalTrackKind, location: String, name: String, resource: Closeable?): Boolean

    /** Frees the player and its native resources; the engine is unusable afterwards. */
    fun release()

    /** A fresh view for the picture. Each layout creates its own; [attach] binds it. */
    fun createView(context: Context): View
    fun attach(view: View)

    /** Detaches [view] only if it is the attached one, so a newer layout keeps its picture. */
    fun detach(view: View)

    /**
     * Off while the app is out of view (screen locked): the views are detached and the video
     * track is disabled, so nothing decodes frames for a dead surface; the sound goes on, and
     * [play] no longer waits for a view. On: the track comes back and the latest view the UI
     * attached (box, pane or fullscreen) gets the picture again.
     */
    fun setPictureEnabled(enabled: Boolean)

    /** libVLC's counters for the open video since it started; null when unknown. */
    fun playbackStats(): PlaybackStats? = null

    /** One item per load, when the view first draws a picture; the state does not change. */
    val firstFrames: Flow<VideoEngineEvent.FirstFrame> get() = emptyFlow()
}

/** Engine events in our own terms, so the state mapping is pure and unit-tested. */
sealed interface VideoEngineEvent {
    data object Opening : VideoEngineEvent
    data class Buffering(val percent: Float) : VideoEngineEvent
    data object Playing : VideoEngineEvent
    data object Paused : VideoEngineEvent
    data object Stopped : VideoEngineEvent
    data object Ended : VideoEngineEvent
    data class Failed(val error: VideoError, val cause: Throwable? = null) : VideoEngineEvent
    data class Time(val positionMs: Long) : VideoEngineEvent
    data class Length(val durationMs: Long) : VideoEngineEvent
    data class Size(val width: Int, val height: Int, val aspect: Float?) : VideoEngineEvent

    /** The first picture after a load was drawn; [firstInProcess] is true for the first one since the app started. */
    data class FirstFrame(val firstInProcess: Boolean) : VideoEngineEvent
}

object VideoStateReducer {

    fun reduce(state: VideoEngineState, event: VideoEngineEvent): VideoEngineState = when (event) {
        VideoEngineEvent.Opening -> VideoEngineState(status = VideoStatus.Buffering)
        // libVLC keeps reporting buffering while it plays; only the start shows a spinner.
        is VideoEngineEvent.Buffering ->
            if (state.status == VideoStatus.Idle && event.percent < 100f) {
                state.copy(status = VideoStatus.Buffering)
            } else {
                state
            }
        VideoEngineEvent.Playing -> state.copy(status = VideoStatus.Playing, error = null, failureCause = null)
        VideoEngineEvent.Paused ->
            if (state.status == VideoStatus.Ended || state.status == VideoStatus.Error) state
            else state.copy(status = VideoStatus.Paused)
        // A stop from replacing the media or after the end must not hide Ended or Error.
        VideoEngineEvent.Stopped ->
            if (state.status == VideoStatus.Playing || state.status == VideoStatus.Paused) {
                state.copy(status = VideoStatus.Idle)
            } else {
                state
            }
        VideoEngineEvent.Ended -> state.copy(
            status = VideoStatus.Ended,
            positionMs = if (state.durationMs > 0L) state.durationMs else state.positionMs,
        )
        is VideoEngineEvent.Failed -> state.copy(
            status = VideoStatus.Error,
            error = event.error,
            failureCause = event.cause,
        )
        is VideoEngineEvent.Time -> state.copy(
            positionMs = event.positionMs.coerceIn(0L, state.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE),
        )
        is VideoEngineEvent.Length -> state.copy(durationMs = event.durationMs.coerceAtLeast(0L))
        is VideoEngineEvent.Size -> state.copy(
            videoWidth = event.width,
            videoHeight = event.height,
            aspect = event.aspect ?: state.aspect,
        )
        is VideoEngineEvent.FirstFrame -> state
    }
}
