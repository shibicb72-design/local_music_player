package com.shibi.musicplayer.video

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.TextureView
import android.widget.FrameLayout

/**
 * Shows the engine's pair of SurfaceTextures (video and subtitles) through plain TextureViews.
 *
 * libVLC keeps one pair of SurfaceTextures for its whole life, so moving the picture between hosts
 * never re-creates the surface the decoder is writing to. A SurfaceTexture can be consumed by only
 * one TextureView at a time, and the listener returns false from onSurfaceTextureDestroyed so the
 * textures survive the view and can be handed to the next host. [show] swaps in a fresh view pair,
 * [release] detaches it, and [setFit] applies the letterbox or fill scale around the view centre.
 */
class VideoTextureFrame(context: Context) : FrameLayout(context) {

    private var videoView: TextureView? = null
    private var subtitleView: TextureView? = null

    private var fitScaleX = 1f
    private var fitScaleY = 1f

    /** Called after the frame's size changes to a non-zero size. */
    var onSizeChanged: ((width: Int, height: Int) -> Unit)? = null

    /** Called each time the video view draws a new frame (not the subtitles view). */
    var onFrameShown: (() -> Unit)? = null

    private val keepTexture = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            if (surface === videoView?.surfaceTexture) onFrameShown?.invoke()
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            // The engine owns the texture and hands it to the next view.
            return false
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyTransform()
        if (w > 0 && h > 0) {
            onSizeChanged?.invoke(w, h)
        }
    }

    /** Shows [video] and [subtitles], replacing any previous view pair. */
    fun show(video: SurfaceTexture, subtitles: SurfaceTexture) {
        if (videoView?.surfaceTexture === video && subtitleView?.surfaceTexture === subtitles) return
        release()
        val v = TextureView(context).apply {
            surfaceTextureListener = keepTexture
            setSurfaceTexture(video)
        }
        val s = TextureView(context).apply {
            isOpaque = false
            surfaceTextureListener = keepTexture
            setSurfaceTexture(subtitles)
        }
        addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(s, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        videoView = v
        subtitleView = s
        applyTransform()
    }

    /** Detaches both views so the textures can be handed to the next view. */
    fun release() {
        videoView?.let { removeView(it) }
        subtitleView?.let { removeView(it) }
        videoView = null
        subtitleView = null
    }

    /** Stores the scale applied around the frame centre and re-applies it to the video view. */
    fun setFit(scaleX: Float, scaleY: Float) {
        fitScaleX = scaleX
        fitScaleY = scaleY
        applyTransform()
    }

    private fun applyTransform() {
        val view = videoView ?: return
        if (width == 0 || height == 0) return
        val m = Matrix()
        m.setScale(fitScaleX, fitScaleY, width / 2f, height / 2f)
        view.setTransform(m)
    }
}
