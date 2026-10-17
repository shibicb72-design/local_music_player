package com.shibi.musicplayer.video

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.View
import com.shibi.musicplayer.domain.AspectMode
import com.shibi.musicplayer.domain.InlineVideoBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * libVLC 3.7 behind [VideoEngine]. [LibVLC] (and so the native libraries) is created lazily,
 * on the first [load] or [attach], and only in the app: tests never construct this class.
 *
 * MediaStore hands out `content://` URIs, which libVLC cannot open itself. The engine opens
 * a read-only [ParcelFileDescriptor] through the ContentResolver and gives libVLC the
 * descriptor (`fd://N`); the descriptor stays open until the next [load] or [release].
 *
 * Events arrive on the main thread (the player is created there).
 */
internal class VlcVideoEngine(context: Context) : VideoEngine {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(VideoEngineState())
    override val state: StateFlow<VideoEngineState> = _state.asStateFlow()

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var attached: VLCVideoLayout? = null
    private var released = false
    private var aspectMode = AspectMode.Default

    /** libVLC's Android vout needs a surface before the first frame; play waits for a view. */
    private var playWhenAttached = false

    private val listener = MediaPlayer.EventListener { event ->
        val mapped = when (event.type) {
            MediaPlayer.Event.Opening -> VideoEngineEvent.Opening
            MediaPlayer.Event.Buffering -> VideoEngineEvent.Buffering(event.buffering)
            MediaPlayer.Event.Playing -> VideoEngineEvent.Playing
            MediaPlayer.Event.Paused -> VideoEngineEvent.Paused
            MediaPlayer.Event.Stopped -> VideoEngineEvent.Stopped
            MediaPlayer.Event.EndReached -> VideoEngineEvent.Ended
            MediaPlayer.Event.EncounteredError -> VideoEngineEvent.Failed(VideoError.Playback)
            MediaPlayer.Event.TimeChanged -> VideoEngineEvent.Time(event.timeChanged)
            MediaPlayer.Event.LengthChanged -> VideoEngineEvent.Length(event.lengthChanged)
            MediaPlayer.Event.Vout -> if (event.voutCount > 0) currentSize() else null
            else -> null
        }
        if (mapped != null) dispatch(mapped)
    }

    override fun load(uri: String) {
        if (released) return
        val mediaPlayer = ensurePlayer()
        val vlc = libVlc ?: return
        mediaPlayer.stop()
        closeDescriptor()
        dispatch(VideoEngineEvent.Opening)
        val opened = runCatching {
            appContext.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
        }.getOrNull()
        if (opened == null) {
            dispatch(VideoEngineEvent.Failed(VideoError.CannotOpen))
            return
        }
        descriptor = opened
        val media = Media(vlc, opened.fileDescriptor)
        // Hardware decoding first; libVLC falls back to software on its own (force = false).
        media.setHWDecoderEnabled(true, false)
        media.addOption(":file-caching=$FileCachingMs")
        mediaPlayer.setMedia(media)
        // The player holds its own reference.
        media.release()
        startOrDefer(mediaPlayer)
    }

    override fun play() {
        val mediaPlayer = player ?: return
        when (_state.value.status) {
            // After the end libVLC keeps the finished input; stop it to start over.
            VideoStatus.Ended -> {
                mediaPlayer.stop()
                startOrDefer(mediaPlayer)
            }
            VideoStatus.Error -> Unit
            else -> startOrDefer(mediaPlayer)
        }
    }

    override fun pause() {
        playWhenAttached = false
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
        if (attached != null) player?.applyScale()
    }

    override fun release() {
        if (released) return
        released = true
        playWhenAttached = false
        player?.let { mediaPlayer ->
            mediaPlayer.setEventListener(null)
            if (attached != null) mediaPlayer.detachViews()
            mediaPlayer.stop()
            mediaPlayer.release()
        }
        attached = null
        player = null
        libVlc?.release()
        libVlc = null
        closeDescriptor()
        _state.value = VideoEngineState()
    }

    override fun createView(context: Context): View =
        VLCVideoLayout(context).apply { setBackgroundColor(Color.BLACK) }

    override fun attach(view: View) {
        if (released) return
        val layout = view as? VLCVideoLayout ?: return
        if (attached === layout) return
        val mediaPlayer = ensurePlayer()
        if (attached != null) mediaPlayer.detachViews()
        // Texture view in every layout (box, pane, fullscreen): it clips and z-orders with
        // Compose (a SurfaceView would not), and a layout switch is one re-attach path.
        mediaPlayer.attachViews(layout, null, true, true)
        attached = layout
        // The scale belongs to the attached layout's helper, so every new view gets it again.
        mediaPlayer.applyScale()
        if (playWhenAttached) {
            playWhenAttached = false
            mediaPlayer.play()
        }
    }

    override fun detach(view: View) {
        if (attached !== view) return
        player?.detachViews()
        attached = null
    }

    private fun ensurePlayer(): MediaPlayer = player ?: run {
        val vlc = LibVLC(appContext, Options)
        libVlc = vlc
        MediaPlayer(vlc).also { created ->
            created.setEventListener(listener)
            player = created
        }
    }

    private fun MediaPlayer.applyScale() {
        setVideoScale(MediaPlayer.ScaleType.valueOf(aspectMode.vlcScale))
    }

    private fun startOrDefer(mediaPlayer: MediaPlayer) {
        if (attached != null) {
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

    private companion object {
        /** Local files: a short read-ahead starts fast and still covers slow SD cards. */
        const val FileCachingMs = 300

        val Options = listOf("--file-caching=$FileCachingMs")
    }
}
