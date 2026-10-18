package com.shibi.musicplayer.domain

import kotlin.math.min
import kotlin.math.roundToLong

/** Pure labels for a video row: length, file size and quality. */
object VideoFormat {
    /** Shown when MediaStore has no value (or 0) for a field. */
    const val MISSING = "—"

    private val sizeUnits = listOf("KB", "MB", "GB", "TB")

    /** `0:42`, `59:59`, `1:02:03`. Partial seconds are floored, like the music seek bar. */
    fun duration(ms: Long?): String {
        if (ms == null || ms <= 0L) return MISSING
        val totalSeconds = ms / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "$hours:${minutes.twoDigits()}:${seconds.twoDigits()}"
        } else {
            "$minutes:${seconds.twoDigits()}"
        }
    }

    /**
     * Decimal units like the system Files app: `812 MB`, `1.4 GB`. One decimal below 10,
     * whole numbers from 10, and a value that rounds to 1000 moves up a unit.
     */
    fun size(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return MISSING
        if (bytes < 1_000L) return "$bytes B"
        var value = bytes / 1_000.0
        for ((index, unit) in sizeUnits.withIndex()) {
            if (value < 9.95) {
                val tenths = (value * 10).roundToLong()
                return "${tenths / 10}.${tenths % 10} $unit"
            }
            val whole = value.roundToLong()
            if (whole < 1_000L || index == sizeUnits.lastIndex) return "$whole $unit"
            value /= 1_000.0
        }
        error("unreachable")
    }

    /** Quality from the short side, so portrait videos get the same label as landscape ones. */
    fun resolution(width: Int?, height: Int?): String {
        if (width == null || height == null || width <= 0 || height <= 0) return MISSING
        val shortSide = min(width, height)
        return when {
            shortSide >= 2160 -> "4K"
            shortSide >= 1440 -> "1440p"
            shortSide >= 1080 -> "1080p"
            shortSide >= 720 -> "720p"
            shortSide >= 480 -> "480p"
            else -> "$width×$height"
        }
    }

    private fun Long.twoDigits(): String = toString().padStart(2, '0')
}
