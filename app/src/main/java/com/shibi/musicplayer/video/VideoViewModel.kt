package com.shibi.musicplayer.video

import android.app.Application
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shibi.musicplayer.R
import com.shibi.musicplayer.diagnostics.ErrorLog
import com.shibi.musicplayer.diagnostics.ErrorLogger
import com.shibi.musicplayer.domain.AspectMode
import com.shibi.musicplayer.domain.BackgroundAction
import com.shibi.musicplayer.domain.BackgroundEvent
import com.shibi.musicplayer.domain.BackgroundPolicy
import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.domain.MediaFacts
import com.shibi.musicplayer.domain.PlaybackArbiter
import com.shibi.musicplayer.domain.PlaybackCheck
import com.shibi.musicplayer.domain.PositionThrottle
import com.shibi.musicplayer.domain.ResumePolicy
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAudioEvent
import com.shibi.musicplayer.domain.VideoFocusPolicy
import com.shibi.musicplayer.domain.VideoProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class VideoUiState(
    val video: Video? = null,
    val status: VideoStatus = VideoStatus.Idle,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** Display width / height; MediaStore's size until the engine reports the real one. */
    val aspect: Float? = null,
    val error: VideoError? = null,
    /** The video resumed here: the "Resumed at 12:34 · Start over" chip shows for 4 s. */
    val resumedAtMs: Long? = null,
) {
    /** Loaded, playing, paused, ended or failed: the box is on screen and the mini player is not. */
    val isActive: Boolean get() = video != null
    val isPlaying: Boolean get() = status == VideoStatus.Playing
}

/**
 * Where resume positions live ([VideoProgressRepository] in the app). [saved] is read
 * synchronously as a video opens, so it must answer from memory.
 */
interface VideoProgressHooks {
    /** Every saved video by its `content://` URI, for the list's hairlines and durations. */
    val progress: StateFlow<Map<String, VideoProgress>> get() = NoProgress

    fun saved(video: Video): VideoProgress? = null

    /** A save point: every 5 s while playing, and on pause, close and leaving the app. */
    fun onCheckpoint(video: Video, positionMs: Long, durationMs: Long)

    /** The end was reached: the position is cleared, the length kept. */
    fun onFinished(video: Video, durationMs: Long)

    /** The chip's Start over: the same as finishing. */
    fun onStartOver(video: Video, durationMs: Long)

    object None : VideoProgressHooks {
        override fun onCheckpoint(video: Video, positionMs: Long, durationMs: Long) = Unit
        override fun onFinished(video: Video, durationMs: Long) = Unit
        override fun onStartOver(video: Video, durationMs: Long) = Unit
    }
}

private val NoProgress: StateFlow<Map<String, VideoProgress>> = MutableStateFlow(emptyMap())

/** Music's side, as the video sees it: a video played from anywhere keeps the music paused. */
interface MusicControl {
    val isPlaying: Boolean
    fun pause()
}

/** Where the picture is on screen, for the error log's step. */
enum class PlayerSurface(val label: String) {
    InlineBox("inline box"),
    VideoPane("video pane"),
    Fullscreen("fullscreen"),
}

/**
 * Creates the video engine and drives it, so playback survives a fold, unfold or rotation
 * (the activity is recreated; this view model is not). The engine is created on the first
 * [open], never at construction, so music-only sessions and Robolectric never touch libVLC.
 *
 * While a video is loaded (not ended or failed) the engine is also handed to
 * [VideoPlaybackService] through [sessionLink]: its session gives earbud, watch, notification
 * and lock-screen control, and its foreground state keeps the sound playing with the screen
 * locked ([onAppBackground], [onAppForeground]).
 */
