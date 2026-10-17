package com.shibi.musicplayer.domain

/**
 * Links and text shaping for the open-source licences screen. LGPL-2.1 asks
 * us to point at the exact libVLC source, so the jar link carries the version.
 */
object OpenSourceNotices {
    const val LibVlcJniUrl = "https://code.videolan.org/videolan/libvlcjni"
    const val VlcUrl = "https://code.videolan.org/videolan/vlc"
    const val ApacheUrl = "https://www.apache.org/licenses/LICENSE-2.0"

    fun libVlcSourcesJarUrl(version: String): String =
        "https://repo1.maven.org/maven2/org/videolan/android/libvlc-all/$version/libvlc-all-$version-sources.jar"

    /**
     * Licence files are hard-wrapped at about 72 columns. Split on blank lines
     * and join each paragraph's lines, so the text wraps to the screen instead.
     */
    fun paragraphs(text: String): List<String> =
        text.replace("\r\n", "\n")
            .split(BlankLine)
            .map { it.trim().replace(Whitespace, " ") }
            .filter { it.isNotEmpty() }

    private val BlankLine = Regex("\n[ \t]*\n")
    private val Whitespace = Regex("\\s+")
}
