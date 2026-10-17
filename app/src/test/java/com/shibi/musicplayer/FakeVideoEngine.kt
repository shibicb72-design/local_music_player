package com.shibi.musicplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import com.shibi.musicplayer.domain.AspectMode
import com.shibi.musicplayer.video.EngineOptions
import com.shibi.musicplayer.video.ExternalTrackKind
import com.shibi.musicplayer.video.RawTrackDescription
import com.shibi.musicplayer.video.TrackSnapshot
import com.shibi.musicplayer.video.VideoEngine
import java.io.Closeable
import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoEngineState
import com.shibi.musicplayer.video.VideoStateReducer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stands in for libVLC in Robolectric: records calls, moves through states on demand, and
 * draws a placeholder frame letterboxed at [frameAspect] on black.
 */
class FakeVideoEngine(
    initial: VideoEngineState = VideoEngineState(),
    private val frameAspect: Float = 16f / 9f,
    initialTracks: TrackSnapshot = TrackSnapshot(),
) : VideoEngine {

    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<VideoEngineState> = _state

    /** Tests set the tracks the "file" has; the track calls below update them like libVLC. */
    val trackState = MutableStateFlow(initialTracks)
    override val tracks: StateFlow<TrackSnapshot> = trackState

    val calls = mutableListOf<String>()

    /** Kept apart from [calls], so the play/pause/seek sequences stay exact. */
    val configured = mutableListOf<EngineOptions>()

    /** False makes [addExternalTrack] refuse, like libVLC's `addSlave` returning false. */
    var acceptExternal = true

    /** False adds the file without a new track appearing (libVLC could not read it). */
    var externalAddsTrack = true
    private var nextExternalId = 100
    var released = false
        private set
    var attachedView: View? = null
        private set

    fun emit(event: VideoEngineEvent) {
        _state.value = VideoStateReducer.reduce(_state.value, event)
    }

    override fun load(uri: String) {
        calls += "load:$uri"
        emit(VideoEngineEvent.Opening)
    }

    override fun play() {
        calls += "play"
    }

    override fun pause() {
        calls += "pause"
    }

    override fun seekTo(positionMs: Long) {
        calls += "seek:$positionMs"
        emit(VideoEngineEvent.Time(positionMs))
    }

    override fun setAspectMode(mode: AspectMode) {
        calls += "aspect:${mode.name}"
    }

    override fun configure(options: EngineOptions) {
        configured += options
    }

    override fun selectAudioTrack(id: Int) {
        calls += "audio:$id"
        trackState.value = trackState.value.copy(selectedAudioId = id)
    }

    override fun selectSubtitleTrack(id: Int) {
        calls += "subtitle:$id"
        trackState.value = trackState.value.copy(selectedSubtitleId = id)
    }

    override fun setAudioDelay(delayMs: Long) {
        calls += "audioDelay:$delayMs"
        trackState.value = trackState.value.copy(audioDelayMs = delayMs)
    }

    override fun setSubtitleDelay(delayMs: Long) {
        calls += "subtitleDelay:$delayMs"
        trackState.value = trackState.value.copy(subtitleDelayMs = delayMs)
    }

    override fun addExternalTrack(
        kind: ExternalTrackKind,
        location: String,
        name: String,
        resource: Closeable?,
    ): Boolean {
        calls += "external:${kind.name}:$location"
        if (!acceptExternal) return false
        if (!externalAddsTrack) return true
        val id = nextExternalId++
        val added = RawTrackDescription(id, "Track $id")
        val current = trackState.value
        trackState.value = when (kind) {
            ExternalTrackKind.Audio -> current.copy(audio = current.audio + added, selectedAudioId = id)
            ExternalTrackKind.Subtitle -> current.copy(subtitles = current.subtitles + added, selectedSubtitleId = id)
        }.copy(externalNames = current.externalNames + (id to name))
        return true
    }

    override fun release() {
        calls += "release"
        released = true
    }

    override fun createView(context: Context): View = PlaceholderFrame(context, frameAspect)

    /** False while the app is out of view, like libVLC with its video track off. */
    var pictureEnabled = true
        private set

    /** The view the UI attached last, which gets the picture back. */
    private var latestView: View? = null

    override fun attach(view: View) {
        latestView = view
        if (!pictureEnabled) return
        calls += "attach"
        attachedView = view
    }

    override fun detach(view: View) {
        if (latestView === view) latestView = null
        if (attachedView !== view) return
        calls += "detach"
        attachedView = null
    }

    override fun setPictureEnabled(enabled: Boolean) {
        if (pictureEnabled == enabled) return
        calls += "picture:$enabled"
        pictureEnabled = enabled
        if (!enabled) {
            attachedView = null
        } else {
            latestView?.let(::attach)
        }
    }

    /** A dusk sky over a field, fitted inside the view like libVLC's best-fit scale. */
    private class PlaceholderFrame(context: Context, private val aspect: Float) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(0xFF000000.toInt())
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val frameW: Float
            val frameH: Float
            if (w / h > aspect) {
                frameH = h
                frameW = h * aspect
            } else {
                frameW = w
                frameH = w / aspect
            }
            val left = (w - frameW) / 2f
            val top = (h - frameH) / 2f
            paint.shader = LinearGradient(
                0f, top, 0f, top + frameH,
                0xFFFFB37A.toInt(), 0xFF4A3A7A.toInt(),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(left, top, left + frameW, top + frameH, paint)
            paint.shader = null
            paint.color = 0xFFFFE3A3.toInt()
            canvas.drawCircle(left + frameW * 0.72f, top + frameH * 0.38f, frameH * 0.12f, paint)
            paint.color = 0xFF1F3326.toInt()
            canvas.drawRect(left, top + frameH * 0.74f, left + frameW, top + frameH, paint)
        }
    }
}
