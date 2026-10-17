package com.shibi.musicplayer.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Hue / lightness picker that cannot leave the pastel range.
 * Saturation is clamped so the result stays soft, never neon.
 */
object PastelColor {
    const val MIN_SATURATION = 0.20f
    const val MAX_SATURATION = 0.45f
    const val MIN_LIGHTNESS = 0.70f
    const val MAX_LIGHTNESS = 0.92f
    const val DEFAULT_SATURATION = 0.32f

    data class Hsl(
        val hue: Float,
        val saturation: Float,
        val lightness: Float,
    )

    fun constrain(hue: Float, saturation: Float, lightness: Float): Hsl {
        return Hsl(
            hue = wrapHue(hue),
            saturation = saturation.coerceIn(MIN_SATURATION, MAX_SATURATION),
            lightness = lightness.coerceIn(MIN_LIGHTNESS, MAX_LIGHTNESS),
        )
    }

    fun fromArgb(argb: Long): Hsl {
        val r = (((argb shr 16) and 0xFF).toInt()) / 255f
        val g = (((argb shr 8) and 0xFF).toInt()) / 255f
        val b = ((argb and 0xFF).toInt()) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val lightness = (max + min) / 2f
        val delta = max - min
        val saturation = when {
            delta == 0f -> 0f
            lightness <= 0f || lightness >= 1f -> 0f
            else -> delta / (1f - abs(2f * lightness - 1f))
        }
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        return Hsl(wrapHue(hue), saturation, lightness)
    }

    fun toArgb(hue: Float, saturation: Float, lightness: Float): Long {
        val clamped = constrain(hue, saturation, lightness)
        val c = (1f - abs(2f * clamped.lightness - 1f)) * clamped.saturation
        val x = c * (1f - abs((clamped.hue / 60f) % 2f - 1f))
        val m = clamped.lightness - c / 2f
        val (r1, g1, b1) = when {
            clamped.hue < 60f -> Triple(c, x, 0f)
            clamped.hue < 120f -> Triple(x, c, 0f)
            clamped.hue < 180f -> Triple(0f, c, x)
            clamped.hue < 240f -> Triple(0f, x, c)
            clamped.hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    fun pastelArgb(hue: Float, lightness: Float, saturation: Float = DEFAULT_SATURATION): Long {
        return toArgb(hue, saturation, lightness)
    }

    fun isPastel(argb: Long): Boolean {
        val hsl = fromArgb(argb)
        return hsl.saturation in MIN_SATURATION..MAX_SATURATION &&
            hsl.lightness in MIN_LIGHTNESS..MAX_LIGHTNESS
    }

    private fun wrapHue(hue: Float): Float {
        var wrapped = hue % 360f
        if (wrapped < 0f) wrapped += 360f
        return wrapped
    }
}
