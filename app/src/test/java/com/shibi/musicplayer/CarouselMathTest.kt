package com.shibi.musicplayer

import com.shibi.musicplayer.domain.CarouselMath
import kotlin.math.PI
import kotlin.math.cos
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
        assertEquals(0f, math.translationY(front, 10f), 0.001f)

        for (step in 0 until math.itemCount) {
            val right = math.offsetRadians(step, 0f)
            val left = math.offsetRadians(Math.floorMod(-step, math.itemCount), 0f)
            assertEquals(math.scale(right), math.scale(left), 0.001f)
            assertEquals(math.alpha(right), math.alpha(left), 0.001f)
            assertEquals(math.translationY(right, 10f), math.translationY(left, 10f), 0.001f)
            assertEquals(
                -math.translationX(right, radius),
                math.translationX(left, radius),
                0.01f,
            )
            if (step == 0 || step * 2 == math.itemCount) {
                assertEquals(math.rotationYDegrees(right), math.rotationYDegrees(left), 0.01f)
            } else {
                assertEquals(-math.rotationYDegrees(right), math.rotationYDegrees(left), 0.01f)
            }
            if (cos(right) < CarouselMath.BACK_COS) {
                assertEquals(0f, math.alpha(right), 0.001f)
                assertEquals(0f, math.alpha(left), 0.001f)
            }
        }

        val right = math.offsetRadians(1, 0f)
        val left = math.offsetRadians(math.itemCount - 1, 0f)
        val side = (PI / 2.0).toFloat()
        val expectedNeighborScale = 0.62f + 0.38f * ((cos(right) + 1f) / 2f)
        assertEquals(expectedNeighborScale, math.scale(right), 0.001f)
        assertEquals(math.alpha(right), math.alpha(left), 0.001f)
        assertTrue(math.alpha(right) > 0.8f)
        assertTrue(math.translationX(right, radius) > 0f)
        assertTrue(math.translationX(left, radius) < 0f)
        assertEquals(radius * kotlin.math.sin(right), math.translationX(right, radius), 0.01f)
        assertEquals(0.81f, math.scale(side), 0.001f)
        assertTrue(math.alpha(side) > 0.6f)
        assertEquals(math.alpha(side), math.alpha(-side), 0.001f)
        assertEquals(-10f, math.translationY(side, 10f), 0.001f)
        assertEquals(0f, math.alpha((PI * 0.75).toFloat()), 0.001f)
        assertEquals(0f, math.alpha(PI.toFloat()), 0.001f)
        assertTrue(math.shade(right) > math.shade(front))
        assertEquals((1f - cos(side)) * CarouselMath.SHADE, math.shade(side), 0.001f)
        assertTrue(math.zIndex(front) > math.zIndex(right))
        assertTrue(math.zIndex(right) > math.zIndex(side))

        val visible = (0 until math.itemCount).count { index ->
            math.alpha(math.offsetRadians(index, 0f)) > 0.01f
        }
        assertEquals(5, visible)
    }

    @Test
    fun rotationYFollowsNegativeAngleAndClamps() {
        assertEquals(0f, math.rotationYDegrees(0f), 0.001f)
        assertEquals(-27f, math.rotationYDegrees(math.stepRadians), 0.05f)
        assertEquals(27f, math.rotationYDegrees(-math.stepRadians), 0.05f)
        assertEquals(-40f, math.rotationYDegrees((PI / 2.0).toFloat()), 0.001f)
        assertEquals(40f, math.rotationYDegrees(-(PI / 2.0).toFloat()), 0.001f)
    }

    @Test
    fun ringRadiusIsWideRelativeToTheOldTightOrbit() {
        assertTrue(CarouselMath.RADIUS_FRACTION >= 0.42f)
    }

    @Test
    fun dragIsOneToOneAlongTheRing() {
        val radius = 120f
        val arc = radius * math.stepRadians
        assertEquals(-1f, math.dragItems(arc, radius), 0.001f)
        assertEquals(0.5f, math.dragItems(-arc / 2f, radius), 0.001f)
        assertEquals(0f, math.dragItems(40f, 0f), 0.001f)
    }

    @Test
    fun flingTargetProjectsMomentumAndLandsOnADetent() {
        // 5 items/s for 0.22 s projects 1.1 items, so it lands one detent on.
        assertEquals(3f, math.flingTarget(2.0f, 5f), 0.001f)
        assertEquals(1f, math.flingTarget(2.0f, -5f), 0.001f)
        // A slow release falls back to the nearest detent.
        assertEquals(2f, math.flingTarget(2.3f, 0.2f), 0.001f)
        // Always a whole detent, whatever the input.
        val target = math.flingTarget(0.37f, 13.3f)
        assertEquals(target, target.toInt().toFloat(), 0.0001f)
    }

    @Test
    fun flingTargetNeverSpinsMoreThanOneTurn() {
        assertEquals(8f, math.flingTarget(0f, 500f), 0.001f)
        assertEquals(-8f, math.flingTarget(0f, -500f), 0.001f)
    }

    @Test
    fun flingTargetWithoutMomentumOnlySnaps() {
        assertEquals(2f, math.flingTarget(2.3f, 40f, momentum = false), 0.001f)
        assertEquals(3f, math.flingTarget(2.6f, -40f, momentum = false), 0.001f)
    }

    @Test
    fun frontnessPeaksAtTheFrontAndFadesOverOneStep() {
        assertEquals(1f, math.frontness(0f), 0.001f)
        assertEquals(0.5f, math.frontness(math.stepRadians / 2f), 0.001f)
        assertEquals(0.5f, math.frontness(-math.stepRadians / 2f), 0.001f)
        assertEquals(0f, math.frontness(math.stepRadians), 0.001f)
        assertEquals(0f, math.frontness(PI.toFloat()), 0.001f)
    }

    @Test
    fun labelAlphaCrossfadesWithoutTwoFullLabels() {
        // Halfway between two items neither label is fully opaque.
        val half = math.stepRadians / 2f
        assertEquals(0f, math.labelAlpha(half), 0.001f)
        assertEquals(0f, math.labelAlpha(-half), 0.001f)
        assertTrue(math.labelAlpha(half / 2f) in 0.4f..0.6f)
        assertEquals(1f, math.labelAlpha(0f), 0.001f)
        assertEquals(0f, math.labelAlpha(math.stepRadians), 0.001f)
    }

    @Test
    fun backBlurIsZeroNearTheFrontAndGrowsTowardTheBack() {
        assertEquals(0f, math.blurFraction(0f), 0.001f)
        assertEquals(0f, math.blurFraction(math.stepRadians), 0.001f)
        val side = math.blurFraction(2f * math.stepRadians)
        assertTrue(side > 0f && side <= 1f)
        assertTrue(math.blurFraction(3f * math.stepRadians) > side)
    }

    // The compact ring with Videos: nine sections, 40° apart.
    private val nine = CarouselMath(itemCount = 9)

    @Test
    fun nineItemRingStepsFortyDegrees() {
        assertEquals(40f, Math.toDegrees(nine.stepRadians.toDouble()).toFloat(), 0.001f)
    }

    /** Front, ±40° and ±80° (cos 0.17) show; ±120° and ±160° sit behind BACK_COS. */
    @Test
    fun nineItemRingShowsFiveTilesSymmetrically() {
        val visible = (0 until nine.itemCount).filter { index ->
            nine.alpha(nine.offsetRadians(index, 0f)) > 0.01f
        }
        assertEquals(listOf(0, 1, 2, 7, 8), visible)
        for (step in 1..2) {
            val right = nine.offsetRadians(step, 0f)
            val left = nine.offsetRadians(nine.itemCount - step, 0f)
            assertEquals(nine.alpha(right), nine.alpha(left), 0.001f)
            assertEquals(nine.scale(right), nine.scale(left), 0.001f)
            assertEquals(-nine.translationX(right, 200f), nine.translationX(left, 200f), 0.01f)
        }
        assertEquals(0f, nine.alpha(nine.offsetRadians(3, 0f)), 0.001f)
        assertEquals(0f, nine.alpha(nine.offsetRadians(6, 0f)), 0.001f)
    }

    @Test
    fun nineItemSelectedIndexWraps() {
        assertEquals(0, nine.selectedIndex(0f))
        assertEquals(0, nine.selectedIndex(9f))
        assertEquals(8, nine.selectedIndex(-1f))
        assertEquals(8, nine.selectedIndex(-0.6f))
        assertEquals(0, nine.selectedIndex(-0.4f))
        assertEquals(4, nine.selectedIndex(13f))
        assertEquals(8.5f, nine.normalize(-0.5f), 0.001f)
    }

    /** Odd ring: no halfway tie, so the shortest spin is unique both ways. */
    @Test
    fun nineItemRotationForIndexTakesTheShortestSpin() {
        assertEquals(-1f, nine.rotationForIndex(0f, 8), 0.001f)
        assertEquals(4f, nine.rotationForIndex(0f, 4), 0.001f)
        assertEquals(-4f, nine.rotationForIndex(0f, 5), 0.001f)
    }

    @Test
    fun nineItemFlingTargetNeverSpinsMoreThanOneTurn() {
        assertEquals(9f, nine.flingTarget(0f, 500f), 0.001f)
        assertEquals(-9f, nine.flingTarget(0f, -500f), 0.001f)
        assertEquals(10f, nine.flingTarget(1f, 500f), 0.001f)
    }
}
