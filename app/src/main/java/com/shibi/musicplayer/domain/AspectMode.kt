package com.shibi.musicplayer.domain

/**
 * How the fullscreen picture fills the screen. [vlcScale] is the libVLC
 * `MediaPlayer.ScaleType` name; only the engine resolves it, so the domain stays free of
 * libVLC. The aspect button cycles in declaration order; the last pick is saved.
 */
enum class AspectMode(val vlcScale: String) {
    /** The whole picture, letterboxed. */
    Fit("SURFACE_BEST_FIT"),

    /** Fills the screen and crops the overflow. */
    Fill("SURFACE_FIT_SCREEN"),

    /** Fills the screen and distorts the picture. */
    Stretch("SURFACE_FILL"),
    Ratio16x9("SURFACE_16_9"),
    Ratio4x3("SURFACE_4_3"),

    /** Pixel for pixel, centred. */
    Original("SURFACE_ORIGINAL"),
    ;

    fun next(): AspectMode = entries[(ordinal + 1) % entries.size]

    /** Stored by name, so reordering the cycle never changes a saved pick. */
    fun encode(): String = name

    companion object {
        val Default = Fit

        fun decode(stored: String?): AspectMode = entries.firstOrNull { it.name == stored } ?: Default
    }
}
