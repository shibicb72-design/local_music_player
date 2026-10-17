package com.shibi.musicplayer.video

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAudioEvent
import com.shibi.musicplayer.domain.VideoFocusPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoUiState(
    val video: Video? = null,
    val status: VideoStatus = VideoStatus.Idle,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** Display width / height; MediaStore's size until the engine reports the real one. */
    val aspect: Float? = null,
    val error: VideoError? = null,
) {
    /** Loaded, playing, paused, ended or failed: the box is on screen and the mini player is not. */
    val isActive: Boolean get() = video != null
    val isPlaying: Boolean get() = status == VideoStatus.Playing
}

/** Where V9 saves resume positions. V4 saves nothing; the calls are already in place. */
interface VideoProgressHooks {
    fun onCheckpoint(video: Video, positionMs: Long, durationMs: Long)
    fun onFinished(video: Video)

    object None : VideoProgressHooks {
        override fun onCheckpoint(video: Video, positionMs: Long, durationMs: Long) = Unit
        override fun onFinished(video: Video) = Unit
    }
}

/**
 * Owns the video engine, so playback survives a fold, unfold or rotation (the activity is
 * recreated; this view model is not). The engine is created on the first [open], never at
 * construction, so music-only sessions and Robolectric never touch libVLC.
 */
class VideoViewModel internal constructor(
    application: Application,
    private val engineFactory: () -> VideoEngine,
    private val audioFocus: VideoAudioFocus,
    private val progress: VideoProgressHooks,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        engineFactory = { VlcVideoEngine(application) },
        audioFocus = AndroidVideoAudioFocus(application),
        progress = VideoProgressHooks.None,
    )

    private val _engine = MutableStateFlow<VideoEngine?>(null)

    /** The engine the surface attaches to; null while no video is open. */
    val engine: StateFlow<VideoEngine?> = _engine.asStateFlow()

    private val _uiState = MutableStateFlow(VideoUiState())
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    private var stateJob: Job? = null
    private var pausedForTransientLoss = false

    /** Plays [video] at once; the same video again just resumes it. */
    fun open(video: Video) {
        val current = _uiState.value
        if (current.video?.id == video.id && current.status != VideoStatus.Error) {
            play()
            return
        }
        checkpoint()
        stateJob?.cancel()
        val engine = _engine.value ?: engineFactory().also { _engine.value = it }
        _uiState.value = VideoUiState(
            video = video,
            status = VideoStatus.Buffering,
            durationMs = video.durationMs ?: 0L,
            aspect = InlineVideoBox.displayAspect(video.width ?: 0, video.height ?: 0),
        )
        pausedForTransientLoss = false
        audioFocus.acquire(::onAudioEvent)
        engine.load(video.contentUri)
        stateJob = viewModelScope.launch {
            engine.state.collect { engineState -> merge(video, engineState) }
        }
    }

    fun play() {
        val engine = _engine.value ?: return
        pausedForTransientLoss = false
        audioFocus.acquire(::onAudioEvent)
        engine.play()
    }

    fun pause() {
        val engine = _engine.value ?: return
        pausedForTransientLoss = false
        engine.pause()
        checkpoint()
    }

    fun togglePlayPause() {
        val status = _uiState.value.status
        if (status == VideoStatus.Playing || status == VideoStatus.Buffering) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        _engine.value?.seekTo(positionMs)
    }

    /** ✕, a song tap, or music started elsewhere: save, release the engine, drop focus. */
    fun close() {
        if (_engine.value == null && _uiState.value.video == null) return
        checkpoint()
        stateJob?.cancel()
        stateJob = null
        _engine.value?.release()
        _engine.value = null
        audioFocus.release()
        pausedForTransientLoss = false
        _uiState.value = VideoUiState()
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    private fun merge(video: Video, engineState: VideoEngineState) {
        val wasEnded = _uiState.value.status == VideoStatus.Ended
        _uiState.update { state ->
            state.copy(
                status = engineState.status,
                positionMs = engineState.positionMs,
                durationMs = engineState.durationMs.takeIf { it > 0L } ?: state.durationMs,
                aspect = engineState.aspect ?: state.aspect,
                error = engineState.error,
            )
        }
        if (!wasEnded && engineState.status == VideoStatus.Ended) {
            progress.onFinished(video)
            // Nothing is playing; a later play() asks again.
            audioFocus.release()
        }
    }

    private fun onAudioEvent(event: VideoAudioEvent) {
        val status = _uiState.value.status
        val playing = status == VideoStatus.Playing || status == VideoStatus.Buffering
        val result = VideoFocusPolicy.decide(event, playing, pausedForTransientLoss)
        when (result.action) {
            VideoFocusPolicy.Action.Pause -> {
                _engine.value?.pause()
                checkpoint()
            }
            VideoFocusPolicy.Action.Resume -> _engine.value?.play()
            VideoFocusPolicy.Action.None -> Unit
        }
        pausedForTransientLoss = result.pausedForTransientLoss
        // Another player took focus for good; ask again only when the user presses play.
        if (event == VideoAudioEvent.Loss) audioFocus.release()
    }

    private fun checkpoint() {
        val state = _uiState.value
        val video = state.video ?: return
        if (state.status == VideoStatus.Error) return
        progress.onCheckpoint(video, state.positionMs, state.durationMs)
    }
}
