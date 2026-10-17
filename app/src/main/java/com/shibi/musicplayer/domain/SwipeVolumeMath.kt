package com.shibi.musicplayer.domain

import kotlin.math.roundToInt

/**
 * The fullscreen volume swipe (right half). A full-height swipe covers the whole stream
 * range; the result is always a whole step, so a drag under half a step changes nothing.
 * The level is measured from where the drag started, so it never drifts.
 */
object SwipeVolumeMath {

    /** [dragUpPx] is positive upwards (louder); [heightPx] is the gesture area's height. */
    fun volumeAfterDrag(startVolume: Int, maxVolume: Int, dragUpPx: Float, heightPx: Float): Int {
        if (maxVolume <= 0) return 0
        val start = startVolume.coerceIn(0, maxVolume)
        if (heightPx <= 0f || !dragUpPx.isFinite()) return start
        val steps = (dragUpPx / heightPx * maxVolume).roundToInt()
        return (start + steps).coerceIn(0, maxVolume)
    }

    /** The chip text, `8 / 15`. There is no bar or slider anywhere. */
    fun label(volume: Int, maxVolume: Int): String = "$volume / $maxVolume"
}
