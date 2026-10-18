package com.shibi.musicplayer

import com.shibi.musicplayer.domain.PipAspect
import com.shibi.musicplayer.domain.PipRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipAspectTest {

    private fun ratio(r: PipRatio): Double = r.numerator.toDouble() / r.denominator.toDouble()

    @Test
    fun aSixteenByNineAspectKeepsItself() {
        assertEquals(PipRatio(1777, 1000), PipAspect.ratioFor(0, 0, 1.777f))
    }

    @Test
    fun videoDimensionsAreTheFallback() {
        val result = PipAspect.ratioFor(1920, 1080, null)
        assertEquals(16.0 / 9.0, ratio(result), 0.001)
    }

    @Test
    fun aCinemaAspectIsClampedToTheWidest() {
        assertEquals(PipRatio(239, 100), PipAspect.ratioFor(0, 0, 2.6f))
    }

    @Test
    fun aVeryTallAspectIsClampedToTheTallest() {
        assertEquals(PipRatio(100, 239), PipAspect.ratioFor(0, 0, 0.2f))
    }

    @Test
    fun exactlyTheWidestEdgeIsAllowed() {
        val result = PipAspect.ratioFor(0, 0, 2.39f)
        assertEquals(PipRatio(2390, 1000), result)
        assertEquals(2.39, ratio(result), 0.001)
    }

    @Test
    fun unknownDimensionsFallBackToSixteenByNine() {
        assertEquals(PipRatio(16, 9), PipAspect.ratioFor(0, 0, null))
    }

    @Test
    fun aNaNAspectFallsBackToTheVideoDimensions() {
        val result = PipAspect.ratioFor(1920, 1080, Float.NaN)
        assertEquals(16.0 / 9.0, ratio(result), 0.001)
    }

    @Test
    fun aPortraitVideoKeepsItsPortraitRatio() {
        val result = PipAspect.ratioFor(1080, 1920, null)
        assertEquals(0.5625, ratio(result), 0.001)
    }

    @Test
    fun aRatioJustInsideTheBandIsNotRoundedOutsideIt() {
        val result = PipAspect.ratioFor(0, 0, 0.41845f)
        assertTrue(ratio(result) >= 100.0 / 239.0)
    }

    @Test
    fun aRatioJustBelowTheWidestEdgeStaysInside() {
        val result = PipAspect.ratioFor(0, 0, 2.3896f)
        assertTrue(ratio(result) <= 2.39)
    }
}
