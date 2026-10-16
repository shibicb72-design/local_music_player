package com.shibi.musicplayer.domain

import java.text.BreakIterator
import java.util.Locale

object TrackDisplay {
    private val AUDIO_EXTENSIONS = setOf(
        "mp3",
        "flac",
        "m4a",
        "aac",
        "ogg",
        "opus",
        "wav",
        "wma",
    )

    fun title(raw: String): String {
        val trimmed = raw.trim()
        val dot = trimmed.lastIndexOf('.')
        val stripped = if (dot >= 0 && trimmed.substring(dot + 1).lowercase() in AUDIO_EXTENSIONS) {
            trimmed.substring(0, dot).trim()
        } else {
            trimmed
        }
        return stripped.ifBlank { trimmed }
    }

    fun artist(raw: String?): String? = knownName(raw, unknownAlbum = false)

    fun album(raw: String?): String? = knownName(raw, unknownAlbum = true)

    /** Artist when it is a real name, otherwise a real album name. */
    fun subtitle(artist: String?, album: String?): String? = artist(artist) ?: album(album)

    /**
     * Index at which a Tamil line should be cut so a following ellipsis fits.
     * The cut is always on a grapheme boundary, one cluster before [visibleEnd].
     */
    fun tamilEllipsisCut(text: String, visibleEnd: Int): Int {
        if (text.isEmpty() || visibleEnd <= 0) return 0
        val boundary = graphemeBoundaryAtOrBefore(text, visibleEnd.coerceAtMost(text.length))
        return previousGrapheme(text, boundary)
    }

    private fun knownName(raw: String?, unknownAlbum: Boolean): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        if (lower == "unknown" || lower == "<unknown>") return null
        if (unknownAlbum && lower == "unknown album") return null
        return trimmed
    }

    private fun graphemeBoundaryAtOrBefore(text: String, index: Int): Int {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        if (iterator.isBoundary(index)) return index
        val previous = iterator.preceding(index)
        return if (previous == BreakIterator.DONE) 0 else previous
    }

    private fun previousGrapheme(text: String, index: Int): Int {
        if (index <= 0) return 0
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val previous = iterator.preceding(index)
        return if (previous == BreakIterator.DONE) 0 else previous
    }
}

fun containsTamil(text: String): Boolean = text.any { it.code in 0x0B80..0x0BFF }
