package com.shibi.musicplayer.domain

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
     * Snaps [visibleEnd] to a grapheme boundary without splitting a cluster.
     *
     * An index that already ends a cluster is kept. An index inside a cluster
     * moves back to that cluster's start. Combining marks and a Tamil virama
     * stay with the preceding letter. This does not use [java.text.BreakIterator]:
     * JDK 17 treats a Tamil vowel sign as its own character, and JDK 21 does not.
     */
    fun tamilEllipsisCut(text: String, visibleEnd: Int): Int {
        if (text.isEmpty() || visibleEnd <= 0) return 0
        return graphemeBoundaryAtOrBefore(text, visibleEnd.coerceAtMost(text.length))
    }

    /**
     * End index of the grapheme before the one that ends at [boundary].
     * A Tamil cluster is removed as a whole.
     */
    fun dropLastGrapheme(text: String, boundary: Int): Int {
        if (text.isEmpty() || boundary <= 0) return 0
        val end = graphemeBoundaryAtOrBefore(text, boundary.coerceAtMost(text.length))
        if (end <= 0) return 0
        return graphemeStart(text, previousCodePointIndex(text, end))
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
        if (index <= 0) return 0
        if (index >= text.length) return text.length
        val start = codePointStart(text, index)
        if (start != index || continuesGrapheme(text, index)) return graphemeStart(text, start)
        return index
    }

    /** Start of the grapheme that contains [index]. */
    private fun graphemeStart(text: String, index: Int): Int {
        var cursor = codePointStart(text, index.coerceIn(0, text.length))
        if (cursor >= text.length) return text.length
        while (cursor > 0 && continuesGrapheme(text, cursor)) {
            cursor = previousCodePointIndex(text, cursor)
        }
        return cursor
    }

    private fun continuesGrapheme(text: String, index: Int): Boolean {
        val current = Character.codePointAt(text, index)
        if (isCombiningMark(current)) return true
        val previous = Character.codePointAt(text, previousCodePointIndex(text, index))
        return previous == TAMIL_VIRAMA
    }

    private fun isCombiningMark(codePoint: Int): Boolean {
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            codePoint == ZWJ ||
            codePoint == ZWNJ
    }

    private fun codePointStart(text: String, index: Int): Int {
        if (index <= 0 || index >= text.length) return index
        return if (Character.isLowSurrogate(text[index])) index - 1 else index
    }

    private fun previousCodePointIndex(text: String, index: Int): Int {
        if (index <= 1) return 0
        val before = index - 1
        return if (Character.isLowSurrogate(text[before])) before - 1 else before
    }

    private const val TAMIL_VIRAMA = 0x0BCD
    private const val ZWJ = 0x200D
    private const val ZWNJ = 0x200C
}

fun containsTamil(text: String): Boolean = text.any { it.code in 0x0B80..0x0BFF }
