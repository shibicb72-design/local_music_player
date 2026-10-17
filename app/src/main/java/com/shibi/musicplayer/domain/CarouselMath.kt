package com.shibi.musicplayer.domain

import java.lang.Math.toDegrees
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Geometry for the bottom carousel.
 *
 * Eight items sit 45° apart on a ring. Angle 0 is the front. Positive angles
 * run to the right. Every pose is an odd or even function of that angle, so
 * the left side mirrors the right. The back of the ring (cos θ < -0.1) is
 * hidden. Screen Y grows downward; a negative Y offset lifts a tile.
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

    /**
     * 0 at the front. Back tiles move up by [liftPx] times `(1 - cos θ)`.
     * [liftPx] is the 10dp rise, in pixels.
     */
    fun translationY(offsetRadians: Float, liftPx: Float): Float =
        -(1f - cos(offsetRadians)) * liftPx

    /** 1 at the front, 0.81 at ±90°, 0.62 at the back. */
    fun scale(offsetRadians: Float): Float {
        val facing = (cos(offsetRadians) + 1f) / 2f
        return MIN_SCALE + (1f - MIN_SCALE) * facing
    }

    /**
     * 0 when `cos θ < -0.1`. Otherwise a mirror-symmetric fade from 0.35 at the
     * back of the visible arc to 1 at the front.
     */
    fun alpha(offsetRadians: Float): Float {
        val facing = cos(offsetRadians)
        if (facing < BACK_COS) return 0f
        val t = (facing + 1f) / 2f
        return MIN_ALPHA + (1f - MIN_ALPHA) * t
    }

    /** Background darkening, 0 at the front and proportional to `(1 - cos θ)`. */
    fun shade(offsetRadians: Float): Float =
        ((1f - cos(offsetRadians)) * SHADE).coerceIn(0f, 1f)

    fun zIndex(offsetRadians: Float): Float = depth(offsetRadians)

    /**
     * `-θ * 0.6` degrees, clamped to ±[MAX_TILT_DEGREES]. Positive θ (the right
     * side) tilts negatively.
     */
    fun rotationYDegrees(offsetRadians: Float): Float {
        val degrees = toDegrees(offsetRadians.toDouble()).toFloat()
        return (-degrees * TILT_PER_DEGREE).coerceIn(-MAX_TILT_DEGREES, MAX_TILT_DEGREES)
    }

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

    /**
     * Where a release with [velocity] (items per second) comes to rest: the
     * momentum is projected for [FLING_PROJECTION_SECONDS], capped at one full
     * turn, and rounded to a detent. One spring then carries the ring there,
     * so a fling never decays and then visibly re-snaps. Without [momentum]
     * (reduce motion) the ring only snaps to the nearest detent.
     */
    fun flingTarget(rotation: Float, velocity: Float, momentum: Boolean = true): Float {
        if (!momentum) return snapTarget(rotation)
        val travel = (velocity * FLING_PROJECTION_SECONDS)
            .coerceIn(-itemCount.toFloat(), itemCount.toFloat())
        return snapTarget(rotation + travel)
    }

    /** 1 at the front, 0 one step away or further. Drives the selected-state morph. */
    fun frontness(offsetRadians: Float): Float =
        (1f - abs(offsetRadians) / stepRadians).coerceIn(0f, 1f)

    /**
     * Label opacity. A hand-off, not a blend: each label fades in over the
     * last half step, so halfway between two items both are clear and the two
     * words never stack into a smear.
     */
    fun labelAlpha(offsetRadians: Float): Float {
        val t = ((frontness(offsetRadians) - 0.5f) / 0.5f).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 0 for the front and its neighbours, rising to 1 at the hidden back. */
    fun blurFraction(offsetRadians: Float): Float {
        val beyond = abs(offsetRadians) - stepRadians
        if (beyond <= 0f) return 0f
        return (beyond / (PI.toFloat() - stepRadians)).coerceIn(0f, 1f)
    }

    companion object {
        const val FLING_PROJECTION_SECONDS = 0.22f
        const val MIN_SCALE = 0.62f
        const val MIN_ALPHA = 0.35f
        const val BACK_COS = -0.1f
        const val SHADE = 0.55f
        const val TILT_PER_DEGREE = 0.6f
        const val MAX_TILT_DEGREES = 40f
        const val RADIUS_FRACTION = 0.44f
    }
}
