package com.shibi.musicplayer.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Geometry for the bottom carousel.
 *
 * Eight items sit on a horizontal ring seen slightly from above. Rotation is in
 * item units (1f moves by one item). Angle 0 is the front of the ring. Positive
 * angles run to the right. Screen Y grows downward, so the front item sits lower
 * than the back of the ring.
 */
class CarouselMath(val itemCount: Int) {
    init {
        require(itemCount >= 2) { "A carousel needs at least two items" }
    }

    val stepRadians: Float = (2.0 * PI / itemCount).toFloat()

    /** Rotation folded into `0` (inclusive) until [itemCount] (exclusive). */
    fun normalize(rotation: Float): Float {
        val span = itemCount.toFloat()
        val wrapped = rotation % span
        return if (wrapped < 0f) wrapped + span else wrapped
    }

    fun selectedIndex(rotation: Float): Int = Math.floorMod(rotation.roundToInt(), itemCount)

    /** Nearest detent, in the same unbounded rotation space as [rotation]. */
    fun snapTarget(rotation: Float): Float = rotation.roundToInt().toFloat()

    /**
     * Unbounded rotation that brings [index] to the front, taking the shortest
     * spin from [current]. An even-sized ring breaks the halfway tie toward the
     * negative direction.
     */
    fun rotationForIndex(current: Float, index: Int): Float {
        require(index in 0 until itemCount) { "index $index is outside 0 until $itemCount" }
        val base = current.roundToInt()
        val currentIndex = Math.floorMod(base, itemCount)
        var delta = index - currentIndex
        val half = itemCount / 2
        if (delta > half) delta -= itemCount
        if (delta < -half) delta += itemCount
        if (itemCount % 2 == 0 && delta == half) delta = -half
        return (base + delta).toFloat()
    }

    /** Signed radians from the front, wrapped to (-π, π]. */
    fun offsetRadians(index: Int, rotation: Float): Float {
        var turns = (index - rotation) / itemCount.toFloat()
        turns %= 1f
        if (turns > 0.5f) turns -= 1f
        if (turns <= -0.5f) turns += 1f
        return turns * (2f * PI.toFloat())
    }

    fun translationX(offsetRadians: Float, radius: Float): Float =
        radius * sin(offsetRadians)

    /** 1 at the front, -1 at the back. */
    fun depth(offsetRadians: Float): Float = cos(offsetRadians)

    /** Front is lower on the screen (positive Y). */
    fun translationY(offsetRadians: Float, verticalRadius: Float): Float =
        verticalRadius * cos(offsetRadians)

    fun scale(offsetRadians: Float): Float {
        val facing = depth(offsetRadians).coerceAtLeast(0f)
        return MIN_SCALE + (1f - MIN_SCALE) * facing.pow(SCALE_GAMMA)
    }

    /**
     * Front of the ring stays visible. Alpha reaches 0 as an item rotates behind
     * the limb, and the back half stays hidden.
     */
    fun alpha(offsetRadians: Float): Float {
        val facing = depth(offsetRadians)
        if (facing <= HIDDEN_DEPTH) return 0f
        val t = ((facing - HIDDEN_DEPTH) / (1f - HIDDEN_DEPTH)).coerceIn(0f, 1f)
        return t.pow(ALPHA_GAMMA)
    }

    fun zIndex(offsetRadians: Float): Float = depth(offsetRadians)

    /** Cylinder tilt in degrees. Items on the right turn their right edge away. */
    fun rotationYDegrees(offsetRadians: Float): Float =
        sin(offsetRadians) * TILT_DEGREES

    /**
     * 1:1 drag along the ring. [deltaPx] is a finger movement (or a velocity in
     * px per second). Positive motion to the right yields a negative item delta
     * so the item under the finger stays under the finger. Returns item units
     * (or item units per second when [deltaPx] is a velocity).
     */
    fun dragItems(deltaPx: Float, radiusPx: Float): Float {
        if (radiusPx <= 0f) return 0f
        val arcPerItem = radiusPx * stepRadians
        return -deltaPx / arcPerItem
    }

    companion object {
        const val MIN_SCALE = 0.50f
        const val SCALE_GAMMA = 1.8f
        const val ALPHA_GAMMA = 0.8f
        const val HIDDEN_DEPTH = -0.30f
        const val TILT_DEGREES = 46f
    }
}
