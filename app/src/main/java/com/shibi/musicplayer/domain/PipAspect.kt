package com.shibi.musicplayer.domain

import kotlin.math.roundToInt

/** A width-to-height picture-in-picture ratio, kept as a fraction so no precision is lost. */
data class PipRatio(val numerator: Int, val denominator: Int)

/**
 * Android refuses a picture-in-picture window whose aspect ratio is outside 1:2.39..2.39:1 and
 * throws an IllegalArgumentException, so [ratioFor] clamps into that band. Pure Kotlin, no
 * Android imports.
 */
object PipAspect {

    private const val MAX_RATIO = 2.39
    private const val MIN_RATIO = 1.0 / 2.39
    private const val TOLERANCE = 1e-6

    /**
     * The PiP aspect ratio (width / height) for a video. Uses [aspect] (the display's own
     * width / height) when it is not null, finite and positive; otherwise the video's own
     * [videoWidth] / [videoHeight] when both are positive; otherwise the 16:9 default. The
     * result is expressed as a fraction over 1000 and clamped to Android's legal band, where
     * the exact 2.39:1 and 1:2.39 edges are allowed.
     */
    fun ratioFor(videoWidth: Int, videoHeight: Int, aspect: Float?): PipRatio {
        val ratio = when {
            aspect != null && aspect.isFinite() && aspect > 0f -> aspect.toDouble()
            videoWidth > 0 && videoHeight > 0 -> videoWidth.toDouble() / videoHeight.toDouble()
            else -> return PipRatio(16, 9)
        }
        // Round first, then clamp the rounded fraction: a ratio just inside the band can round
        // to a fraction just outside it, which Android would refuse.
        val rounded = PipRatio((ratio * 1000.0).roundToInt(), 1000)
        val fraction = rounded.numerator.toDouble() / rounded.denominator.toDouble()
        return when {
            fraction > MAX_RATIO + TOLERANCE -> PipRatio(239, 100)
            fraction < MIN_RATIO - TOLERANCE -> PipRatio(100, 239)
            else -> rounded
        }
    }
}
