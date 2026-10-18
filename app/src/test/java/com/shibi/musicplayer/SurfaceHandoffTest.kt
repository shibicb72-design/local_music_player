package com.shibi.musicplayer

import com.shibi.musicplayer.video.SurfaceHandoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceHandoffTest {

    @Test
    fun switchingFromOneViewToAnotherWithARunningTrackDisablesFirst() {
        assertTrue(
            SurfaceHandoff.disableBeforeSwitch(
                hadView = true,
                mediaLoaded = true,
                currentTrack = 1,
                pictureOff = false,
            ),
        )
    }

    @Test
    fun firstAttachOfAFreshLoadDoesNotDisable() {
        assertFalse(
            SurfaceHandoff.disableBeforeSwitch(
                hadView = false,
                mediaLoaded = false,
                currentTrack = SurfaceHandoff.NoTrack,
                pictureOff = false,
            ),
        )
    }

    @Test
    fun mediaNotLoadedDoesNotDisable() {
        assertFalse(
            SurfaceHandoff.disableBeforeSwitch(
                hadView = false,
                mediaLoaded = false,
                currentTrack = 1,
                pictureOff = false,
            ),
        )
        assertFalse(
            SurfaceHandoff.disableBeforeSwitch(
                hadView = true,
                mediaLoaded = false,
                currentTrack = 1,
                pictureOff = false,
            ),
        )
    }

    @Test
    fun pictureOffScreenLockedNeverDisables() {
        assertFalse(
            SurfaceHandoff.disableBeforeSwitch(
                hadView = true,
                mediaLoaded = true,
                currentTrack = 1,
                pictureOff = true,
            ),
        )
        assertFalse(
            SurfaceHandoff.disableBeforeSwitch(
                hadView = false,
                mediaLoaded = true,
                currentTrack = 1,
                pictureOff = true,
            ),
        )
    }

    @Test
    fun runningTrackWithNoViewAfterALockEndedDisables() {
        assertTrue(
            SurfaceHandoff.disableBeforeSwitch(
                hadView = false,
                mediaLoaded = true,
                currentTrack = 1,
                pictureOff = false,
            ),
        )
    }

    @Test
    fun trackToSaveKeepsTheRunningIdAndFallsBackToSaved() {
        assertEquals(3, SurfaceHandoff.trackToSave(currentTrack = 3, alreadySaved = 5))
        assertEquals(5, SurfaceHandoff.trackToSave(currentTrack = SurfaceHandoff.NoTrack, alreadySaved = 5))
        assertNull(SurfaceHandoff.trackToSave(currentTrack = SurfaceHandoff.NoTrack, alreadySaved = null))
    }

    @Test
    fun restoreOnSurfaceReadyOnlyWhenPendingPictureOnAndViewAttached() {
        assertTrue(SurfaceHandoff.restoreOnSurfaceReady(pending = true, pictureOff = false, hasView = true))
        assertFalse(SurfaceHandoff.restoreOnSurfaceReady(pending = false, pictureOff = false, hasView = true))
        assertFalse(SurfaceHandoff.restoreOnSurfaceReady(pending = true, pictureOff = true, hasView = true))
        assertFalse(SurfaceHandoff.restoreOnSurfaceReady(pending = true, pictureOff = false, hasView = false))
    }

    @Test
    fun reopenAfterErrorOnlyWhenPlayedNotRetriedAndNotReleased() {
        assertTrue(SurfaceHandoff.reopenAfterError(positionMs = 7_000L, retried = false, released = false))
        assertFalse(SurfaceHandoff.reopenAfterError(positionMs = 0L, retried = false, released = false))
        assertFalse(SurfaceHandoff.reopenAfterError(positionMs = 7_000L, retried = true, released = false))
        assertFalse(SurfaceHandoff.reopenAfterError(positionMs = 7_000L, retried = false, released = true))
    }
}
