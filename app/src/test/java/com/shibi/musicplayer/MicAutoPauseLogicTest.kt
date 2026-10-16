package com.shibi.musicplayer

import com.shibi.musicplayer.playback.MicAutoPauseLogic
import com.shibi.musicplayer.playback.MicAutoPauseLogic.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class MicAutoPauseLogicTest {
    private val logic = MicAutoPauseLogic()

    @Test
    fun outroRecording_pausesWhilePlaying() {
        assertEquals(Action.PAUSE, logic.onRecordingChanged(othersRecording = true, isPlaying = true))
    }

    @Test
    fun recordingStart_doesNotPauseWhenAlreadyPaused() {
        assertEquals(Action.NONE, logic.onRecordingChanged(othersRecording = true, isPlaying = false))
    }

    @Test
    fun recordingEnd_resumesAfterAutoPause() {
        assertEquals(Action.PAUSE, logic.onRecordingChanged(othersRecording = true, isPlaying = true))

        assertEquals(Action.RESUME, logic.onRecordingChanged(othersRecording = false, isPlaying = false))
    }

    @Test
    fun manualPause_isNotOverriddenByRecordingEnd() {
        logic.onUserPlayPauseIntent()

        assertEquals(Action.NONE, logic.onRecordingChanged(othersRecording = false, isPlaying = false))
    }

    @Test
    fun userIntentDuringAutoPause_cancelsTheResume() {
        assertEquals(Action.PAUSE, logic.onRecordingChanged(othersRecording = true, isPlaying = true))

        logic.onUserPlayPauseIntent()

        assertEquals(Action.NONE, logic.onRecordingChanged(othersRecording = false, isPlaying = false))
    }

    @Test
    fun repeatedEvents_doNotDoubleAct() {
        assertEquals(Action.PAUSE, logic.onRecordingChanged(othersRecording = true, isPlaying = true))
        assertEquals(Action.NONE, logic.onRecordingChanged(othersRecording = true, isPlaying = true))

        assertEquals(Action.NONE, logic.onRecordingChanged(othersRecording = true, isPlaying = false))

        assertEquals(Action.RESUME, logic.onRecordingChanged(othersRecording = false, isPlaying = false))
        assertEquals(Action.NONE, logic.onRecordingChanged(othersRecording = false, isPlaying = false))
    }
}
