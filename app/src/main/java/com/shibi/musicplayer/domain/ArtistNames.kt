package com.shibi.musicplayer.domain

import java.text.Normalizer
import java.util.Locale

/** Who a song is by: the primary artist, the featured ones, the library's grouping artist and its sort key. */
object ArtistNames {
    /** [primary] is the artist the song is filed under; [featured] are the guests. */
    data class Split(val primary: String, val featured: List<String>)

    private val bracketed = Regex("""[(\[]\s*(?:feat\.?|ft\.?|featuring)\s+([^)\]]+?)\s*[)\]]""", RegexOption.IGNORE_CASE)
    private val plain = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+(.+)$""", RegexOption.IGNORE_CASE)
    /** The forms a leading track number takes, tried in order: disc-track, "7.", "7 - ", "07 ". */
    private val leadingNumbers = listOf(
        Regex("""^\d-\d{1,3}\s+"""),
        Regex("""^\d{1,3}\s*[.)]\s*"""),
        Regex("""^\d{1,3}\s+-\s+"""),
        Regex("""^0\d\s+"""),
    )
    private val shortNumber = Regex("""\d{1,2}""")
    private val whitespace = Regex("""\s+""")
    private val diacritics = Regex("""\p{InCombiningDiacriticalMarks}+""")

    /** Splits on `;`, then splits `feat.` forms off the first part. Never splits on `&`, `,`, `/`, " and " or " x ". */
    fun split(raw: String): Split {
        val parts = raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Split(raw.trim(), emptyList())
        val featured = mutableListOf<String>()
        var primary = parts[0]
        bracketed.find(primary)?.let {
            featured += it.groupValues[1]
            primary = primary.removeRange(it.range).trim().replace(whitespace, " ")
        }
        plain.find(primary)?.let {
            featured += it.groupValues[1].trim()
            primary = primary.substring(0, it.range.first).trim()
        }
        if (primary.isEmpty()) return Split(parts[0], parts.drop(1))
        return Split(primary, featured + parts.drop(1))
    }

    /** The artist [track] is grouped under: album artist, else primary track artist, else the file name's, else null. */
    fun libraryArtist(track: Track, unknown: UnknownNames): String? =
        known(track.albumArtist, unknown, album = false)
            ?: known(track.artist, unknown, album = false)?.let { split(it).primary }
            ?: fromFileName(track.fileName)

    /** Who performs [track]: its own primary artist, else [libraryArtist]. */
    fun performer(track: Track, unknown: UnknownNames): String? =
        known(track.artist, unknown, album = false)?.let { split(it).primary }
            ?: libraryArtist(track, unknown)

    /** The primary artist of a raw artist tag, or null when it is missing or the app's placeholder. */
    fun artist(raw: String?, unknown: UnknownNames): String? = known(raw, unknown, album = false)?.let { split(it).primary }

    /** The album name, or null when it is missing or the app's placeholder. */
    fun album(raw: String?, unknown: UnknownNames): String? = known(raw, unknown, album = true)

    /** Lower case, no accents, no leading "the ", single spaces: what names are compared by. */
    fun sortKey(name: String): String {
        // Only Latin-style accents go: stripping every mark would break Tamil and Korean.
        val folded = Normalizer.normalize(Normalizer.normalize(name, Normalizer.Form.NFD).replace(diacritics, ""), Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
            .trim()
            .replace(whitespace, " ")
        return folded.removePrefix("the ").trimStart()
    }

    private fun known(raw: String?, unknown: UnknownNames, album: Boolean): String? {
        val name = (if (album) TrackDisplay.album(raw) else TrackDisplay.artist(raw)) ?: return null
        if (name.equals(unknown.artist, ignoreCase = true) || name.equals(unknown.album, ignoreCase = true)) return null
        return name
    }

    private fun fromFileName(fileName: String?): String? {
        if (fileName == null) return null
        val dot = fileName.lastIndexOf('.')
        val stem = (if (dot > 0) fileName.substring(0, dot) else fileName).trim()
        val number = leadingNumbers.firstNotNullOfOrNull { it.find(stem) }
        // "311 - Amber" strips to "Amber", which has no artist: then the whole stem is read.
        return (number?.let { artistOf(stem.removeRange(it.range)) }) ?: artistOf(stem)
    }

    /** The left side of exactly one " - " when both sides have text and the artist is not just 1-2 digits. */
    private fun artistOf(stem: String): String? {
        val sides = stem.split(" - ")
        if (sides.size != 2) return null
        val artist = sides[0].trim()
        return artist.takeIf { it.isNotEmpty() && sides[1].isNotBlank() && !shortNumber.matches(it) }
    }
}
