package com.shibi.musicplayer

import com.shibi.musicplayer.domain.BackgroundAction
import com.shibi.musicplayer.domain.BackgroundEvent
import com.shibi.musicplayer.domain.BackgroundPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPolicyTest {

    @Test
    fun theDecisionTable() {
        // event, playing -> action
        val table = listOf(
            Triple(BackgroundEvent.ScreenOff, true, BackgroundAction.KeepAudio),
            Triple(BackgroundEvent.ScreenOff, false, BackgroundAction.None),
            Triple(BackgroundEvent.LeftApp, true, BackgroundAction.EnterPip),
            Triple(BackgroundEvent.LeftApp, false, BackgroundAction.None),
            Triple(BackgroundEvent.PipDismissed, true, BackgroundAction.Pause),
            Triple(BackgroundEvent.PipDismissed, false, BackgroundAction.None),
            Triple(BackgroundEvent.PickingFile, true, BackgroundAction.KeepAudio),
            Triple(BackgroundEvent.PickingFile, false, BackgroundAction.None),
        )
        table.forEach { (event, playing, expected) ->
            assertEquals("$event, playing=$playing", expected, BackgroundPolicy.decide(event, playing))
        }
    }

    @Test
    fun aDarkScreenOrALockedKeyguardIsAScreenLock() {
        assertEquals(BackgroundEvent.ScreenOff, BackgroundPolicy.eventFor(interactive = false, keyguardLocked = false))
        assertEquals(BackgroundEvent.ScreenOff, BackgroundPolicy.eventFor(interactive = false, keyguardLocked = true))
        // The screen is still on but the lock screen came up (power key, smart lock off).
        assertEquals(BackgroundEvent.ScreenOff, BackgroundPolicy.eventFor(interactive = true, keyguardLocked = true))
    }

    @Test
    fun anUnlockedLitScreenMeansTheUserLeft() {
        assertEquals(BackgroundEvent.LeftApp, BackgroundPolicy.eventFor(interactive = true, keyguardLocked = false))
    }

    @Test
    fun theAppsOwnFilePickerIsNotLeaving() {
        assertEquals(
            BackgroundEvent.PickingFile,
            BackgroundPolicy.eventFor(interactive = true, keyguardLocked = false, pickingFile = true),
        )
        // Locking the phone while the picker is open is still a lock.
        assertEquals(
            BackgroundEvent.ScreenOff,
            BackgroundPolicy.eventFor(interactive = false, keyguardLocked = true, pickingFile = true),
        )
    }

    @Test
    fun anUnlockedLitScreenWhileInPictureInPictureIsADismissal() {
        assertEquals(
            BackgroundEvent.PipDismissed,
            BackgroundPolicy.eventFor(interactive = true, keyguardLocked = false, inPip = true),
        )
    }

    @Test
    fun lockingWhileInPictureInPictureKeepsTheSound() {
        // The lock wins over the dismissal, so locking in PiP does not pause the audio.
        assertEquals(
            BackgroundEvent.ScreenOff,
            BackgroundPolicy.eventFor(interactive = false, keyguardLocked = false, inPip = true),
        )
    }

    @Test
    fun leavingTheAppWithAPlayingVideoEntersPictureInPicture() {
        assertEquals(BackgroundAction.EnterPip, BackgroundPolicy.decide(BackgroundEvent.LeftApp, videoPlaying = true))
    }

    @Test
    fun dismissingTheWindowPausesAPlayingVideo() {
        assertEquals(BackgroundAction.Pause, BackgroundPolicy.decide(BackgroundEvent.PipDismissed, videoPlaying = true))
        assertEquals(BackgroundAction.None, BackgroundPolicy.decide(BackgroundEvent.PipDismissed, videoPlaying = false))
    }

    @Test
    fun onlyAPlayingVideoEntersPictureInPicture() {
        assertTrue(BackgroundPolicy.shouldEnterPip(videoPlaying = true))
        assertFalse(BackgroundPolicy.shouldEnterPip(videoPlaying = false))
    }
}
