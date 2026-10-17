package com.shibi.musicplayer

import com.shibi.musicplayer.domain.BackgroundAction
import com.shibi.musicplayer.domain.BackgroundEvent
import com.shibi.musicplayer.domain.BackgroundPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundPolicyTest {

    @Test
    fun theDecisionTable() {
        // event, playing -> action
        val table = listOf(
            Triple(BackgroundEvent.ScreenOff, true, BackgroundAction.KeepAudio),
            Triple(BackgroundEvent.ScreenOff, false, BackgroundAction.None),
            Triple(BackgroundEvent.LeftApp, true, BackgroundAction.Pause),
            Triple(BackgroundEvent.LeftApp, false, BackgroundAction.None),
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
    fun leavingTheAppPausesUntilPictureInPictureLands() {
        // V10 maps this to picture-in-picture; until then a video never plays unseen by choice.
        assertEquals(BackgroundAction.Pause, BackgroundPolicy.decide(BackgroundEvent.LeftApp, videoPlaying = true))
    }
}
