package com.shibi.musicplayer.domain

import kotlin.math.abs

/** How long the fast-scroll thumb stays visible after the last scroll or drag activity. */
const val FAST_SCROLL_HIDE_DELAY_MS = 2_000L

/** True while the thumb is being used, or for [FAST_SCROLL_HIDE_DELAY_MS] after the last activity. */
fun fastScrollVisible(active: Boolean, lastActiveAtMs: Long?, nowMs: Long): Boolean =
    active || (lastActiveAtMs != null && nowMs - lastActiveAtMs < FAST_SCROLL_HIDE_DELAY_MS)

/**
 * The fast-scroll thumb after the dots' pull. [fraction] is where the thumb is drawn and
 * [lockedIndex] the dot holding it (null between dots). [nearestIndex] and [proximity] say how close
 * the finger is to the nearest dot: 1 on it, 0.5 at the lock radius, 0 at twice the radius or farther.
 */
data class MagneticSnap(
    val fraction: Float,
    val lockedIndex: Int? = null,
    val nearestIndex: Int? = null,
    val proximity: Float = 0f,
)

/**
 * Pulls the raw thumb [fraction] toward the nearest of [dots] and locks onto that dot while the
 * finger is within [radius] of it (all in track fractions, 0..1). The pull closes [strength] (0..1)
 * of the gap right at the dot and fades to nothing at the radius, so the thumb never jumps as it
 * enters or leaves, and it never passes the dot.
 */
fun magneticSnap(fraction: Float, dots: List<Float>, radius: Float, strength: Float): MagneticSnap {
    val raw = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    var nearest = -1
    var distance = Float.POSITIVE_INFINITY
    dots.forEachIndexed { index, dot ->
        val d = abs(raw - dot)
        if (d < distance) {
            nearest = index
            distance = d
        }
    }
    if (nearest < 0) return MagneticSnap(raw)
    if (radius <= 0f) return MagneticSnap(raw, nearestIndex = nearest)
    val proximity = (1f - distance / (2f * radius)).coerceIn(0f, 1f)
    if (distance > radius) return MagneticSnap(raw, nearestIndex = nearest, proximity = proximity)
    val pull = strength.coerceIn(0f, 1f) * (1f - distance / radius)
    val pulled = (raw + (dots[nearest] - raw) * pull).coerceIn(0f, 1f)
    return MagneticSnap(pulled, lockedIndex = nearest, nearestIndex = nearest, proximity = proximity)
}

/**
 * Tick-once state for the fast-scroll dots. [tick] is true only on the update where the thumb locks
 * onto a dot it was not already on: staying on a dot is silent, and leaving it (or sliding straight
 * onto another dot) arms the next tick.
 */
data class DotTickState(val lockedIndex: Int? = null, val tick: Boolean = false) {
    fun next(lockedIndex: Int?): DotTickState =
        DotTickState(lockedIndex, tick = lockedIndex != null && lockedIndex != this.lockedIndex)
}