class VideoViewModel internal constructor(
    application: Application,
    private val engineFactory: () -> VideoEngine,
    private val audioFocus: VideoAudioFocus,
    private val progress: VideoProgressHooks,
    private val prefs: VideoPrefs = InMemoryVideoPrefs(),
    private val errorLogger: ErrorLogger = ErrorLogger.None,
    private val externalTracks: ExternalTrackSource? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionLink: VideoSessionLink = VideoSessionLink.None,
    /** Paces the 5 s saves; tests pass their own. */
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        engineFactory = { testEngineFactory?.invoke() ?: VlcVideoEngine(application) },
        audioFocus = AndroidVideoAudioFocus(application),
        progress = testProgress?.invoke() ?: VideoProgressRepository.get(application),
        prefs = DataStoreVideoPrefs(application),
        errorLogger = ErrorLog.get(application),
        externalTracks = ContentExternalTrackSource(application),
        sessionLink = testSessionLink?.invoke() ?: ServiceVideoSessionLink(application, ErrorLog.get(application)),
    )

    internal companion object {
        /** A loaded file whose track has not appeared by then failed. */
        const val TrackAppearTimeoutMs = 4_000L

        /**
         * Robolectric tests of the real activity swap libVLC for a fake here, since the
         * activity builds this view model with the default factory. The app never sets it.
         */
        @VisibleForTesting
        @Volatile
        var testEngineFactory: (() -> VideoEngine)? = null

        /** The same for the session service, which Robolectric cannot bind for real. */
        @VisibleForTesting
        @Volatile
        var testSessionLink: (() -> VideoSessionLink)? = null

        /**
         * The same for resume positions: the database is one per process, so a position saved
         * by one real-activity test would resume the next one's video.
         */
        @VisibleForTesting
        @Volatile
        var testProgress: (() -> VideoProgressHooks)? = null
    }

    private val _engine = MutableStateFlow<VideoEngine?>(null)

    /** The engine the surface attaches to; null while no video is open. */
    val engine: StateFlow<VideoEngine?> = _engine.asStateFlow()

    private val _uiState = MutableStateFlow(VideoUiState())
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    private val _inPip = MutableStateFlow(false)

    /** The activity is in picture-in-picture: the UI shows only the video. */
    val inPip: StateFlow<Boolean> = _inPip.asStateFlow()

    private var stateJob: Job? = null
    private var pausedForTransientLoss = false

    /** Saved positions by URI, for the Videos list. */
    val savedProgress: StateFlow<Map<String, VideoProgress>> = progress.progress

    /** Where the open video started; 0 unless it resumed. */
    private var resumeFromMs = 0L

    /**
     * The open video has played or paused at its real position. Until then the engine's
     * position is still 0 (or the opening seek), so nothing is saved over the old place.
     */
    private var started = false
    private var lastSavedAtMs = 0L
    private var noticeJob: Job? = null
    private var checkJob: Job? = null

    /** The last aspect mode picked in fullscreen (saved); the box and the pane always fit. */
    val aspectMode: StateFlow<AspectMode> =
        prefs.aspectMode.stateIn(viewModelScope, SharingStarted.Eagerly, AspectMode.Default)

    /** Settings: the left-half brightness swipe in fullscreen, on by default. */
    val brightnessSwipe: StateFlow<Boolean> =
        prefs.brightnessSwipe.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private var fullscreen = false
    private var surface = PlayerSurface.InlineBox

    /** Set by the app's composition; null when nothing can play music (tests, no activity). */
    private var musicControl: MusicControl? = null

    /** Why the app is out of view, or null while it is in view. */
    private var background: BackgroundEvent? = null

    /** The engine's picture is off until the app is in view again. */
    private var pictureOff = false

    /** The session's buttons: the same calls as the app's own controls. */
    private val sessionControls = object : VideoSessionControls {
        override val positionMs: Long get() = exactPositionMs()
        override fun play() = this@VideoViewModel.play()
        override fun pause() = this@VideoViewModel.pause()
        override fun seekTo(positionMs: Long) = this@VideoViewModel.seekTo(positionMs)
        override fun stop() = close()
    }

    private val engineOptions: StateFlow<EngineOptions> =
        combine(prefs.hardwareDecoding, prefs.subtitleTextSize) { hardware, textSize ->
            EngineOptions(hardwareDecoding = hardware, subtitleTextSize = textSize)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, EngineOptions())

    private val trackLoading = MutableStateFlow<ExternalTrackKind?>(null)
    private val trackMessage = MutableStateFlow<TrackMessage?>(null)
    private var trackJob: Job? = null

    /** The current load; a cancelled one finds another token here and leaves the state alone. */
    private var loadToken: Any? = null

    /** The tracks sheet: the open video's tracks, delays, text size and decoding. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val trackSheet: StateFlow<TrackSheetState> = combine(
        _engine.flatMapLatest { engine -> engine?.tracks ?: flowOf(TrackSnapshot()) },
        engineOptions,
        trackLoading,
        trackMessage,
    ) { snapshot, options, loading, message ->
        TrackSheetState.of(snapshot, options, loading, message)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TrackSheetState())

    init {
        // The saved mode can arrive after fullscreen opened; apply it once it does.
        viewModelScope.launch {
            aspectMode.collect { if (fullscreen) applyAspect() }
        }
        // A changed decoding or text size reopens the open video in place.
        viewModelScope.launch {
            engineOptions.collect { options -> _engine.value?.configure(options) }
        }
        // The session (and its service) is up while a video is loaded and not finished.
        viewModelScope.launch {
            _uiState.map(VideoSessionMapping::snapshotOf).distinctUntilChanged().collect { snapshot ->
                val engine = _engine.value
                if (snapshot != null && engine != null) {
                    sessionLink.show(snapshot, sessionControls, engine)
                } else {
                    sessionLink.end()
                }
            }
        }
    }

    /** The app's music player; a video played from its notification or earbuds pauses it. */
    fun setMusicControl(control: MusicControl) {
        musicControl = control
    }

    /** Only the composition that set [control] takes it away, so a newer one keeps its own. */
    fun clearMusicControl(control: MusicControl) {
        if (musicControl === control) musicControl = null
    }

    /**
     * The activity stopped for real (not a rotation or fold). The picture goes, since its
     * surface goes with the window; the sound follows [BackgroundPolicy]: a lock keeps it,
     * leaving the app pauses it (picture-in-picture in V10).
     */
    fun onAppBackground(event: BackgroundEvent) {
        background = event
        val engine = _engine.value ?: return
        val status = _uiState.value.status
        val playing = status == VideoStatus.Playing || status == VideoStatus.Buffering
        when (BackgroundPolicy.decide(event, playing)) {
            BackgroundAction.Pause -> pause()
            // onStop only arrives here with a playing video when picture-in-picture did not
            // happen (not supported, or refused), so fall back to pausing.
            BackgroundAction.EnterPip -> pause()
            // Locked, or picking a file: still a save point, in case the process goes.
            BackgroundAction.KeepAudio, BackgroundAction.None -> checkpoint()
        }
        engine.setPictureEnabled(false)
        pictureOff = true
    }

    /**
     * The tracks sheet's system file picker opened (it stops the activity) or returned. Known
     * gap: Home pressed inside the picker brings no second stop, so the video plays on there.
     */
    fun setFilePickerShown(shown: Boolean) {
        isPickingFile = shown
    }

    /** While the tracks sheet's file picker covers the app. */
    var isPickingFile: Boolean = false
        private set

    /** Back in view (unlocked): the picture returns where the sound is. */
    fun onAppForeground() {
        background = null
        isPickingFile = false
        if (!pictureOff) return
        pictureOff = false
        val engine = _engine.value ?: return
        engine.setPictureEnabled(true)
        val state = _uiState.value
        if (state.status == VideoStatus.Playing || state.status == VideoStatus.Paused) {
            // The decoder restarts from a key frame here, so the picture matches the sound.
            engine.seekTo(state.positionMs)
        }
    }

    /**
     * Plays [video] at once; the same video again just resumes it. A video left past its first
     * 5 s and before its last 5% starts where it was left ([ResumePolicy]).
     */
    fun open(video: Video) {
        val current = _uiState.value
        if (current.video?.id == video.id && current.status != VideoStatus.Error) {
            play()
            return
        }
        checkpoint()
        stateJob?.cancel()
        noticeJob?.cancel()
        checkJob?.cancel()
        resetTrackLoad()
        externalTracks?.clear()
        val engine = _engine.value ?: engineFactory().also { created ->
            _engine.value = created
            // A new engine starts at fit, which is what the box and the pane want.
            if (fullscreen) applyAspect()
        }
        engine.configure(engineOptions.value)
        val saved = progress.saved(video)
        val startMs = ResumePolicy.startAt(saved, video.durationMs)
        resumeFromMs = startMs
        started = false
        lastSavedAtMs = clock()
        _uiState.value = VideoUiState(
            video = video,
            status = VideoStatus.Buffering,
            positionMs = startMs,
            // MediaStore often has no length for MKV/WMV; libVLC's from an earlier play is saved.
            durationMs = ResumePolicy.displayDuration(video.durationMs, saved) ?: 0L,
            aspect = InlineVideoBox.displayAspect(video.width ?: 0, video.height ?: 0),
        )
        pausedForTransientLoss = false
        audioFocus.acquire(::onAudioEvent)
        engine.load(video.contentUri, startMs)
        stateJob = viewModelScope.launch {
            engine.state.collect { engineState -> merge(video, engineState) }
        }
    }

    fun play() {
        val engine = _engine.value ?: return
        // From the box, the notification, the lock screen or earbuds: never two sounds.
        musicControl?.let { music ->
            if (PlaybackArbiter.onVideoStart(music.isPlaying).pauseMusic) music.pause()
        }
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

    /** The activity entered or left picture-in-picture; the UI shows only the video while in it. */
    fun setInPip(on: Boolean) {
        _inPip.value = on
    }

    fun seekTo(positionMs: Long) {
        _engine.value?.seekTo(positionMs)
    }

    /**
     * One picture-in-picture control tap: [deltaMs] later (positive) or earlier (negative),
     * kept inside the video. Does nothing when no video is open.
     */
    fun seekBy(deltaMs: Long) {
        val state = _uiState.value
        if (state.video == null) return
        val target = state.positionMs + deltaMs
        val clamped = if (state.durationMs > 0L) {
            target.coerceIn(0L, state.durationMs)
        } else {
            target.coerceAtLeast(0L)
        }
        seekTo(clamped)
    }

    /** The chip's Start over: back to 0, and the saved place is cleared. */
    fun startOver() {
        val engine = _engine.value ?: return
        val state = _uiState.value
        val video = state.video ?: return
        noticeJob?.cancel()
        resumeFromMs = 0L
        lastSavedAtMs = clock()
        engine.seekTo(0L)
        progress.onStartOver(video, state.durationMs)
        _uiState.update { it.copy(resumedAtMs = null) }
    }

    /** Fullscreen uses the saved aspect mode; leaving it goes back to fit for the box and pane. */
    fun setFullscreen(on: Boolean) {
        if (fullscreen == on) return
        fullscreen = on
        applyAspect()
    }

    /** The fullscreen aspect button: the next mode, saved for next time. Returns it for the label. */
    fun cycleAspectMode(): AspectMode {
        val next = aspectMode.value.next()
        viewModelScope.launch { prefs.setAspectMode(next) }
        _engine.value?.takeIf { fullscreen }?.setAspectMode(next)
        return next
    }

    fun setBrightnessSwipe(enabled: Boolean) {
        viewModelScope.launch { prefs.setBrightnessSwipe(enabled) }
    }

    /** The box, the pane or fullscreen; only named in the error log. */
    fun setPlayerSurface(surface: PlayerSurface) {
        this.surface = surface
    }

    fun selectAudioTrack(id: Int) {
        _engine.value?.selectAudioTrack(id)
    }

    fun selectSubtitleTrack(id: Int) {
        _engine.value?.selectSubtitleTrack(id)
    }

    /** One stepper tap: +1 plays the audio later, -1 earlier. */
    fun stepAudioDelay(direction: Int) {
        val engine = _engine.value ?: return
        engine.setAudioDelay(TrackDelay.Audio.step(engine.tracks.value.audioDelayMs, direction))
    }

    /** One stepper tap: +1 shows the subtitles later, -1 earlier. */
    fun stepSubtitleDelay(direction: Int) {
        val engine = _engine.value ?: return
        engine.setSubtitleDelay(TrackDelay.Subtitle.step(engine.tracks.value.subtitleDelayMs, direction))
    }

    fun setSubtitleTextSize(size: SubtitleTextSize) {
        viewModelScope.launch { prefs.setSubtitleTextSize(size) }
    }

    fun setHardwareDecoding(enabled: Boolean) {
        viewModelScope.launch { prefs.setHardwareDecoding(enabled) }
    }

    /** The sheet closed: its last message goes with it. */
    fun dismissTrackMessage() {
        trackMessage.value = null
    }

    /**
     * A file picked in the tracks sheet. Subtitles must have a subtitle extension. On any
     * failure the sheet shows a short message and the details go to the error log.
     */
    fun loadExternalTrack(kind: ExternalTrackKind, uri: String) {
        val engine = _engine.value ?: return
        val source = externalTracks ?: return
        val video = _uiState.value.video ?: return
        trackJob?.cancel()
        trackMessage.value = null
        trackLoading.value = kind
        // Set before launch: on the immediate dispatcher the load can finish before trackJob is.
        val token = Any()
        loadToken = token
        trackJob = viewModelScope.launch {
            val facts = withContext(ioDispatcher) { source.describe(uri) }
            // Until the engine holds it, an open descriptor is ours to close.
            var unclaimed: PreparedTrack? = null
            try {
                if (kind == ExternalTrackKind.Subtitle && !SubtitleFiles.isSupported(facts.fileName)) {
                    throw UnsupportedTrackFileException(
                        "Not a subtitle file (${SubtitleFiles.extensionsLabel()})",
                    )
                }
                // Claimed inside the block: a cancel landing as it returns must still close it.
                val prepared = withContext(ioDispatcher) {
                    source.prepare(kind, uri, facts).also { unclaimed = it }
                }
                val before = trackCount(engine.tracks.value, kind)
                if (!engine.addExternalTrack(kind, prepared.location, prepared.name, prepared.resource)) {
                    throw TrackLoadException("libVLC refused the file (addSlave returned false)")
                }
                // The engine closes it with the video from here on.
                unclaimed = null
                withTimeoutOrNull(TrackAppearTimeoutMs) {
                    engine.tracks.first { trackCount(it, kind) > before }
                } ?: throw TrackLoadException("libVLC added no track for the file within $TrackAppearTimeoutMs ms")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = when {
                    error is UnsupportedTrackFileException -> TrackMessage.NotASubtitle
                    kind == ExternalTrackKind.Subtitle -> TrackMessage.SubtitleFailed
                    else -> TrackMessage.AudioFailed
                }
                trackMessage.value = message
                errorLogger.log(
                    step = "${kind.step()} for ${video.displayName} (${surface.label})",
                    error = error,
                    shown = shownText(message),
                    media = facts,
                )
            } finally {
                // Refused, failed or cancelled (another pick, a new video, close) before handover.
                unclaimed?.resource?.let { resource -> runCatching { resource.close() } }
                // A newer pick already shows its own spinner; a cancelled one leaves it alone.
                if (loadToken === token) trackLoading.value = null
            }
        }
    }

    /** ✕, a song tap, or music started elsewhere: save, release the engine, drop focus. */
    fun close() {
        if (_engine.value == null && _uiState.value.video == null) return
        checkpoint()
        stateJob?.cancel()
        stateJob = null
        noticeJob?.cancel()
        noticeJob = null
        checkJob?.cancel()
        checkJob = null
        started = false
        resetTrackLoad()
        externalTracks?.clear()
        // The notification goes at once, before the engine it controls.
        sessionLink.end()
        _engine.value?.release()
        _engine.value = null
        audioFocus.release()
        pausedForTransientLoss = false
        pictureOff = false
        _uiState.value = VideoUiState()
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    private fun merge(video: Video, engineState: VideoEngineState) {
        val wasEnded = _uiState.value.status == VideoStatus.Ended
        val wasFailed = _uiState.value.status == VideoStatus.Error
        val current = _uiState.value
        val next = current.copy(
            status = engineState.status,
            positionMs = engineState.positionMs,
            durationMs = engineState.durationMs.takeIf { it > 0L } ?: current.durationMs,
            aspect = engineState.aspect ?: current.aspect,
            error = engineState.error,
        )
        val otherChanged = next.copy(positionMs = current.positionMs) != current
        // The position reaches the UI about 4 times a second (PositionThrottle) so the app does not recompose on every libVLC tick.
        if (PositionThrottle.shouldPublish(current.positionMs, next.positionMs, otherChanged)) _uiState.value = next
        val status = engineState.status
        if (!started && (status == VideoStatus.Playing || status == VideoStatus.Paused) &&
            (resumeFromMs == 0L || engineState.positionMs > 0L)
        ) {
            started = true
            lastSavedAtMs = clock()
            if (resumeFromMs > 0L) showResumeNotice(resumeFromMs)
            if (status == VideoStatus.Playing) startPlaybackCheck()
        }
        if (status == VideoStatus.Playing && ResumePolicy.isSaveDue(clock(), lastSavedAtMs)) checkpoint()
        if (!wasEnded && status == VideoStatus.Ended) {
            noticeJob?.cancel()
            _uiState.update { it.copy(resumedAtMs = null) }
            progress.onFinished(video, _uiState.value.durationMs)
            // Nothing is playing; a later play() asks again.
            audioFocus.release()
        }
        if (!wasFailed && status == VideoStatus.Error) {
            noticeJob?.cancel()
            _uiState.update { it.copy(resumedAtMs = null) }
            logPlaybackError(video, engineState)
        }
    }

    /** The chip shows once the video plays from the saved place, and goes 4 s later. */
    private fun showResumeNotice(positionMs: Long) {
        _uiState.update { it.copy(resumedAtMs = positionMs) }
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(ResumePolicy.NoticeMs)
            _uiState.update { it.copy(resumedAtMs = null) }
        }
    }

    /** One playback-check log line per load: the frames shown over the first 5 s of playing. */
    private fun startPlaybackCheck() {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            val engine = _engine.value ?: return@launch
            val before = engine.playbackStats() ?: return@launch
            delay(PlaybackCheck.WindowMs)
            val after = engine.playbackStats() ?: return@launch
            val video = _uiState.value.video ?: return@launch
            errorLogger.log(
                step = PlaybackCheck.summary(
                    PlaybackCheck.fps(before.displayedPictures, after.displayedPictures, PlaybackCheck.WindowMs),
                    after.lostPictures - before.lostPictures,
                    engineOptions.value.hardwareDecoding,
                ) + " (${surface.label})",
                error = null,
                shown = null,
                media = MediaFacts(
                    fileName = video.displayName,
                    mimeType = video.mimeType,
                    codecs = TrackMapping.codecSummary(engine.tracks.value.mediaTracks),
                    width = video.width,
                    height = video.height,
                    durationMs = video.durationMs,
                    sizeBytes = video.sizeBytes,
                ),
            )
        }
    }

    /** The box, pane or fullscreen shows a short message; the log gets the cause and the file. */
    private fun logPlaybackError(video: Video, engineState: VideoEngineState) {
        val error = engineState.error ?: VideoError.Playback
        // Out of view, the short message waits on the box, pane or fullscreen for the return.
        val where = when (background) {
            BackgroundEvent.ScreenOff -> "${surface.label}, screen locked"
            BackgroundEvent.LeftApp -> "${surface.label}, app in background"
            BackgroundEvent.PipDismissed -> "${surface.label}, picture-in-picture closed"
            BackgroundEvent.PickingFile -> "${surface.label}, file picker open"
            null -> surface.label
        }
        val step = when (error) {
            VideoError.CannotOpen -> "Open video ($where)"
            VideoError.Playback -> "Play video ($where): libVLC error"
        }
        val codecs = _engine.value?.tracks?.value?.mediaTracks.orEmpty()
        errorLogger.log(
            step = step,
            error = engineState.failureCause,
            shown = getApplication<Application>().getString(
                when (error) {
                    VideoError.CannotOpen -> R.string.video_error_open
                    VideoError.Playback -> R.string.video_error_playback
                },
            ),
            media = MediaFacts(
                fileName = video.displayName,
                mimeType = video.mimeType,
                codecs = TrackMapping.codecSummary(codecs),
                width = engineState.videoWidth.takeIf { it > 0 } ?: video.width,
                height = engineState.videoHeight.takeIf { it > 0 } ?: video.height,
                durationMs = engineState.durationMs.takeIf { it > 0L } ?: video.durationMs,
                sizeBytes = video.sizeBytes,
            ),
        )
    }

    private fun resetTrackLoad() {
        trackJob?.cancel()
        trackJob = null
        loadToken = null
        trackLoading.value = null
        trackMessage.value = null
    }

    private fun shownText(message: TrackMessage): String {
        val app = getApplication<Application>()
        return when (message) {
            TrackMessage.NotASubtitle -> app.getString(R.string.tracks_error_not_subtitle, SubtitleFiles.extensionsLabel())
            TrackMessage.SubtitleFailed -> app.getString(R.string.tracks_error_subtitle)
            TrackMessage.AudioFailed -> app.getString(R.string.tracks_error_audio)
        }
    }

    private fun ExternalTrackKind.step(): String = when (this) {
        ExternalTrackKind.Audio -> "Load audio file"
        ExternalTrackKind.Subtitle -> "Load subtitle file"
    }

    private fun trackCount(snapshot: TrackSnapshot, kind: ExternalTrackKind): Int = when (kind) {
        ExternalTrackKind.Audio -> snapshot.audio.count { !TrackMapping.isDisable(it) }
        ExternalTrackKind.Subtitle -> snapshot.subtitles.count { !TrackMapping.isDisable(it) }
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

    private fun applyAspect() {
        _engine.value?.setAspectMode(if (fullscreen) aspectMode.value else AspectMode.Fit)
    }

    /** The engine's exact position; the UI's copy moves in 250 ms steps (PositionThrottle). */
    private fun exactPositionMs(): Long = _engine.value?.state?.value?.positionMs ?: _uiState.value.positionMs

    /**
     * Saves where the open video is. Not before it has played (its position is not real yet),
     * not after a failure, and not after the end, which already cleared it.
     */
    private fun checkpoint() {
        val state = _uiState.value
        val video = state.video ?: return
        if (!started || state.status == VideoStatus.Error || state.status == VideoStatus.Ended) return
        lastSavedAtMs = clock()
        progress.onCheckpoint(video, exactPositionMs(), state.durationMs)
    }
}
