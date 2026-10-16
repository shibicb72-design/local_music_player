package com.shibi.musicplayer

import com.shibi.musicplayer.domain.NavWheelMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NavWheelMathTest {
    private val math = NavWheelMath(itemCount = 4)

    @Test
    fun stepDegreesSplitsTheCircleEvenly() {
        assertEquals(90f, math.stepDegrees, 0.001f)
        assertEquals(120f, NavWheelMath(3).stepDegrees, 0.001f)
    }

    @Test
    fun constructorRejectsASingleItem() {
        assertThrows(IllegalArgumentException::class.java) {
            NavWheelMath(1)
        }
    }

    @Test
    fun normalizeWrapsNegativeAndFullTurns() {
        assertEquals(0f, math.normalize(0f), 0.001f)
        assertEquals(0f, math.normalize(4f), 0.001f)
        assertEquals(1.5f, math.normalize(5.5f), 0.001f)
        assertEquals(3.5f, math.normalize(-0.5f), 0.001f)
    }

    @Test
    fun selectedIndexFollowsTheNearestDetentAndWraps() {
        assertEquals(0, math.selectedIndex(0f))
        assertEquals(1, math.selectedIndex(0.6f))
        assertEquals(0, math.selectedIndex(4f))
        assertEquals(0, math.selectedIndex(-0.4f))
        assertEquals(3, math.selectedIndex(-0.6f))
    }

    @Test
    fun snapTargetRoundsAndAddsFling() {
        assertEquals(0f, math.snapTarget(0.4f), 0.001f)
        assertEquals(1f, math.snapTarget(0.5f), 0.001f)
        assertEquals(2f, math.snapTarget(0.2f, flingItems = 1.6f), 0.001f)
        assertEquals(-1f, math.snapTarget(0.2f, flingItems = -0.9f), 0.001f)
    }

    @Test
    fun rotationForIndexTakesTheShortestSpin() {
        assertEquals(1f, math.rotationForIndex(0f, 1), 0.001f)
        assertEquals(-1f, math.rotationForIndex(0f, 3), 0.001f)
        assertEquals(-2f, math.rotationForIndex(0f, 2), 0.001f)
        assertEquals(5f, math.rotationForIndex(5.2f, 1), 0.001f)
    }

    @Test
    fun rotationForIndexRejectsAnUnknownIndex() {
        assertThrows(IllegalArgumentException::class.java) {
            math.rotationForIndex(0f, 4)
        }
    }

    @Test
    fun offsetDegreesPlacesNeighborsOnEitherSideOfTheTop() {
        assertEquals(0f, math.offsetDegrees(0, 0f), 0.001f)
        assertEquals(90f, math.offsetDegrees(1, 0f), 0.001f)
        assertEquals(-90f, math.offsetDegrees(3, 0f), 0.001f)
        assertEquals(180f, math.offsetDegrees(2, 0f), 0.001f)
        assertEquals(-45f, math.offsetDegrees(0, 0.5f), 0.001f)
    }

    @Test
    fun offsetDegreesIsContinuousAcrossTheBackOfTheWheel() {
        val before = math.position(math.offsetDegrees(2, 0.01f), 100f)
        val after = math.position(math.offsetDegrees(2, -0.01f), 100f)
        assertEquals(before.x, after.x, 5f)
        assertEquals(before.y, after.y, 5f)
    }

    @Test
    fun scaleAndAlphaShrinkAwayFromTheTop() {
        assertEquals(1f, math.scale(0f), 0.001f)
        assertEquals(NavWheelMath.MIN_SCALE, math.scale(180f), 0.001f)
        assertEquals(1f, math.alpha(0f), 0.001f)
        assertEquals(NavWheelMath.MIN_ALPHA, math.alpha(180f), 0.001f)
        assertTrue(math.scale(0f) > math.scale(45f))
        assertTrue(math.scale(45f) > math.scale(90f))
        assertTrue(math.scale(90f) > math.scale(180f))
        assertTrue(math.alpha(0f) > math.alpha(90f))
        assertTrue(math.alpha(90f) > math.alpha(180f))
    }

    @Test
    fun rimMarksRotateWithTheWheelAndMatchItemOrder() {
        assertEquals(0f, math.rimOffsetDegrees(0, 60, 0f), 0.001f)
        assertEquals(6f, math.rimOffsetDegrees(1, 60, 0f), 0.001f)
        assertEquals(-90f, math.rimOffsetDegrees(0, 60, 1f), 0.001f)
        assertEquals(math.offsetDegrees(0, 0.5f), math.rimOffsetDegrees(0, 4, 0.5f), 0.001f)
        assertEquals(math.offsetDegrees(2, 0.25f), math.rimOffsetDegrees(2, 4, 0.25f), 0.001f)
    }

    @Test
    fun rimMarksRejectAnEmptyRim() {
        assertThrows(IllegalArgumentException::class.java) {
            math.rimOffsetDegrees(0, 0, 0f)
        }
    }

    @Test
    fun positionPutsZeroDegreesAtTheTopAndPositiveDegreesClockwise() {
        val top = math.position(0f, 100f)
        val right = math.position(90f, 100f)
        val bottom = math.position(180f, 100f)
        val left = math.position(-90f, 100f)
        assertEquals(0f, top.x, 0.001f)
        assertEquals(-100f, top.y, 0.001f)
        assertEquals(100f, right.x, 0.001f)
        assertEquals(0f, right.y, 0.001f)
        assertEquals(0f, bottom.x, 0.001f)
        assertEquals(100f, bottom.y, 0.001f)
        assertEquals(-100f, left.x, 0.001f)
        assertEquals(0f, left.y, 0.001f)
    }

    @Test
    fun flingItemsFollowsTheFingerAndCapsTheCoast() {
        assertEquals(0f, math.flingItems(0f, 100f), 0.001f)
        assertEquals(0f, math.flingItems(500f, 0f), 0.001f)
        assertEquals(-1f, math.flingItems(velocityPxPerSec = 400f, pixelsPerItem = 100f), 0.001f)
        assertEquals(1f, math.flingItems(velocityPxPerSec = -400f, pixelsPerItem = 100f), 0.001f)
        assertEquals(-NavWheelMath.MAX_FLING_ITEMS, math.flingItems(100_000f, 100f), 0.001f)
    }
}
