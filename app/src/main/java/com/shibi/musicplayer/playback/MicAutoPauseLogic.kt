package com.shibi.musicplayer.playback

class MicAutoPauseLogic {
    enum class Action {
        NONE,
        PAUSE,
        RESUME,
    }

    private var autoPaused = false

    fun onRecordingChanged(othersRecording: Boolean, isPlaying: Boolean): Action {
        if (othersRecording) {
            if (isPlaying && !autoPaused) {
                autoPaused = true
                return Action.PAUSE
            }
            return Action.NONE
        }
        if (autoPaused) {
            autoPaused = false
            return Action.RESUME
        }
        return Action.NONE
    }

    fun onUserPlayPauseIntent() {
        autoPaused = false
    }
}
