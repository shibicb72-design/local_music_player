package com.shibi.musicplayer.domain

import kotlin.math.abs

/**
 * Video playback speeds. Policy: the speed resets to 1x for every newly opened video; it is
 * never saved and never carried over to the next one.
 */
object PlaybackSpeed {

    val Options: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    const val Default = 1f

    /** `0.5×`, `1×`, `1.25×` (multiplication sign, no trailing zeros). */
    fun label(rate: Float): String {
        val whole = rate.toInt()
        val text = if (rate == whole.toFloat()) whole.toString() else rate.toString()
        return "$text×"
    }

    /** The nearest option; an unusable value gives [Default]. */
    fun coerce(rate: Float): Float {
        if (!rate.isFinite()) return Default
        return Options.minByOrNull { abs(it - rate) } ?: Default
    }
}
