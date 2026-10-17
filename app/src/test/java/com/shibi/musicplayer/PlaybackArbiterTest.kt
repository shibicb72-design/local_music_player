package com.shibi.musicplayer

import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.domain.PlaybackArbiter
import com.shibi.musicplayer.domain.VideoAudioEvent
import com.shibi.musicplayer.domain.VideoFocusPolicy
import com.shibi.musicplayer.domain.VideoFocusPolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackArbiterTest {

    @Test
    fun videoStartPausesPlayingMusic() {
        val decision = PlaybackArbiter.onVideoStart(musicPlaying = true)
        assertTrue(decision.pauseMusic)
        assertFalse(decision.closeVideo)
        assertFalse(decision.resumeMusic)
    }

    @Test
    fun videoStartLeavesPausedMusicAlone() {
        assertEquals(PlaybackArbiter.Decision(), PlaybackArbiter.onVideoStart(musicPlaying = false))
    }

    @Test
    fun songTapClosesTheVideo() {
        assertTrue(PlaybackArbiter.onMusicStart(videoActive = true).closeVideo)
        assertFalse(PlaybackArbiter.onMusicStart(videoActive = true).pauseMusic)
        assertFalse(PlaybackArbiter.onMusicStart(videoActive = false).closeVideo)
    }

    @Test
    fun onlyTheRisingEdgeOfMusicClosesTheVideo() {
        // Paused to playing (notification, lock screen): close.
        assertTrue(PlaybackArbiter.onMusicPlayingChanged(false, true, videoActive = true).closeVideo)
        // The pause the video caused, and music that keeps playing or stays paused: no change.
        assertFalse(PlaybackArbiter.onMusicPlayingChanged(true, false, videoActive = true).closeVideo)
        assertFalse(PlaybackArbiter.onMusicPlayingChanged(true, true, videoActive = true).closeVideo)
        assertFalse(PlaybackArbiter.onMusicPlayingChanged(false, false, videoActive = true).closeVideo)
        // No video, nothing to close.
        assertFalse(PlaybackArbiter.onMusicPlayingChanged(false, true, videoActive = false).closeVideo)
    }

    @Test
    fun musicDoesNotResumeWhenTheVideoEnds() {
        assertFalse(PlaybackArbiter.ResumeMusicAfterVideo)
        assertFalse(PlaybackArbiter.onVideoEnded(musicPausedByVideo = true).resumeMusic)
        assertFalse(PlaybackArbiter.onVideoEnded(musicPausedByVideo = false).resumeMusic)
        assertEquals(PlaybackArbiter.Decision(), PlaybackArbiter.onVideoEnded(musicPausedByVideo = true))
    }

    @Test
    fun permanentLossAndNoisyPauseWithoutResuming() {
        for (event in listOf(VideoAudioEvent.Loss, VideoAudioEvent.BecomingNoisy)) {
            val playing = VideoFocusPolicy.decide(event, isPlaying = true, pausedForTransientLoss = false)
            assertEquals(Action.Pause, playing.action)
            assertFalse(playing.pausedForTransientLoss)
            // A later gain must not restart it.
            assertEquals(Action.None, VideoFocusPolicy.decide(VideoAudioEvent.Gain, false, playing.pausedForTransientLoss).action)
            assertEquals(Action.None, VideoFocusPolicy.decide(event, isPlaying = false, pausedForTransientLoss = false).action)
        }
    }

    @Test
    fun permanentLossForgetsAnEarlierTransientPause() {
        val result = VideoFocusPolicy.decide(VideoAudioEvent.Loss, isPlaying = false, pausedForTransientLoss = true)
        assertFalse(result.pausedForTransientLoss)
    }

    @Test
    fun transientLossPausesAndGainResumes() {
        val lost = VideoFocusPolicy.decide(VideoAudioEvent.LossTransient, isPlaying = true, pausedForTransientLoss = false)
        assertEquals(Action.Pause, lost.action)
        assertTrue(lost.pausedForTransientLoss)
        val gained = VideoFocusPolicy.decide(VideoAudioEvent.Gain, isPlaying = false, lost.pausedForTransientLoss)
        assertEquals(Action.Resume, gained.action)
        assertFalse(gained.pausedForTransientLoss)
    }

    @Test
    fun transientLossWhileUserPausedDoesNotResume() {
        val lost = VideoFocusPolicy.decide(VideoAudioEvent.LossTransient, isPlaying = false, pausedForTransientLoss = false)
        assertEquals(Action.None, lost.action)
        assertFalse(lost.pausedForTransientLoss)
        assertEquals(Action.None, VideoFocusPolicy.decide(VideoAudioEvent.Gain, false, lost.pausedForTransientLoss).action)
    }

    @Test
    fun duckingKeepsPlaying() {
        val result = VideoFocusPolicy.decide(VideoAudioEvent.LossTransientCanDuck, isPlaying = true, pausedForTransientLoss = false)
        assertEquals(Action.None, result.action)
    }

    @Test
    fun inlineBoxIsSixteenByNineUntilKnown() {
        assertEquals(202.5f, InlineVideoBox.height(360f, 1000f, aspect = null), 0.01f)
        assertEquals(202.5f, InlineVideoBox.height(360f, 1000f, aspect = Float.NaN), 0.01f)
        assertEquals(202.5f, InlineVideoBox.height(360f, 1000f, aspect = 0f), 0.01f)
    }

    @Test
    fun inlineBoxIsCappedAtHalfTheViewport() {
        // 4:3 on a 411dp phone wants 308dp; a 500dp viewport caps it at 250dp.
        assertEquals(250f, InlineVideoBox.height(411f, 500f, aspect = 4f / 3f), 0.01f)
        // Portrait 9:16 always hits the cap.
        assertEquals(320f, InlineVideoBox.height(411f, 640f, aspect = 9f / 16f), 0.01f)
        // Cinema 2.39:1 stays at its natural height.
        assertEquals(171.97f, InlineVideoBox.height(411f, 800f, aspect = 2.39f), 0.01f)
        assertEquals(0f, InlineVideoBox.height(411f, 0f, aspect = 1f), 0.01f)
    }

    @Test
    fun displayAspectUsesSampleRatioAndRotation() {
        assertEquals(16f / 9f, InlineVideoBox.displayAspect(1920, 1080)!!, 0.0001f)
        // Anamorphic DVD: 720x576 with 16:15 pixels shows 4:3.
        assertEquals(4f / 3f, InlineVideoBox.displayAspect(720, 576, 16, 15)!!, 0.0001f)
        // A phone recording stored landscape and rotated 90 degrees plays portrait.
        assertEquals(9f / 16f, InlineVideoBox.displayAspect(1920, 1080, rotated = true)!!, 0.0001f)
        // An unknown sample ratio counts as square pixels.
        assertEquals(16f / 9f, InlineVideoBox.displayAspect(1920, 1080, 0, 0)!!, 0.0001f)
        assertNull(InlineVideoBox.displayAspect(0, 1080))
        assertNull(InlineVideoBox.displayAspect(1920, -1))
    }
}
