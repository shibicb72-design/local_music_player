package com.shibi.musicplayer.domain

import java.util.Locale

/** Turns libVLC's picture counters around a play window into one readable log line. */
object PlaybackCheck {
    const val WindowMs = 5_000L

    /** Frames shown per second over the window; 0 when the window is not positive. */
    fun fps(displayedBefore: Int, displayedAfter: Int, windowMs: Long): Double {
        if (windowMs <= 0L) return 0.0
        return (displayedAfter - displayedBefore) * 1000.0 / windowMs
    }

    /** One line for the log, e.g. "Playback check: 29.8 fps shown, 0 frames lost, hardware decoding on". One decimal. */
    fun summary(fps: Double, lostDelta: Int, hardwareDecoding: Boolean): String {
        val shown = String.format(Locale.US, "%.1f", fps)
        val decoding = if (hardwareDecoding) "on" else "off"
        return "Playback check: $shown fps shown, $lostDelta frames lost, hardware decoding $decoding"
    }
}
