package com.shibi.musicplayer.domain

import java.util.Locale

object PlaybackTime {
    fun format(durationMs: Long): String {
        val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun clampSeek(positionMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        return positionMs.coerceIn(0L, durationMs)
    }
}
