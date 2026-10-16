package com.shibi.musicplayer.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Geometry for the bottom dial.
 *
 * Item positions live on a circle. Rotation is measured in item units (1f rotates
 * by one item). Angle 0 is the top of the dial and positive angles run clockwise.
 * [WheelPoint] uses a y-down coordinate system so Compose can place icons directly.
 */
class NavWheelMath(val itemCount: Int) {
    init {
        require(itemCount >= 2) { "A wheel needs at least two items" }
    }

    val stepDegrees: Float = 360f / itemCount

    /** Rotation folded into `0` (inclusive) until [itemCount] (exclusive). */
    fun normalize(rotation: Float): Float {
        val span = itemCount.toFloat()
        val wrapped = rotation % span
        return if (wrapped < 0f) wrapped + span else wrapped
    }

    fun selectedIndex(rotation: Float): Int = Math.floorMod(rotation.roundToInt(), itemCount)

    /** Nearest detent. [flingItems] is added before rounding so a flick carries. */
    fun snapTarget(rotation: Float, flingItems: Float = 0f): Float =
        (rotation + flingItems).roundToInt().toFloat()

    /**
     * Unbounded rotation that centers [index], taking the shortest spin from [current].
     * An even-sized wheel breaks the halfway tie toward the negative direction.
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

    /** Signed degrees from the top, wrapped to (-180, 180]. */
    fun offsetDegrees(index: Int, rotation: Float): Float {
        var degrees = (index - rotation) * stepDegrees
        degrees %= 360f
        if (degrees > 180f) degrees -= 360f
        if (degrees <= -180f) degrees += 360f
        return degrees
    }

    fun scale(offsetDegrees: Float): Float {
        val t = (abs(offsetDegrees) / 180f).coerceIn(0f, 1f)
        return 1f - t * (1f - MIN_SCALE)
    }

    fun alpha(offsetDegrees: Float): Float {
        val t = (abs(offsetDegrees) / 180f).coerceIn(0f, 1f)
        val eased = t * t
        return 1f - eased * (1f - MIN_ALPHA)
    }

    /**
     * Degrees of rim mark [index] out of [count], spinning with [rotation] the same
     * way the items do. Index 0 sits at the top when [rotation] is 0.
     */
    fun rimOffsetDegrees(index: Int, count: Int, rotation: Float): Float {
        require(count >= 1) { "A rim needs at least one mark" }
        var degrees = index * (360f / count) - rotation * stepDegrees
        degrees %= 360f
        if (degrees > 180f) degrees -= 360f
        if (degrees <= -180f) degrees += 360f
        return degrees
    }

    fun position(offsetDegrees: Float, radius: Float): WheelPoint {
        val radians = offsetDegrees * (PI.toFloat() / 180f)
        return WheelPoint(
            x = radius * sin(radians),
            y = -radius * cos(radians),
        )
    }

    /**
     * Converts a horizontal fling into item units.
     * Positive [velocityPxPerSec] (finger moving right) yields a negative delta so the
     * item on the left swings up to the top.
     */
    fun flingItems(velocityPxPerSec: Float, pixelsPerItem: Float): Float {
        if (pixelsPerItem <= 0f) return 0f
        val items = -velocityPxPerSec / pixelsPerItem * FLING_COAST_SECONDS
        return items.coerceIn(-MAX_FLING_ITEMS, MAX_FLING_ITEMS)
    }

    companion object {
        const val MIN_SCALE = 0.70f
        const val MIN_ALPHA = 0.75f
        const val MAX_FLING_ITEMS = 2.5f
        const val FLING_COAST_SECONDS = 0.25f
    }
}

data class WheelPoint(
    val x: Float,
    val y: Float,
)
