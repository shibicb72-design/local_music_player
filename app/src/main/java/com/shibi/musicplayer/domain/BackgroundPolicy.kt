package com.shibi.musicplayer.domain

/** Why the app went out of view with a video loaded, free of Android. */
enum class BackgroundEvent {
    /** The screen went off or the lock screen came up: the sound should carry on. */
    ScreenOff,

    /** Home, Recents or another app, with the screen on and unlocked. */
    LeftApp,

    /** The user swiped away or closed the picture-in-picture window, with the screen on. */
    PipDismissed,

    /** The tracks sheet's own file picker covers the app; the video plays on, as before V8. */
    PickingFile,
}

/** What the video does when the app goes out of view. */
enum class BackgroundAction {
    /** Keep playing; only the picture goes (no views, no video decoding) until the app is back. */
    KeepAudio,

    /** Go to picture-in-picture; the video keeps playing in the small window. */
    EnterPip,

    /** Pause it. */
    Pause,

    /** Leave it as it is (it was not playing). */
    None,
}

/**
 * Locking the phone during a video keeps its sound (Shibi, 2026-10-17); leaving the app now
 * enters picture-in-picture (V10), and dismissing that window pauses. A paused video stays
 * paused either way. Pure: the activity reports the event, the video view model applies the
 * action.
 */
object BackgroundPolicy {

    fun decide(event: BackgroundEvent, videoPlaying: Boolean): BackgroundAction = when {
        !videoPlaying -> BackgroundAction.None
        event == BackgroundEvent.LeftApp -> BackgroundAction.EnterPip
        event == BackgroundEvent.PipDismissed -> BackgroundAction.Pause
        else -> BackgroundAction.KeepAudio
    }

    /**
     * `onStop` with the screen off ([interactive] false) or the keyguard up is a lock; with
     * the picture-in-picture window dismissed ([inPip]) it is that; with the app's own file
     * picker on top ([pickingFile]) it is that; otherwise the user left.
     */
    fun eventFor(
        interactive: Boolean,
        keyguardLocked: Boolean,
        pickingFile: Boolean = false,
        inPip: Boolean = false,
    ): BackgroundEvent = when {
        !interactive || keyguardLocked -> BackgroundEvent.ScreenOff
        inPip -> BackgroundEvent.PipDismissed
        pickingFile -> BackgroundEvent.PickingFile
        else -> BackgroundEvent.LeftApp
    }

    /**
     * Leaving the app (Home, Recents, another app) with a playing video always enters
     * picture-in-picture; a paused video does not; music never does.
     */
    fun shouldEnterPip(videoPlaying: Boolean): Boolean = videoPlaying
}
