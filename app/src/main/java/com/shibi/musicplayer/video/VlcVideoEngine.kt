package com.shibi.musicplayer.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.Surface
import android.view.View
import com.shibi.musicplayer.domain.AspectMode
import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.domain.VideoFit
import java.io.Closeable
import java.io.FileNotFoundException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.libvlc.util.HWDecoderUtil

/**
 * libVLC 3.7 behind [VideoEngine]. [LibVLC] (and so the native libraries) is created lazily,
 * on the first [load] or [attach], and only in the app: tests never construct this class.
 *
 * MediaStore hands out `content://` URIs, which libVLC cannot open itself. The engine opens
 * a read-only [ParcelFileDescriptor] through the ContentResolver and gives libVLC the
 * descriptor (`fd://N`); the descriptor stays open until the next [load] or [release].
 * Files loaded from the tracks sheet arrive already as `file://` or `fd://` locations.
 *
 * Events arrive on the main thread (the player is created there).
 *
 * libVLC renders into one engine-owned video SurfaceTexture and one subtitle SurfaceTexture
 * for the player's whole life; hosts (box, pane, fullscreen, PiP) only display them through
 * [VideoTextureFrame], so moving the picture never rebuilds libVLC's output; aspect modes
 * are a TextureView transform ([VideoFit]).
 */
internal class VlcVideoEngine(context: Context) : VideoEngine {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(VideoEngineState())
    override val state: StateFlow<VideoEngineState> = _state.asStateFlow()
    private val _tracks = MutableStateFlow(TrackSnapshot())
    override val tracks: StateFlow<TrackSnapshot> = _tracks.asStateFlow()

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var attached: VideoTextureFrame? = null
    private var released = false
    private var aspectMode = AspectMode.Default

    private var options = EngineOptions()

    /** What [openMedia] asked libVLC to decode with, for the playback check log. */
    private var decoderLabel = "MediaCodec (mediacodec_ndk, software fallback allowed)"

    /** The audio output the LibVLC constructor picks (opensles or android_audiotrack). */
    private val audioOutputLabel: String by lazy {
        if (HWDecoderUtil.getAudioOutputFromDevice() == HWDecoderUtil.AudioOutput.OPENSLES) {
            "opensles"
        } else {
            "android_audiotrack"
        }
    }

    /** The text size [libVlc] was created with: it is a LibVLC-wide option. */
    private var libVlcTextSize: SubtitleTextSize? = null
    private var currentUri: String? = null

    /** Files loaded from the sheet for the current video, re-added when it reopens. */
    private val externals = mutableListOf<ExternalTrack>()

    /** Loaded files whose track has not shown up yet, matched to the next new track of their type. */
    private val awaitingTrack = ArrayDeque<ExternalTrack>()
    private val externalNames = mutableMapOf<Int, String>()

    /** What to put back once a reopened video plays again. */
    private var restore: Restore? = null

    /** libVLC's Android vout needs a surface before the first frame; play waits for a view. */
    private var playWhenAttached = false

    /** Screen locked: no views and no video track, audio only (V8). */
    private var pictureOff = false

    /** The view the UI attached last; it gets the picture back when [pictureOff] ends. */
    private var latest: VideoTextureFrame? = null

    /** Hosts that are on screen, oldest first; the newest one shows the picture. */
    private val hosts = ArrayList<VideoTextureFrame>()

    /** The engine-owned pair libVLC renders into (and [Surface]s around it), for the player's life. */
    private var videoTexture: SurfaceTexture? = null
    private var subtitleTexture: SurfaceTexture? = null
    private var videoOut: Surface? = null
    private var subtitleOut: Surface? = null

    /** What libVLC last reported about the picture (OnNewVideoLayoutListener); 0 until known. */
    private var layoutWidth = 0
    private var layoutHeight = 0
    private var layoutSarNum = 1
    private var layoutSarDen = 1

    /** The video track to bring back, as libVLC numbered it before it was disabled. */
    private var savedVideoTrack: Int? = null

    /** The track was switched off for a surface change or a lock and comes back once the attached view's surface is ready. */
    private var trackPending = false

