package com.shibi.musicplayer

import com.shibi.musicplayer.domain.BrightnessMath
import com.shibi.musicplayer.domain.FullscreenGestures
import com.shibi.musicplayer.domain.SwipeTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenGesturesTest {

    private val width = 900f

    @Test
    fun doubleTapOnTheLeftThirdGoesBackTenSeconds() {
        assertEquals(-10_000L, FullscreenGestures.doubleTapSeekMs(0f, width))
        assertEquals(-10_000L, FullscreenGestures.doubleTapSeekMs(299f, width))
    }

    @Test
    fun doubleTapOnTheRightThirdGoesForwardTenSeconds() {
        assertEquals(10_000L, FullscreenGestures.doubleTapSeekMs(601f, width))
        assertEquals(10_000L, FullscreenGestures.doubleTapSeekMs(width, width))
    }

    @Test
    fun doubleTapInTheMiddleThirdDoesNotSeek() {
        assertEquals(0L, FullscreenGestures.doubleTapSeekMs(300f, width))
        assertEquals(0L, FullscreenGestures.doubleTapSeekMs(450f, width))
        assertEquals(0L, FullscreenGestures.doubleTapSeekMs(600f, width))
        assertEquals(0L, FullscreenGestures.doubleTapSeekMs(100f, 0f))
    }

    @Test
    fun leftHalfIsBrightnessAndRightHalfIsVolume() {
        assertEquals(SwipeTarget.Brightness, FullscreenGestures.swipeTarget(10f, width))
        assertEquals(SwipeTarget.Brightness, FullscreenGestures.swipeTarget(449f, width))
        assertEquals(SwipeTarget.Volume, FullscreenGestures.swipeTarget(450f, width))
        assertEquals(SwipeTarget.Volume, FullscreenGestures.swipeTarget(890f, width))
    }

    @Test
    fun theDragAxisIsTheLargerMovement() {
        assertTrue(FullscreenGestures.isHorizontal(12f, -5f))
        assertFalse(FullscreenGestures.isHorizontal(3f, 9f))
        // A perfect diagonal is a vertical swipe: volume and brightness are the common case.
        assertFalse(FullscreenGestures.isHorizontal(5f, 5f))
    }

    @Test
    fun aHorizontalDragPreviewsAndClampsTheSeek() {
        // Full width is 90 s.
        assertEquals(105_000L, FullscreenGestures.seekPreview(60_000L, 600_000L, width / 2, width))
        assertEquals(15_000L, FullscreenGestures.seekPreview(60_000L, 600_000L, -width / 2, width))
        assertEquals(0L, FullscreenGestures.seekPreview(20_000L, 600_000L, -width, width))
        assertEquals(600_000L, FullscreenGestures.seekPreview(590_000L, 600_000L, width, width))
        // Unknown length or a zero-width area: stay where it is.
        assertEquals(60_000L, FullscreenGestures.seekPreview(60_000L, 0L, width, width))
        assertEquals(60_000L, FullscreenGestures.seekPreview(60_000L, 600_000L, width, 0f))
    }

    @Test
    fun fullWidthIsNinetySecondsOrTheWholeShortClip() {
        assertEquals(30_000L, FullscreenGestures.swipeFullWidthMs(30_000L))
        assertEquals(90_000L, FullscreenGestures.swipeFullWidthMs(90_000L))
        assertEquals(90_000L, FullscreenGestures.swipeFullWidthMs(7_200_000L))
        assertEquals(90_000L, FullscreenGestures.swipeFullWidthMs(0L))
    }

    @Test
    fun aFullWidthDragOnAShortClipReachesTheEnd() {
        assertEquals(30_000L, FullscreenGestures.seekPreview(0L, 30_000L, width, width))
        assertEquals(15_000L, FullscreenGestures.seekPreview(0L, 30_000L, width / 2, width))
        assertEquals(0L, FullscreenGestures.seekPreview(30_000L, 30_000L, -width, width))
    }

    @Test
    fun theOffsetLabelShowsTheJumpAndTheTarget() {
        assertEquals("+0:45 · 2:13", FullscreenGestures.seekOffsetLabel(45_000L, 133_000L))
        assertEquals("-1:05 · 0:08", FullscreenGestures.seekOffsetLabel(-65_000L, 8_000L))
        assertEquals("+0:30 · 1:02:03", FullscreenGestures.seekOffsetLabel(30_000L, 3_723_000L))
    }

    @Test
    fun theGestureEdgesAreTheOuterStrips() {
        assertTrue(FullscreenGestures.startsInGestureEdge(10f, 1000f, 48f, 48f))
        assertTrue(FullscreenGestures.startsInGestureEdge(990f, 1000f, 48f, 48f))
        assertFalse(FullscreenGestures.startsInGestureEdge(48f, 1000f, 48f, 48f))
        assertFalse(FullscreenGestures.startsInGestureEdge(500f, 1000f, 48f, 48f))
    }

    @Test
    fun theSeekChipShowsTheSignedJump() {
        assertEquals("+0:45", FullscreenGestures.seekDelta(45_000L))
        assertEquals("-1:05", FullscreenGestures.seekDelta(-65_000L))
        assertEquals("+0:00", FullscreenGestures.seekDelta(400L))
    }

    @Test
    fun aFullHeightSwipeCoversTheWholeBrightnessRange() {
        assertEquals(1f, BrightnessMath.afterDrag(0f, 1_000f, 1_000f), 0.0001f)
        assertEquals(BrightnessMath.Min, BrightnessMath.afterDrag(1f, -1_000f, 1_000f), 0.0001f)
        assertEquals(0.75f, BrightnessMath.afterDrag(0.5f, 250f, 1_000f), 0.0001f)
    }

    @Test
    fun brightnessNeverGoesFullyDarkOrPastFull() {
        assertEquals(BrightnessMath.Min, BrightnessMath.afterDrag(0.2f, -5_000f, 1_000f), 0.0001f)
        assertEquals(1f, BrightnessMath.afterDrag(0.9f, 5_000f, 1_000f), 0.0001f)
        assertEquals(0.4f, BrightnessMath.afterDrag(0.4f, 300f, 0f), 0.0001f)
        assertTrue(BrightnessMath.Min > 0f)
    }

    @Test
    fun theStartComesFromTheWindowThenTheSystemSetting() {
        assertEquals(0.3f, BrightnessMath.start(windowBrightness = 0.3f, systemSetting = 200), 0.0001f)
        // -1 is "follow the system": read the 0..255 setting.
        assertEquals(1f, BrightnessMath.start(windowBrightness = -1f, systemSetting = 255), 0.0001f)
        assertEquals(0.5f, BrightnessMath.start(windowBrightness = -1f, systemSetting = null), 0.0001f)
        assertEquals(1f, BrightnessMath.start(windowBrightness = -1f, systemSetting = 900), 0.0001f)
    }

    @Test
    fun theChipShowsAPercentage() {
        assertEquals("60%", BrightnessMath.label(0.6f))
        assertEquals("1%", BrightnessMath.label(BrightnessMath.Min))
        assertEquals("100%", BrightnessMath.label(1f))
    }
}
