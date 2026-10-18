package com.shibi.musicplayer.video

/**
 * Pure decision policy for handing libVLC's video output from one view to another (inline box,
 * two-pane, fullscreen, picture-in-picture).
 *
 * Each host composes its own video view, so switching hosts moves the decoder's output while the
 * hardware decoder is still running. When the old view's surface disappears underneath it, the
 * decoder fails and libVLC raises EncounteredError ("This video can't be played"). The fix is to
 * switch the running video track off before the old view is detached, attach the new view, and
 * restore the track only once libVLC reports that the new surface is ready. This object holds only
 * that decision logic, with no Android or libVLC dependency, so it can be unit tested.
 */
object SurfaceHandoff {

    /** libVLC's id for "no track" (same value as TrackMapping.DisabledId, which is -1). */
    const val NoTrack = -1

    /**
     * A view is being attached. Returns true when the running video track must be switched off
     * before the old view is detached: a view is already attached ([hadView]) or the video track is
     * running without one ([currentTrack] different from [NoTrack]), while the media is loaded and
     * the picture is not off for a lock. False for the very first attach of a fresh load.
     */
    fun disableBeforeSwitch(
        hadView: Boolean,
        mediaLoaded: Boolean,
        currentTrack: Int,
        pictureOff: Boolean,
    ): Boolean =
        !pictureOff &&
            mediaLoaded &&
            (hadView || currentTrack != NoTrack)

    /** The track to keep for later: the running one, never [NoTrack] over a saved real id. */
    fun trackToSave(currentTrack: Int, alreadySaved: Int?): Int? =
        if (currentTrack != NoTrack) currentTrack else alreadySaved

    /** libVLC says the new view's surface is ready: restore only when one is pending and the picture is not off. */
    fun restoreOnSurfaceReady(pending: Boolean, pictureOff: Boolean, hasView: Boolean): Boolean =
        pending && !pictureOff && hasView

    /** Fallback delay: if libVLC never reports the surface, restore the track anyway after this long. */
    const val RestoreFallbackMs = 1_500L

    /**
     * libVLC reported EncounteredError. Reopen once at the current position instead of showing the
     * error when the video had already played (positionMs above 0), it has not been retried since
     * this load (retried false), and the engine is not released.
     */
    fun reopenAfterError(positionMs: Long, retried: Boolean, released: Boolean): Boolean =
        positionMs > 0L && !retried && !released
}
