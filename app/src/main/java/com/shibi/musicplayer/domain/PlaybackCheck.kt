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

    /**
     * One line for the log, with the deltas over the window and the decoder and audio output
     * labels, e.g. "Playback check: 29.8 fps shown, 150 frames decoded, 0 frames lost;
     * audio 230 buffers played, 0 lost; decoder MediaCodec (mediacodec_ndk, software
     * fallback allowed); audio out android_audiotrack to speaker". One decimal for the fps.
     */
    fun summary(
        fps: Double,
        lostPictures: Int,
        decodedVideo: Int,
        playedAudioBuffers: Int,
        lostAudioBuffers: Int,
        decoder: String,
        audioOutput: String,
    ): String {
        val shown = String.format(Locale.US, "%.1f", fps)
        return "Playback check: $shown fps shown, $decodedVideo frames decoded, $lostPictures frames lost; " +
            "audio $playedAudioBuffers buffers played, $lostAudioBuffers lost; " +
            "decoder $decoder; audio out $audioOutput"
    }
}
