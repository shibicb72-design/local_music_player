package com.shibi.musicplayer

import com.shibi.musicplayer.domain.ResumePolicy
import com.shibi.musicplayer.domain.VideoProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumePolicyTest {

    private val tenMinutes = 600_000L

    @Test
    fun resumesOnlyPastTheFirstFiveSeconds() {
        assertEquals(0L, ResumePolicy.startAt(VideoProgress(0L, tenMinutes), null))
        assertEquals(0L, ResumePolicy.startAt(VideoProgress(4_999L, tenMinutes), null))
        assertEquals(0L, ResumePolicy.startAt(VideoProgress(5_000L, tenMinutes), null))
        assertEquals(5_001L, ResumePolicy.startAt(VideoProgress(5_001L, tenMinutes), null))
        assertEquals(754_000L, ResumePolicy.startAt(VideoProgress(754_000L, 3_600_000L), null))
    }

    @Test
    fun theLastFivePercentStartsOver() {
        // 95% of 10:00 is 9:30.
        assertEquals(569_999L, ResumePolicy.startAt(VideoProgress(569_999L, tenMinutes), null))
        assertEquals(0L, ResumePolicy.startAt(VideoProgress(570_000L, tenMinutes), null))
        assertEquals(0L, ResumePolicy.startAt(VideoProgress(tenMinutes, tenMinutes), null))
        assertTrue(ResumePolicy.isFinished(570_000L, tenMinutes))
        assertFalse(ResumePolicy.isFinished(569_999L, tenMinutes))
    }

    @Test
    fun nothingSavedStartsAtZero() {
        assertEquals(0L, ResumePolicy.startAt(null, tenMinutes))
    }

    @Test
    fun anUnknownLengthStillResumesAndMediaStoreFillsIn() {
        // libVLC never reported a length: no end rule, the position alone decides.
        assertEquals(42_000L, ResumePolicy.startAt(VideoProgress(42_000L, 0L), null))
        // MediaStore's length applies when none was saved.
        assertEquals(0L, ResumePolicy.startAt(VideoProgress(580_000L, 0L), tenMinutes))
        // The saved (libVLC) length wins over MediaStore's.
        assertEquals(580_000L, ResumePolicy.startAt(VideoProgress(580_000L, 1_200_000L), tenMinutes))
    }

    @Test
    fun finishingClearsThePosition() {
        assertEquals(0L, ResumePolicy.positionToStore(570_000L, tenMinutes))
        assertEquals(0L, ResumePolicy.positionToStore(tenMinutes, tenMinutes))
        assertEquals(569_000L, ResumePolicy.positionToStore(569_000L, tenMinutes))
        // Short positions are stored as they are; they just do not resume.
        assertEquals(3_000L, ResumePolicy.positionToStore(3_000L, tenMinutes))
        assertEquals(0L, ResumePolicy.positionToStore(-5L, tenMinutes))
        // No length known: never treated as finished.
        assertEquals(900_000L, ResumePolicy.positionToStore(900_000L, 0L))
    }

    @Test
    fun startOverIsAClearedPositionThatDoesNotResume() {
        // Start over stores position 0; the next open starts at 0 with no chip.
        val cleared = VideoProgress(ResumePolicy.positionToStore(0L, tenMinutes), tenMinutes)
        assertEquals(0L, ResumePolicy.startAt(cleared, null))
        assertNull(ResumePolicy.watchedFraction(cleared, null))
    }

    @Test
    fun theHairlineShowsOnlyForVideosThatWouldResume() {
        assertEquals(0.5f, ResumePolicy.watchedFraction(VideoProgress(300_000L, tenMinutes), null)!!, 0.0001f)
        assertEquals(0.25f, ResumePolicy.watchedFraction(VideoProgress(150_000L, 0L), tenMinutes)!!, 0.0001f)
        assertNull(ResumePolicy.watchedFraction(VideoProgress(3_000L, tenMinutes), null))
        assertNull(ResumePolicy.watchedFraction(VideoProgress(590_000L, tenMinutes), null))
        // Nothing to measure against.
        assertNull(ResumePolicy.watchedFraction(VideoProgress(300_000L, 0L), null))
        assertNull(ResumePolicy.watchedFraction(null, tenMinutes))
    }

    @Test
    fun savesEveryFiveSecondsWhilePlaying() {
        assertFalse(ResumePolicy.isSaveDue(nowMs = 4_999L, lastSavedAtMs = 0L))
        assertTrue(ResumePolicy.isSaveDue(nowMs = 5_000L, lastSavedAtMs = 0L))
        assertTrue(ResumePolicy.isSaveDue(nowMs = 17_000L, lastSavedAtMs = 10_000L))
    }

    @Test
    fun aMissingMediaStoreDurationUsesTheSavedLength() {
        assertEquals(754_000L, ResumePolicy.displayDuration(754_000L, VideoProgress(0L, 999_000L)))
        assertEquals(999_000L, ResumePolicy.displayDuration(null, VideoProgress(0L, 999_000L)))
        assertNull(ResumePolicy.displayDuration(null, VideoProgress(10_000L, 0L)))
        assertNull(ResumePolicy.displayDuration(null, null))
    }
}
