package com.shibi.musicplayer

import com.shibi.musicplayer.domain.CarouselMath
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselMathTest {
    private val math = CarouselMath(itemCount = 8)

    @Test
    fun stepSplitsTheRingEvenly() {
        assertEquals((2.0 * PI / 8.0).toFloat(), math.stepRadians, 0.001f)
        assertEquals((2.0 * PI / 3.0).toFloat(), CarouselMath(3).stepRadians, 0.001f)
    }

    @Test
    fun constructorRejectsASingleItem() {
        assertThrows(IllegalArgumentException::class.java) {
            CarouselMath(1)
        }
    }

    @Test
    fun normalizeWrapsNegativeAndFullTurns() {
        assertEquals(0f, math.normalize(0f), 0.001f)
        assertEquals(0f, math.normalize(8f), 0.001f)
        assertEquals(1.5f, math.normalize(9.5f), 0.001f)
        assertEquals(7.5f, math.normalize(-0.5f), 0.001f)
    }

    @Test
    fun selectedIndexFollowsTheNearestDetentAndWraps() {
        assertEquals(0, math.selectedIndex(0f))
        assertEquals(1, math.selectedIndex(0.6f))
        assertEquals(0, math.selectedIndex(8f))
        assertEquals(0, math.selectedIndex(-0.4f))
        assertEquals(7, math.selectedIndex(-0.6f))
    }

    @Test
    fun snapTargetRoundsToTheNearestItem() {
        assertEquals(0f, math.snapTarget(0.4f), 0.001f)
        assertEquals(1f, math.snapTarget(0.5f), 0.001f)
        assertEquals(-1f, math.snapTarget(-0.6f), 0.001f)
    }

    @Test
    fun rotationForIndexTakesTheShortestSpin() {
        assertEquals(1f, math.rotationForIndex(0f, 1), 0.001f)
        assertEquals(-1f, math.rotationForIndex(0f, 7), 0.001f)
        assertEquals(-4f, math.rotationForIndex(0f, 4), 0.001f)
        assertEquals(9f, math.rotationForIndex(8.2f, 1), 0.001f)
    }

    @Test
    fun rotationForIndexRejectsAnUnknownIndex() {
        assertThrows(IllegalArgumentException::class.java) {
            math.rotationForIndex(0f, 8)
        }
    }

    @Test
    fun offsetPutsTheSelectedItemAtTheFrontAndNeighborsOnEitherSide() {
        assertEquals(0f, math.offsetRadians(0, 0f), 0.001f)
        assertEquals(math.stepRadians, math.offsetRadians(1, 0f), 0.001f)
        assertEquals(-math.stepRadians, math.offsetRadians(7, 0f), 0.001f)
        assertEquals(PI.toFloat(), math.offsetRadians(4, 0f), 0.001f)
    }

    @Test
    fun translationFollowsAHorizontalRingSeenFromAbove() {
        assertEquals(0f, math.translationX(0f, 100f), 0.001f)
        assertEquals(100f, math.translationX((PI / 2.0).toFloat(), 100f), 0.01f)
        assertTrue(math.translationX(math.stepRadians, 100f) > 0f)
        assertTrue(math.translationX(-math.stepRadians, 100f) < 0f)
        assertTrue(math.translationY(0f, 20f) > math.translationY(PI.toFloat(), 20f))
        assertEquals(1f, math.depth(0f), 0.001f)
        assertEquals(-1f, math.depth(PI.toFloat()), 0.001f)
    }

    @Test
    fun restLayoutIsSymmetricAboutTheFront() {
        val radius = 200f
        val front = math.offsetRadians(0, 0f)
        assertEquals(0f, math.translationX(front, radius), 0.001f)
        assertEquals(0f, math.translationX(front, 48f), 0.001f)
        assertEquals(1f, math.scale(front), 0.001f)
        assertEquals(1f, math.alpha(front), 0.001f)
        assertEquals(0f, math.shade(front), 0.001f)

        for (step in 0 until math.itemCount) {
            val right = math.offsetRadians(step, 0f)
            val left = math.offsetRadians(Math.floorMod(-step, math.itemCount), 0f)
            assertEquals(math.scale(right), math.scale(left), 0.001f)
            assertEquals(math.alpha(right), math.alpha(left), 0.001f)
            assertEquals(
                -math.translationX(right, radius),
                math.translationX(left, radius),
                0.01f,
            )
            assertEquals(-math.rotationYDegrees(right), math.rotationYDegrees(left), 0.01f)
            if (math.scale(right) < CarouselMath.VISIBLE_MIN_SCALE) {
                assertEquals(0f, math.alpha(right), 0.001f)
                assertEquals(0f, math.alpha(left), 0.001f)
            }
        }

        val right = math.offsetRadians(1, 0f)
        val left = math.offsetRadians(math.itemCount - 1, 0f)
        assertTrue(math.scale(right) >= CarouselMath.VISIBLE_MIN_SCALE)
        assertEquals(math.alpha(right), math.alpha(left), 0.001f)
        assertEquals(1f, math.alpha(right), 0.001f)
        assertTrue(math.translationX(right, radius) > 0f)
        assertTrue(math.translationX(left, radius) < 0f)
        assertTrue(math.scale(front) > math.scale(right))
        assertEquals(CarouselMath.MIN_SCALE, math.scale((PI / 2.0).toFloat()), 0.001f)
        assertTrue(math.scale((PI / 2.0).toFloat()) < CarouselMath.VISIBLE_MIN_SCALE)
        assertEquals(0f, math.alpha((PI / 2.0).toFloat()), 0.001f)
        assertEquals(0f, math.alpha((-PI / 2.0).toFloat()), 0.001f)
        assertTrue(math.shade(right) > math.shade(front))
        assertTrue(math.zIndex(front) > math.zIndex(right))

        val visible = (0 until math.itemCount).count { index ->
            math.alpha(math.offsetRadians(index, 0f)) > 0.01f
        }
        assertEquals(3, visible)
    }

    @Test
    fun rotationYTiltsSideItemsAwayFromTheCamera() {
        assertEquals(0f, math.rotationYDegrees(0f), 0.001f)
        assertTrue(math.rotationYDegrees(math.stepRadians) > 0f)
        assertTrue(math.rotationYDegrees(-math.stepRadians) < 0f)
        assertTrue(math.rotationYDegrees(math.stepRadians) < CarouselMath.TILT_DEGREES)
    }

    @Test
    fun dragIsOneToOneAlongTheRing() {
        val radius = 120f
        val arc = radius * math.stepRadians
        assertEquals(-1f, math.dragItems(arc, radius), 0.001f)
        assertEquals(0.5f, math.dragItems(-arc / 2f, radius), 0.001f)
        assertEquals(0f, math.dragItems(40f, 0f), 0.001f)
    }
}