    /** This load already reopened once after an EncounteredError. */
    private var retriedAfterError = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val fallbackRestore = Runnable { restorePendingTrack() }

    private val newLayoutListener = IVLCVout.OnNewVideoLayoutListener { _, width, height, visibleWidth, visibleHeight, sarNum, sarDen ->
        layoutWidth = if (visibleWidth > 0) visibleWidth else width
        layoutHeight = if (visibleHeight > 0) visibleHeight else height
        layoutSarNum = sarNum
        layoutSarDen = sarDen
        applyFit()
    }

    /** libVLC reports when a view's surface is created or destroyed; a destroyed surface under a running decoder is the failure guarded against. */
    private val voutCallback = object : IVLCVout.Callback {
        override fun onSurfacesCreated(vlcVout: IVLCVout) {
            if (SurfaceHandoff.restoreOnSurfaceReady(trackPending, pictureOff, attached != null)) {
                restorePendingTrack()
            }
        }

        override fun onSurfacesDestroyed(vlcVout: IVLCVout) {
            // The surface went away under a running decoder: turn the track off now so it comes
            // back on the next ready surface, instead of letting the decoder fail.
            if (!pictureOff) {
                val mediaPlayer = player ?: return
                val track = mediaPlayer.videoTrack
                if (track != SurfaceHandoff.NoTrack) {
                    savedVideoTrack = SurfaceHandoff.trackToSave(track, savedVideoTrack)
                    mediaPlayer.setVideoTrack(SurfaceHandoff.NoTrack)
                    trackPending = true
                }
            }
        }
    }

