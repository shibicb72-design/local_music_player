package com.shibi.musicplayer.domain

/** Why the app went out of view with a video loaded, free of Android. */
enum class BackgroundEvent {
    /** The screen went off or the lock screen came up: the sound should carry on. */
    ScreenOff,

    /** Home, Recents or another app, with the screen on and unlocked. */
    LeftApp,

    /** The tracks sheet's own file picker covers the app; the video plays on, as before V8. */
    PickingFile,
}

/** What the video does when the app goes out of view. */
enum class BackgroundAction {
    /** Keep playing; only the picture goes (no views, no video decoding) until the app is back. */
    KeepAudio,

    /** Pause it. V10 turns "left the app while playing" into picture-in-picture instead. */
    Pause,

    /** Leave it as it is (it was not playing). */
    None,
}

/**
 * Locking the phone during a video keeps its sound (Shibi, 2026-10-17); leaving the app
 * pauses it until picture-in-picture lands in V10. A paused video stays paused either way.
 * Pure: the activity reports the event, the video view model applies the action.
 */
object BackgroundPolicy {

    fun decide(event: BackgroundEvent, videoPlaying: Boolean): BackgroundAction = when {
        !videoPlaying -> BackgroundAction.None
        event == BackgroundEvent.LeftApp -> BackgroundAction.Pause // V10: EnterPip.
        else -> BackgroundAction.KeepAudio
    }

    /**
     * `onStop` with the screen off ([interactive] false) or the keyguard up is a lock; with
     * the app's own file picker on top ([pickingFile]) it is that; otherwise the user left.
     */
    fun eventFor(interactive: Boolean, keyguardLocked: Boolean, pickingFile: Boolean = false): BackgroundEvent = when {
        !interactive || keyguardLocked -> BackgroundEvent.ScreenOff
        pickingFile -> BackgroundEvent.PickingFile
        else -> BackgroundEvent.LeftApp
    }
}
