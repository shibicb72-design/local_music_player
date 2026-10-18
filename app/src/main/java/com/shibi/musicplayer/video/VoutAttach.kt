package com.shibi.musicplayer.video

import android.view.Surface
import org.videolan.libvlc.interfaces.IVLCVout

/**
 * Attaches libVLC's output when both of its surfaces are raw [Surface]s.
 *
 * In libVLC 3.7.7 a raw Surface counts as ready without any SurfaceView, so when the
 * video helper attaches, both helpers already look ready: the vout moves to READY and
 * fires onSurfacesCreated. The subtitles helper then attaches as the last step of
 * attachViews, and its own onSurfaceCreated throws IllegalArgumentException "invalid
 * state" because the state is no longer ATTACHED. That error is expected here: the
 * vout is fully attached at that point. Anything else is a real failure and is rethrown.
 */
internal object VoutAttach {

    fun attachRawSurfaces(
        vout: IVLCVout,
        video: Surface,
        subtitles: Surface,
        listener: IVLCVout.OnNewVideoLayoutListener?,
    ) {
        vout.setVideoSurface(video, null)
        vout.setSubtitlesSurface(subtitles, null)
        try {
            vout.attachViews(listener)
        } catch (error: IllegalArgumentException) {
            if (error.message != "invalid state" || !vout.areViewsAttached()) throw error
        }
    }
}
