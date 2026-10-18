package com.shibi.musicplayer.domain

data class Bounds(val left: Float, val top: Float, val width: Float, val height: Float)

object BoundsLerp {
    /** Linear blend from [from] (t = 0) to [to] (t = 1); t is not clamped (springs overshoot a little). */
    fun lerp(from: Bounds, to: Bounds, t: Float): Bounds = Bounds(
        left = from.left + (to.left - from.left) * t,
        top = from.top + (to.top - from.top) * t,
        width = from.width + (to.width - from.width) * t,
        height = from.height + (to.height - from.height) * t,
    )

    /** Where fullscreen grows from: the player box when known and non-empty, else a box 80% of the window, centred. */
    fun startFor(box: Bounds?, windowWidth: Float, windowHeight: Float): Bounds {
        if (box != null && box.width > 0f && box.height > 0f) return box
        val width = windowWidth * 0.8f
        val height = windowHeight * 0.8f
        return Bounds(
            left = (windowWidth - width) / 2f,
            top = (windowHeight - height) / 2f,
            width = width,
            height = height,
        )
    }
}
