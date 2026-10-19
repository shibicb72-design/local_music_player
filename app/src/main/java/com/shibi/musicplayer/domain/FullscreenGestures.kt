package com.shibi.musicplayer.domain

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** What a vertical swipe in fullscreen changes, by the half it starts in. */
enum class SwipeTarget { Brightness, Volume }

/** Fullscreen touch zones and drag maths, free of Android so they stay JUnit-tested. */
object FullscreenGestures {

    const val DoubleTapSeekMs = 10_000L

    /** A horizontal drag across the whole width moves this far. */
    const val SeekFullWidthMs = 90_000L

    /** Left third goes back 10 s, right third forward 10 s; the middle third does not seek. */
    fun doubleTapSeekMs(x: Float, width: Float): Long {
        if (width <= 0f) return 0L
        return when {
            x < width / 3f -> -DoubleTapSeekMs
            x > width * 2f / 3f -> DoubleTapSeekMs
            else -> 0L
        }
    }

    /** Left half is brightness, right half is volume. */
    fun swipeTarget(x: Float, width: Float): SwipeTarget =
        if (x < width / 2f) SwipeTarget.Brightness else SwipeTarget.Volume

    /** Decided once, at the touch slop; a tie is vertical. */
    fun isHorizontal(dx: Float, dy: Float): Boolean = abs(dx) > abs(dy)

    /**
     * What a drag across the whole width covers: [SeekFullWidthMs] (90 s), or the whole clip
     * when it is shorter, so a full-width drag on a short clip reaches its end.
     */
    fun swipeFullWidthMs(durationMs: Long): Long =
        if (durationMs in 1 until SeekFullWidthMs) durationMs else SeekFullWidthMs

    /** Where a horizontal drag of [dragPx] from [startMs] lands; unknown length stays put. */
    fun seekPreview(startMs: Long, durationMs: Long, dragPx: Float, widthPx: Float): Long {
        if (durationMs <= 0L || widthPx <= 0f) return startMs
        val delta = (dragPx / widthPx * swipeFullWidthMs(durationMs)).roundToLong()
        return (startMs + delta).coerceIn(0L, durationMs)
    }

    /** `+0:45 · 2:13`: the change and the target, as m:ss (h:mm:ss from one hour). */
    fun seekOffsetLabel(deltaMs: Long, targetMs: Long): String {
        val total = (targetMs.coerceAtLeast(0L)) / 1_000L
        val hours = total / 3_600L
        val minutes = (total % 3_600L) / 60L
        val seconds = total % 60L
        val target = if (hours > 0L) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "${total / 60L}:${seconds.toString().padStart(2, '0')}"
        }
        return "${seekDelta(deltaMs)} · $target"
    }

    /** True when a touch starts in the system gesture strips at either side. */
    fun startsInGestureEdge(x: Float, width: Float, leftEdgePx: Float, rightEdgePx: Float): Boolean =
        x < leftEdgePx || x > width - rightEdgePx

    /** `+0:45` or `-1:05`, whole seconds. */
    fun seekDelta(deltaMs: Long): String {
        val seconds = abs(deltaMs) / 1_000L
        val sign = if (deltaMs < 0L && seconds > 0L) "-" else "+"
        return "$sign${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }
}

/**
 * The fullscreen brightness swipe (left half). It sets the window's brightness only, never
 * the system setting; a full-height swipe covers the whole range.
 */
object BrightnessMath {

    /** Never fully dark: a black screen with no way to see the controls would be a trap. */
    const val Min = 0.01f

    private const val SystemMax = 255f
    private const val Unknown = 0.5f

    fun afterDrag(start: Float, dragUpPx: Float, heightPx: Float): Float {
        if (heightPx <= 0f || !dragUpPx.isFinite()) return start.coerceIn(Min, 1f)
        return (start + dragUpPx / heightPx).coerceIn(Min, 1f)
    }

    /**
     * The level a swipe starts from: the window's own override if set, else the system
     * setting (0..255). A negative window value means "follow the system".
     */
    fun start(windowBrightness: Float, systemSetting: Int?): Float = when {
        windowBrightness >= 0f -> windowBrightness.coerceIn(Min, 1f)
        systemSetting != null -> (systemSetting / SystemMax).coerceIn(Min, 1f)
        else -> Unknown
    }

    fun percent(brightness: Float): Int = (brightness.coerceIn(0f, 1f) * 100f).roundToInt()

    fun label(brightness: Float): String = "${percent(brightness)}%"
}
