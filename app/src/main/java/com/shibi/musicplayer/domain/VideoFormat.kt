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
        val shown = shownSize(bytes)
        val unit = sizeUnits[shown.unitIndex]
        return if (shown.tenths) "${shown.value / 10}.${shown.value % 10} $unit" else "${shown.value} $unit"
    }

    /**
     * The byte count [size] shows, rounded the same way: 9,970,000 reads "10 MB", so it is
     * 10,000,000. The size sort bands on this, so a band always agrees with the row's label.
     */
    fun shownBytes(bytes: Long): Long {
        if (bytes < 1_000L) return bytes
        val shown = shownSize(bytes)
        var scale = 1_000L
        repeat(shown.unitIndex) { scale *= 1_000L }
        return if (shown.tenths) shown.value * scale / 10 else shown.value * scale
    }

    /** [value] in [sizeUnits] at [unitIndex], or in tenths of that unit when [tenths]. */
    private class ShownSize(val value: Long, val tenths: Boolean, val unitIndex: Int)

    private fun shownSize(bytes: Long): ShownSize {
        var value = bytes / 1_000.0
        for (index in sizeUnits.indices) {
            if (value < 9.95) return ShownSize((value * 10).roundToLong(), tenths = true, unitIndex = index)
            val whole = value.roundToLong()
            if (whole < 1_000L || index == sizeUnits.lastIndex) return ShownSize(whole, tenths = false, unitIndex = index)
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
