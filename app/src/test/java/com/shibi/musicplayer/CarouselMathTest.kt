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
    fun exactlyFourItemsAreVisibleAndScaleFollowsCosine() {
        val front = math.offsetRadians(0, 0f)
        val right = math.offsetRadians(1, 0f)
        val left = math.offsetRadians(7, 0f)
        val peek = math.offsetRadians(2, 0f)
        val leftLimb = math.offsetRadians(6, 0f)
        val back = math.offsetRadians(4, 0f)
        val rear = math.offsetRadians(3, 0f)

        assertEquals(1f, math.scale(front), 0.001f)
        assertEquals(CarouselMath.MIN_SCALE, math.scale((PI / 2.0).toFloat()), 0.001f)
        assertTrue(math.scale(right) in 0.80f..0.95f)
        assertTrue(math.scale(front) > math.scale(right))
        assertTrue(math.scale(right) > math.scale(peek))

        assertEquals(1f, math.alpha(front), 0.001f)
        assertEquals(1f, math.alpha(right), 0.001f)
        assertEquals(1f, math.alpha(left), 0.001f)
        assertTrue(math.alpha(peek) in 0.55f..0.95f)
        assertEquals(0f, math.alpha(leftLimb), 0.001f)
        assertEquals(0f, math.alpha(back), 0.001f)
        assertEquals(0f, math.alpha(rear), 0.001f)
        val visible = (0 until math.itemCount).count { index ->
            math.alpha(math.offsetRadians(index, 0f)) > 0.01f
        }
        assertEquals(4, visible)

        assertEquals(0f, math.shade(front), 0.001f)
        assertTrue(math.shade(peek) > math.shade(right))
        assertEquals(0f, math.blurDp(front), 0.001f)
        assertTrue(math.blurDp(peek) > math.blurDp(right))
        assertTrue(math.zIndex(front) > math.zIndex(right))
        assertTrue(math.zIndex(right) > math.zIndex(peek))
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