    private val listener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.ESAdded -> {
                // A reopen while locked (even one starting paused) brings a video track back.
                if (pictureOff && event.esChangedType == IMedia.Track.Type.Video) {
                    player?.setVideoTrack(TrackMapping.DisabledId)
                }
                onTrackAdded(event.esChangedType, event.esChangedID)
                refreshTracks()
            }
            MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> refreshTracks()
            MediaPlayer.Event.Playing -> {
                if (pictureOff) player?.setVideoTrack(TrackMapping.DisabledId)
                dispatch(VideoEngineEvent.Playing)
                restoreTracks()
                refreshTracks()
            }
            MediaPlayer.Event.Paused -> {
                dispatch(VideoEngineEvent.Paused)
                // A reopened paused video starts paused and may never report Playing.
                restoreTracks()
            }
            MediaPlayer.Event.EncounteredError -> {
                val mediaPlayer = player
                val uri = currentUri
                if (
                    mediaPlayer != null &&
                    uri != null &&
                    SurfaceHandoff.reopenAfterError(_state.value.positionMs, retriedAfterError, released)
                ) {
                    // Usually a decoder that lost its surface: open the same video again where it was, once.
                    retriedAfterError = true
                    val wasPlaying = _state.value.status == VideoStatus.Playing ||
                        _state.value.status == VideoStatus.Buffering
                    if (restore == null) {
                        restore = Restore(
                            positionMs = _state.value.positionMs,
                            play = wasPlaying,
                            audioId = TrackMapping.DisabledId,
                            subtitleId = mediaPlayer.spuTrack,
                            audioDelayUs = mediaPlayer.audioDelay,
                            subtitleDelayUs = mediaPlayer.spuDelay,
                        )
                    }
                    trackPending = false
                    savedVideoTrack = null
                    mainHandler.removeCallbacks(fallbackRestore)
                    reopen(mediaPlayer, uri)
                } else {
                    refreshTracks()
                    dispatch(
                        VideoEngineEvent.Failed(
                            VideoError.Playback,
                            VideoPlaybackException("libVLC reported EncounteredError while playing"),
                        ),
                    )
                }
            }
            else -> {
                val mapped = when (event.type) {
                    MediaPlayer.Event.Opening -> VideoEngineEvent.Opening
                    MediaPlayer.Event.Buffering -> VideoEngineEvent.Buffering(event.buffering)
                    MediaPlayer.Event.Stopped -> VideoEngineEvent.Stopped
                    MediaPlayer.Event.EndReached -> VideoEngineEvent.Ended
                    MediaPlayer.Event.TimeChanged -> VideoEngineEvent.Time(event.timeChanged)
                    MediaPlayer.Event.LengthChanged -> VideoEngineEvent.Length(event.lengthChanged)
                    MediaPlayer.Event.Vout -> if (event.voutCount > 0) currentSize() else null
                    else -> null
                }
                if (mapped != null) dispatch(mapped)
            }
        }
    }

    override fun load(uri: String, startMs: Long) {
        if (released) return
        restore = null
        currentUri = uri
        // Another file has its own track ids.
        savedVideoTrack = null
        trackPending = false
        retriedAfterError = false
        mainHandler.removeCallbacks(fallbackRestore)
        dispatch(VideoEngineEvent.Opening)
        if (startMs > 0L) dispatch(VideoEngineEvent.Time(startMs))
        _tracks.value = TrackSnapshot()
        val mediaPlayer = ensurePlayer() ?: return
        mediaPlayer.stop()
        // A new video starts with no loaded files; their descriptors close after the stop.
        closeExternals()
        openMedia(mediaPlayer, uri, startMs = startMs)
    }

    override fun play() {
        val mediaPlayer = player ?: return
        when (_state.value.status) {
            // After the end libVLC keeps the finished input; stop it to start over.
            VideoStatus.Ended -> {
                mediaPlayer.stop()
                // Files loaded while it played lived on that input; the media gets them again.
                externals.forEach { track -> mediaPlayer.addSlave(track.kind.slaveType(), track.location, false) }
                startOrDefer(mediaPlayer)
            }
            VideoStatus.Error -> Unit
            else -> startOrDefer(mediaPlayer)
        }
    }

    override fun pause() {
        playWhenAttached = false
        // A pause while a reopen is still starting must stick once it plays.
        restore = restore?.copy(play = false)
        val mediaPlayer = player ?: return
        if (mediaPlayer.isPlaying) mediaPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        val mediaPlayer = player ?: return
        val duration = _state.value.durationMs
        val target = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        mediaPlayer.setTime(target)
        dispatch(VideoEngineEvent.Time(target))
    }

    override fun setAspectMode(mode: AspectMode) {
        aspectMode = mode
        applyFit()
    }

    override fun configure(options: EngineOptions) {
        if (options == this.options) return
        this.options = options
        val uri = currentUri ?: return
        val mediaPlayer = player ?: return
        if (released || _state.value.status == VideoStatus.Error) return
        reopen(mediaPlayer, uri)
    }

    override fun selectAudioTrack(id: Int) {
        player?.setAudioTrack(id)
        refreshTracks()
    }

    override fun selectSubtitleTrack(id: Int) {
        player?.setSpuTrack(id)
        refreshTracks()
    }

    override fun setAudioDelay(delayMs: Long) {
        player?.setAudioDelay(delayMs * UsPerMs)
        refreshTracks()
    }

    override fun setSubtitleDelay(delayMs: Long) {
        player?.setSpuDelay(delayMs * UsPerMs)
        refreshTracks()
    }

    override fun addExternalTrack(
        kind: ExternalTrackKind,
        location: String,
        name: String,
        resource: Closeable?,
    ): Boolean {
        val mediaPlayer = player ?: return false
        if (released) return false
        val added = mediaPlayer.addSlave(kind.slaveType(), location, true)
        if (added) {
            // A file of this kind still waiting never produced a track; the next new track is this one's.
            awaitingTrack.removeAll { it.kind == kind }
            val track = ExternalTrack(kind, location, name, resource)
            externals += track
            awaitingTrack += track
        }
        return added
    }

    override fun release() {
        if (released) return
        released = true
        playWhenAttached = false
        pictureOff = false
        latest = null
        hosts.clear()
        savedVideoTrack = null
        trackPending = false
        mainHandler.removeCallbacks(fallbackRestore)
        restore = null
        releasePlayer()
        closeDescriptor()
        closeExternals()
        currentUri = null
        _state.value = VideoEngineState()
        _tracks.value = TrackSnapshot()
    }

    override fun createView(context: Context): View = VideoTextureFrame(context)

    override fun attach(view: View) {
        if (released) return
        val frame = view as? VideoTextureFrame ?: return
        hosts.remove(frame)
        hosts.add(frame)
        latest = frame
        // Locked: the picture comes back on this view when the app is in view again.
        if (pictureOff) return
        if (attached === frame) return
        val mediaPlayer = ensurePlayer() ?: return
        val video = videoTexture ?: return
        val subtitles = subtitleTexture ?: return
        // The old host lets go first: a SurfaceTexture is shown by one view at a time.
        attached?.release()
        frame.onSizeChanged = { width, height -> if (attached === frame) onFrameSized(width, height) }
        frame.show(video, subtitles)
        attached = frame
        if (frame.width > 0 && frame.height > 0) onFrameSized(frame.width, frame.height)
        applyFit()
        restorePendingTrack()   // a lock may have switched the track off; the surface is always valid now
        if (playWhenAttached) {
            playWhenAttached = false
            mediaPlayer.play()
        } else if (!mediaPlayer.isPlaying && _state.value.status == VideoStatus.Paused) {
            // Paused: the new view has no frame yet; a seek in place draws the current one.
            mediaPlayer.setTime(mediaPlayer.time)
        }
    }

    /** The picture goes back to the newest host still on screen (fullscreen closing returns it to the box or pane). */
    override fun detach(view: View) {
        if (latest === view) latest = null
        val frame = view as? VideoTextureFrame ?: return
        hosts.remove(frame)
        if (attached !== frame) return
        frame.release()
        attached = null
        if (!released) hosts.lastOrNull()?.let { next ->
            latest = next
            attach(next)
        }
    }
    override fun setPictureEnabled(enabled: Boolean) {
        if (released || pictureOff == !enabled) return
        val mediaPlayer = player
        if (!enabled) {
            pictureOff = true
            mainHandler.removeCallbacks(fallbackRestore)
            if (mediaPlayer != null) {
                val track = mediaPlayer.videoTrack
                if (track != TrackMapping.DisabledId) savedVideoTrack = track
                mediaPlayer.setVideoTrack(TrackMapping.DisabledId)
            }
            attached?.release()
            attached = null
            // A play asked for before a view came no longer needs one.
            if (playWhenAttached && mediaPlayer != null) {
                playWhenAttached = false
                mediaPlayer.play()
            }
            return
        }
        pictureOff = false
        // The vout needs a surface first: the track comes back once the attached view's surface
        // is ready, now if the UI still has one, or on the next attach if its layout is rebuilt.
        trackPending = true
        latest?.let(::attach)
    }

    override fun playbackStats(): PlaybackStats? {
        val media = player?.media ?: return null
        return try {
            media.stats?.let {
                PlaybackStats(
                    decodedVideo = it.decodedVideo,
                    displayedPictures = it.displayedPictures,
                    lostPictures = it.lostPictures,
                    decodedAudio = it.decodedAudio,
                    playedAudioBuffers = it.playedAbuffers,
                    lostAudioBuffers = it.lostAbuffers,
                    decoder = decoderLabel,
                    audioOutput = "$audioOutputLabel to ${audioRouteLabel()}",
                )
            }
        } finally {
            media.release()
        }
    }

    /** Where the sound goes right now; read at [playbackStats] time because it can change. */
    private fun audioRouteLabel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return "unknown"
        val manager = appContext.getSystemService(AudioManager::class.java) ?: return "unknown"
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        return audioRouteType(manager.getAudioDevicesForAttributes(attributes).firstOrNull()?.type)
    }

    /** One word for the playback check log, from the audio device type. */
    private fun audioRouteType(type: Int?): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        -> "wired"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        -> "bluetooth"
        else -> "other"
    }

    /** Turns the saved video track back on; only with a view attached and the picture on. */
    private fun restorePendingTrack() {
        mainHandler.removeCallbacks(fallbackRestore)
        if (!trackPending || pictureOff || attached == null) return
        trackPending = false
        val mediaPlayer = player ?: return
        val track = savedVideoTrack
            ?: mediaPlayer.videoTracks.orEmpty().firstOrNull { it.id != SurfaceHandoff.NoTrack }?.id
        savedVideoTrack = null
        if (track != null) {
            mediaPlayer.setVideoTrack(track)
            // Paused: a seek to the same place makes the new surface show the current frame.
            if (!mediaPlayer.isPlaying) mediaPlayer.setTime(mediaPlayer.time)
        }
    }

    /** libVLC normally reports the new surface; if it never does, the track still comes back. */
    private fun scheduleFallbackRestore() {
        mainHandler.removeCallbacks(fallbackRestore)
        mainHandler.postDelayed(fallbackRestore, SurfaceHandoff.RestoreFallbackMs)
    }

    /** Null only if libVLC itself cannot start; the state then says why. */
    private fun ensurePlayer(): MediaPlayer? {
        player?.let { return it }
        val vlc = try {
            createLibVlc()
        } catch (error: Exception) {
            dispatch(VideoEngineEvent.Failed(VideoError.Playback, error))
            return null
        }
        return MediaPlayer(vlc).also { created ->
            created.setEventListener(listener)
            created.vlcVout.addCallback(voutCallback)
            // One engine-owned output for this player's whole life: views only display it.
            val video = SurfaceTexture(false).also { videoTexture = it }
            val subtitles = SurfaceTexture(false).also { subtitleTexture = it; it.setDefaultBufferSize(1920, 1080) }
            val vout = created.vlcVout
            vout.setVideoSurface(Surface(video).also { videoOut = it }, null)
            vout.setSubtitlesSurface(Surface(subtitles).also { subtitleOut = it }, null)
            vout.setWindowSize(1920, 1080)
            vout.attachViews(newLayoutListener)
            player = created
        }
    }

    private fun createLibVlc(): LibVLC {
        val textSize = options.subtitleTextSize
        val created = try {
            LibVLC(appContext, libVlcOptions(textSize))
        } catch (error: IllegalStateException) {
            // A text-size option this build does not know must never stop playback.
            if (textSize.relativeFontSize == null) throw error
            LibVLC(appContext, libVlcOptions(SubtitleTextSize.Default))
        }
        libVlc = created
        libVlcTextSize = textSize
        return created
    }

    private fun openMedia(mediaPlayer: MediaPlayer, uri: String, resume: Restore? = null, startMs: Long = 0L) {
        val vlc = libVlc ?: return
        val opened = try {
            appContext.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                ?: throw FileNotFoundException("The content resolver returned no file descriptor")
        } catch (error: Exception) {
            closeDescriptor()
            dispatch(VideoEngineEvent.Failed(VideoError.CannotOpen, error))
            return
        }
        val media = try {
            Media(vlc, opened.fileDescriptor)
        } catch (error: Exception) {
            runCatching { opened.close() }
            closeDescriptor()
            dispatch(VideoEngineEvent.Failed(VideoError.CannotOpen, error))
            return
        }
        // Hardware decoding first (libVLC falls back to software itself), or software only.
        // setHWDecoderEnabled(false, false) alone still tries MediaCodec first (":codec=all");
        // the later ":codec=avcodec,all" wins and keeps the decoders to avcodec, i.e. software.
        media.setHWDecoderEnabled(options.hardwareDecoding, false)
        if (!options.hardwareDecoding) media.addOption(":codec=avcodec,all")
        decoderLabel = if (options.hardwareDecoding) {
            "MediaCodec (mediacodec_ndk, software fallback allowed)"
        } else {
            "software (avcodec)"
        }
        media.addOption(":file-caching=$FileCachingMs")
        if (resume != null) {
            // libVLC starts at the saved position itself, and paused if it was paused.
            if (resume.positionMs > 0L) media.addOption(":start-time=${resume.positionMs / 1000.0}")
            if (!resume.play) media.addOption(":start-paused")
        } else if (startMs > 0L) {
            // A saved resume position (V9): libVLC opens there, with no frame from 0 first.
            media.addOption(":start-time=${startMs / 1000.0}")
        }
        mediaPlayer.setMedia(media)
        // The player holds its own reference.
        media.release()
        // The previous descriptor stays open until the new media holds its own.
        closeDescriptor()
        descriptor = opened
        // Before the input starts they become the media's slaves; they open with it.
        externals.forEach { track -> mediaPlayer.addSlave(track.kind.slaveType(), track.location, false) }
        startOrDefer(mediaPlayer)
    }

    /**
     * Decoding or text size changed: open the same video again at the same position and play
     * state, and put back the chosen tracks and the delays as they reappear. A text-size
     * change needs a new [LibVLC], since it is a LibVLC-wide option. Loaded files are expected
     * to come back with the same track ids (same file, same order), so their names stay; this
     * is not yet checked on a device.
     */
    private fun reopen(current: MediaPlayer, uri: String) {
        // A reopen still starting has not got its tracks back yet; keep what it is restoring.
        val saved = restore ?: Restore(
            positionMs = _state.value.positionMs,
            play = current.isPlaying || playWhenAttached || _state.value.status == VideoStatus.Buffering,
            audioId = current.audioTrack,
            subtitleId = current.spuTrack,
            audioDelayUs = current.audioDelay,
            subtitleDelayUs = current.spuDelay,
        )
        val layout = attached
        var mediaPlayer = current
        if (options.subtitleTextSize != libVlcTextSize) {
            releasePlayer()
            mediaPlayer = ensurePlayer() ?: return
            if (layout != null) attach(layout)
        } else {
            mediaPlayer.stop()
        }
        playWhenAttached = false
        restore = saved
        _state.value = _state.value.copy(status = VideoStatus.Buffering)
        openMedia(mediaPlayer, uri, saved)
    }

    /**
     * Tracks and delays live on the running input, so they go back once it plays (or starts
     * paused). libVLC opens a video's slaves before it reports either, so every track the
     * user had chosen exists by then.
     */
    private fun restoreTracks() {
        val saved = restore ?: return
        val mediaPlayer = player ?: return
        restore = null
        if (saved.audioId != TrackMapping.DisabledId) mediaPlayer.setAudioTrack(saved.audioId)
        mediaPlayer.setSpuTrack(saved.subtitleId)
        mediaPlayer.setAudioDelay(saved.audioDelayUs)
        mediaPlayer.setSpuDelay(saved.subtitleDelayUs)
        // Paused while it was starting: it opened playing, so pause it now.
        if (!saved.play && mediaPlayer.isPlaying) mediaPlayer.pause()
        refreshTracks()
    }

    private fun onTrackAdded(type: Int, id: Int) {
        val kind = when (type) {
            IMedia.Track.Type.Audio -> ExternalTrackKind.Audio
            IMedia.Track.Type.Text -> ExternalTrackKind.Subtitle
            else -> return
        }
        // A reopened video: its loaded files come back with the ids they had.
        if (restore != null) return
        val awaited = awaitingTrack.firstOrNull { it.kind == kind } ?: return
        awaitingTrack.remove(awaited)
        externalNames[id] = awaited.name
    }

    private fun refreshTracks() {
        val mediaPlayer = player ?: return
        _tracks.value = TrackSnapshot(
            audio = mediaPlayer.audioTracks.orEmpty().map { RawTrackDescription(it.id, it.name) },
            subtitles = mediaPlayer.spuTracks.orEmpty().map { RawTrackDescription(it.id, it.name) },
            mediaTracks = mediaTracks(mediaPlayer),
            selectedAudioId = mediaPlayer.audioTrack,
            selectedSubtitleId = mediaPlayer.spuTrack,
            audioDelayMs = mediaPlayer.audioDelay / UsPerMs,
            subtitleDelayMs = mediaPlayer.spuDelay / UsPerMs,
            externalNames = externalNames.toMap(),
        )
    }

    private fun mediaTracks(mediaPlayer: MediaPlayer): List<RawMediaTrack> {
        val media = mediaPlayer.media ?: return emptyList()
        return try {
            (0 until media.trackCount).mapNotNull { index -> media.getTrack(index)?.toRaw() }
        } finally {
            media.release()
        }
    }

    private fun IMedia.Track.toRaw(): RawMediaTrack = RawMediaTrack(
        id = id,
        language = language,
        codec = codec,
        description = description,
        type = when (type) {
            IMedia.Track.Type.Audio -> MediaTrackType.Audio
            IMedia.Track.Type.Video -> MediaTrackType.Video
            IMedia.Track.Type.Text -> MediaTrackType.Text
            else -> MediaTrackType.Unknown
        },
        info = when (this) {
            is IMedia.VideoTrack -> "${width}x$height"
            is IMedia.AudioTrack -> "${channels}ch $rate Hz"
            else -> null
        },
    )

    private fun releasePlayer() {
        player?.let { mediaPlayer ->
            mediaPlayer.vlcVout.removeCallback(voutCallback)
            mediaPlayer.setEventListener(null)
            mediaPlayer.vlcVout.detachViews()
            mediaPlayer.stop()
            mediaPlayer.release()
        }
        attached?.release()
        attached = null
        runCatching { videoOut?.release() }
        runCatching { subtitleOut?.release() }
        runCatching { videoTexture?.release() }
        runCatching { subtitleTexture?.release() }
        videoOut = null
        subtitleOut = null
        videoTexture = null
        subtitleTexture = null
        layoutWidth = 0
        layoutHeight = 0
        player = null
        libVlc?.release()
        libVlc = null
        libVlcTextSize = null
        mainHandler.removeCallbacks(fallbackRestore)
    }

    private fun onFrameSized(width: Int, height: Int) {
        player?.vlcVout?.setWindowSize(width, height)
        subtitleTexture?.setDefaultBufferSize(width, height)
        applyFit()
    }

    private fun applyFit() {
        val frame = attached ?: return
        val fit = VideoFit.scale(aspectMode, frame.width, frame.height, layoutWidth, layoutHeight, layoutSarNum, layoutSarDen)
        frame.setFit(fit.scaleX, fit.scaleY)
    }

    private fun startOrDefer(mediaPlayer: MediaPlayer) {
        // Locked, there is no view to wait for: the sound plays on its own.
        if (attached != null || pictureOff) {
            mediaPlayer.play()
        } else {
            playWhenAttached = true
        }
    }

    private fun currentSize(): VideoEngineEvent? {
        val track = player?.currentVideoTrack ?: return null
        val rotated = track.orientation == IMedia.VideoTrack.Orientation.LeftTop ||
            track.orientation == IMedia.VideoTrack.Orientation.LeftBottom ||
            track.orientation == IMedia.VideoTrack.Orientation.RightTop ||
            track.orientation == IMedia.VideoTrack.Orientation.RightBottom
        return VideoEngineEvent.Size(
            width = track.width,
            height = track.height,
            aspect = InlineVideoBox.displayAspect(track.width, track.height, track.sarNum, track.sarDen, rotated),
        )
    }

    private fun dispatch(event: VideoEngineEvent) {
        _state.value = VideoStateReducer.reduce(_state.value, event)
    }

    private fun closeDescriptor() {
        runCatching { descriptor?.close() }
        descriptor = null
    }

    private fun closeExternals() {
        externals.forEach { track -> runCatching { track.resource?.close() } }
        externals.clear()
        awaitingTrack.clear()
        externalNames.clear()
    }

    private class ExternalTrack(
        val kind: ExternalTrackKind,
        val location: String,
        val name: String,
        val resource: Closeable?,
    )

    private data class Restore(
        val positionMs: Long,
        val play: Boolean,
        val audioId: Int,
        val subtitleId: Int,
        val audioDelayUs: Long,
        val subtitleDelayUs: Long,
    )

    internal companion object {
        /** Local files: a short read-ahead starts fast and still covers slow SD cards. */
        private const val FileCachingMs = 300
        private const val UsPerMs = 1_000L

        /**
         * Options for libVLC's Java constructor.
         *
         * That constructor appends its own options (for example the audio output and the
         * Android display chroma) to the list it is handed, so this must return a fresh
         * mutable ArrayList. A read-only list (listOf, buildList) makes the constructor
         * throw UnsupportedOperationException and no video can play.
         */
        fun libVlcOptions(textSize: SubtitleTextSize): ArrayList<String> {
            val options = arrayListOf("--file-caching=$FileCachingMs")
            textSize.relativeFontSize?.let { options.add("--freetype-rel-fontsize=$it") }
            return options
        }

        private fun ExternalTrackKind.slaveType(): Int = when (this) {
            ExternalTrackKind.Audio -> IMedia.Slave.Type.Audio
            ExternalTrackKind.Subtitle -> IMedia.Slave.Type.Subtitle
        }
    }
}
