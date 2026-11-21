package com.shibi.musicplayer.domain

import com.shibi.musicplayer.domain.VideoIdentity.CameraSource
import java.time.Year

/**
 * Sorts a video into Movie, TV episode, Personal clip or Other from its MediaStore file name
 * (`DISPLAY_NAME`, with extension) and folder (`BUCKET_DISPLAY_NAME`), and cleans its title.
 *
 * Rules, first match wins:
 * 1. Camera, phone, screen-recording or messaging-app file names -> [VideoIdentity.Personal].
 * 2. An episode marker (`S01E02`, `S01E02E03`, `1x02`, `Season 1 Episode 2`) -> [VideoIdentity.Episode].
 * 3. A year (1920 to next year) in brackets, before a junk token, or last -> [VideoIdentity.Movie].
 *    Tokens that cleaning drops (`[YTS.MX]`, `(1)`, a trailing `-GROUP`) don't count as "next".
 * 4. A strong junk token (resolution, source, codec) -> [VideoIdentity.Movie] without a year.
 * 5. A camera folder (`Camera`, `DCIM`, ...) -> [VideoIdentity.Personal].
 * 6. Still Other, or a Movie title of 2 characters or fewer: the folder's Movie, if it parses as one.
 * 7. Otherwise [VideoIdentity.Other].
 *
 * Movie and Other titles are never empty: they fall back to the cleaned stem, the raw stem, the
 * raw file name and finally [UNTITLED].
 *
 * Known false positive: a trailing year always qualifies, so `Vacation 2019.mp4` is
 * `Movie("Vacation", 2019)` even when it is a home video.
 */
object VideoNameParser {
    /** The title for an empty file name. The UI may swap it for a localized string. */
    const val UNTITLED = "Untitled"

    fun parse(fileName: String, folder: String?): VideoIdentity =
        parse(fileName, folder, Year.now().value)

    /** Pure: same input, same output. [currentYear] bounds the accepted years at `currentYear + 1`. */
    fun parse(fileName: String, folder: String?, currentYear: Int): VideoIdentity {
        val years = MIN_YEAR..currentYear + 1
        val stem = fileName.substringBeforeLast('.', fileName)

        cameraSource(stem)?.let { return VideoIdentity.Personal(it) }
        episode(stem, folder, years)?.let { return it }
        val movie = movie(stem, years)
        if (movie != null && movie.title.length > SHORT_TITLE) return movie
        if (movie == null && folder != null && folder.trim().lowercase() in CAMERA_FOLDERS) {
            return VideoIdentity.Personal(CameraSource.OTHER_CAMERA)
        }
        val folderMovie = folder?.let { movie(it, years) }
        if (folderMovie != null) return folderMovie
        return movie ?: VideoIdentity.Other(
            titleOrWhole(stem, stem, years).ifEmpty { fileName.trim() }.ifEmpty { UNTITLED },
        )
    }

    private const val MIN_YEAR = 1920
    private const val SHORT_TITLE = 2

    /** Separators between tokens: `.`, `_`, space, `-`, brackets and parentheses. */
    private const val SEP = """._ \-\[\]()"""

    /** Junk tokens that keep their dot or hyphen, matched before the plain token rule. */
    private const val JOINED_JUNK = """h\.26[45]|aac5\.1|ddp5\.1|dd5\.1|[57]\.1|blu-ray|web-dl"""

    private val TOKEN = Regex(
        """(?<![^$SEP])(?:$JOINED_JUNK)(?![^$SEP])|[^$SEP]+""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A dot or underscore to turn into a space, unless it sits inside a joined junk token (group 1)
     * or a single-letter acronym like `S.W.A.T.` (group 2).
     */
    private val TITLE_SEPARATOR = Regex(
        """(?<![^$SEP])($JOINED_JUNK)(?![^$SEP])|(?<![A-Za-z0-9])([A-Za-z](?:\.[A-Za-z])+\.?)(?![A-Za-z])|[._]""",
        RegexOption.IGNORE_CASE,
    )

    private val STRONG_JUNK = setOf(
        "480p", "576p", "720p", "1080p", "1440p", "2160p", "4k", "uhd",
        "bluray", "blu-ray", "brrip", "bdrip", "web-dl", "webdl", "webrip", "hdtv", "dvdrip", "hdrip", "remux",
        "x264", "x265", "h.264", "h264", "h.265", "h265", "hevc", "avc", "xvid", "divx", "10bit", "8bit",
    )
    private val RESOLUTION = Regex("""\d{3,4}x\d{3,4}""")

    /** Junk only after a strong token or a year. */
    private val WEAK_JUNK = setOf(
        "hdr", "hdr10", "dv",
        "aac", "aac5.1", "ac3", "dts", "ddp5.1", "dd5.1", "5.1", "7.1", "atmos", "truehd",
        "web", "dvd",
        "proper", "repack", "extended", "unrated", "imax", "internal", "limited",
    )

    private val CAMERA_PREFIXES = listOf(
        """DJI_\d+""" to CameraSource.DJI,
        """VID_\d{8}_\d{6}""" to CameraSource.PHONE_TIMESTAMP,
        """PXL_\d{8}_\d+""" to CameraSource.PIXEL,
        """REC_\d{8}_\d{6}""" to CameraSource.PHONE_TIMESTAMP,
        """\d{8}_\d{6}""" to CameraSource.PHONE_TIMESTAMP,
        """Screen_Recording_""" to CameraSource.SCREEN_RECORDING,
        """screen-\d{8}""" to CameraSource.SCREEN_RECORDING,
        """VID-\d{8}-WA\d+""" to CameraSource.MESSAGING,
        """WhatsApp Video \d{4}-\d{2}-\d{2} at """ to CameraSource.MESSAGING,
        """Snapchat-\d+""" to CameraSource.MESSAGING,
        """InShot_\d{8}_\d+""" to CameraSource.OTHER_CAMERA,
    ).map { (pattern, source) -> Regex("^$pattern", RegexOption.IGNORE_CASE) to source }

    /** Whole-stem names, so `Classic1999` or `Doc2019` never count. A ` (n)` copy suffix is allowed. */
    private val CAMERA_STEMS = listOf(
        """IMG_\d+|MVI_\d+|DSC_?\d+|MAH\d+|C\d{4}""" to CameraSource.OTHER_CAMERA,
        """GX\d{6}|GH\d{6}|GOPR\d{4}|GP\d{6}""" to CameraSource.GOPRO,
    ).map { (pattern, source) -> Regex("""(?:$pattern)(?: ?\(\d+\))?""", RegexOption.IGNORE_CASE) to source }

    private val CAMERA_FOLDERS = setOf(
        "camera", "dcim", "screen recordings", "screenrecorder", "whatsapp video", "snapchat", "dji album", "gopro",
    )

    /** Each marker needs a non-alphanumeric neighbour or the string end, so `1920x1080` and `x264` never match. */
    private val EPISODE_MARKERS = listOf(
        """S(\d{1,2})[ ._-]?E(\d{1,3})(?:(?:-?E\d{1,3})*-?E(\d{1,3}))?""",
        """(\d{1,2})x(\d{2,3})""",
        """Season[ ._-]*(\d{1,3})[ ._-]*Episode[ ._-]*(\d{1,4})""",
    ).map { Regex("""(?<![A-Za-z0-9])(?:$it)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE) }

    private val SEASON_FOLDER = Regex("""Season[ ._-]*\d+|S\d+""", RegexOption.IGNORE_CASE)

    /** Trailing "Complete (TV) Series", "Complete" and sizes like `167GB` or `4.5 GB` on a show folder. */
    private val SHOW_FOLDER_SUFFIX = Regex(
        """(?:[ ._-]+(?:complete(?:[ ._-]+tv)?[ ._-]+series|complete|\d+(?:\.\d+)?[ ._-]*[gmt]b))+[ ._-]*$""",
        RegexOption.IGNORE_CASE,
    )

    private val SQUARE_GROUP = Regex("""\[[^\[\]]*\]""")
    private val ROUND_GROUP = Regex("""\([^()]*\)""")
    private val TRAILING_GROUP = Regex("""-[^$SEP]+\s*$""")
    private val WHITESPACE = Regex("""\s+""")
    private const val TRIM_CHARS = " -–:"

    private class Token(val text: String, val start: Int, val end: Int) {
        val lower = text.lowercase()
    }

    private fun tokens(text: String): List<Token> =
        TOKEN.findAll(text).map { Token(it.value, it.range.first, it.range.last + 1) }.toList()

    private fun isStrong(token: Token): Boolean = token.lower in STRONG_JUNK || RESOLUTION.matches(token.lower)

    private fun isWeak(token: Token): Boolean = token.lower in WEAK_JUNK

    private fun yearOf(token: Token, years: IntRange): Int? =
        token.text.takeIf { it.length == 4 && it.all { c -> c in '0'..'9' } }?.toInt()?.takeIf { it in years }

    /** With no space, dot or underscore anywhere, `-` is the word separator (`Movie-Name-2010`). */
    private fun dashSeparated(text: String): Boolean = text.none { it == ' ' || it == '.' || it == '_' }

    private fun cameraSource(stem: String): CameraSource? =
        CAMERA_PREFIXES.firstOrNull { (regex, _) -> regex.containsMatchIn(stem) }?.second
            ?: CAMERA_STEMS.firstOrNull { (regex, _) -> regex.matches(stem) }?.second

    private fun episode(stem: String, folder: String?, years: IntRange): VideoIdentity.Episode? {
        val marker = EPISODE_MARKERS.mapNotNull { it.find(stem) }.minByOrNull { it.range.first } ?: return null
        val season = marker.groupValues[1].toInt()
        val episode = marker.groupValues[2].toInt()
        val lastEpisode = marker.groupValues.getOrNull(3)?.toIntOrNull()?.takeIf { it > episode }
        val dashes = dashSeparated(stem)

        val show = clean(stem.substring(0, marker.range.first), years, dashes).ifEmpty { null }
            ?: folder?.let { folderShow(it, years) }
            ?: titleOrWhole(stem, stem, years)
        val episodeTitle = clean(beforeFirstStrong(stem, marker.range.last + 1), years, dashes).ifEmpty { null }

        return VideoIdentity.Episode(show, season, episode, lastEpisode, episodeTitle)
    }

    /** The show name a folder gives, or null for a bare `Season N` / `S1` folder or nothing left. */
    private fun folderShow(folder: String, years: IntRange): String? {
        if (SEASON_FOLDER.matches(folder.trim())) return null
        val head = withoutGroups(beforeFirstStrong(folder, 0), years)
        return clean(SHOW_FOLDER_SUFFIX.replace(head, ""), years, dashSeparated(folder)).ifEmpty { null }
    }

    /** Rules 3 and 4 on [text], a file stem or a folder name. */
    private fun movie(text: String, years: IntRange): VideoIdentity.Movie? {
        val tokens = tokens(text)
        val masked = withoutGroups(text, years)
        val groupStart = trailingGroupStart(masked, years) ?: text.length
        val kept = tokens.filter { it.start < groupStart && masked[it.start] != ' ' }
        val dashes = dashSeparated(text)
        for (i in kept.indices.reversed()) {
            val token = kept[i]
            val year = yearOf(token, years) ?: continue
            val enclosed = token.start > 0 && token.end < text.length &&
                text[token.start - 1] in "([" && text[token.end] in ")]"
            val next = kept.getOrNull(i + 1)
            if (!enclosed && next != null && !isStrong(next) && !isWeak(next)) continue
            val title = clean(text.substring(0, if (enclosed) token.start - 1 else token.start), years, dashes)
            if (title.isNotEmpty()) return VideoIdentity.Movie(title, year)
        }
        if (tokens.none(::isStrong)) return null
        return VideoIdentity.Movie(titleOrWhole(beforeFirstStrong(text, 0), text, years), null)
    }

    /** [text] from [from] up to its first strong junk token, never ending inside an open bracket. */
    private fun beforeFirstStrong(text: String, from: Int): String {
        val strong = tokens(text).firstOrNull { it.start >= from && isStrong(it) } ?: return text.substring(from)
        val open = ArrayDeque<Int>()
        for (i in from until strong.start) {
            when (text[i]) {
                '(', '[' -> open.addLast(i)
                ')', ']' -> open.removeLastOrNull()
            }
        }
        return text.substring(from, open.firstOrNull() ?: strong.start)
    }

    /** The cleaned [part], else the cleaned [whole], else [whole] trimmed. */
    private fun titleOrWhole(part: String, whole: String, years: IntRange): String {
        val dashes = dashSeparated(whole)
        return clean(part, years, dashes).ifEmpty { clean(whole, years, dashes) }.ifEmpty { whole.trim() }
    }

    private fun clean(text: String, years: IntRange, dashes: Boolean): String {
        val masked = withoutGroups(text, years)
        val ungrouped = trailingGroupStart(masked, years)?.let { masked.substring(0, it) } ?: masked
        val spaced = TITLE_SEPARATOR.replace(ungrouped) { match ->
            when {
                match.groups[1] != null -> match.value
                match.groups[2] != null -> match.value + " "
                else -> " "
            }
        }
        val words = if (dashes) spaced.replace('-', ' ') else spaced
        return WHITESPACE.replace(words, " ").trim { it in TRIM_CHARS }
    }

    /** [text] with each `[...]` group holding no year and each non-year `(...)` group blanked to spaces. */
    private fun withoutGroups(text: String, years: IntRange): String {
        val squares = SQUARE_GROUP.replace(text) { group ->
            if (tokens(group.value).any { yearOf(it, years) != null }) group.value else " ".repeat(group.value.length)
        }
        return ROUND_GROUP.replace(squares) { group ->
            val inner = group.value.substring(1, group.value.length - 1).trim()
            if (yearOf(Token(inner, 0, inner.length), years) != null) group.value else " ".repeat(group.value.length)
        }
    }

    /** Where a release-group suffix (`-GROUP`) starts, only right after a junk token or a year, so `Spider-Man` stays. */
    private fun trailingGroupStart(text: String, years: IntRange): Int? {
        val start = TRAILING_GROUP.find(text)?.range?.first ?: return null
        val before = tokens(text.substring(0, start))
        val previous = before.lastOrNull()?.takeIf { it.end == start } ?: return null
        val isYear = { token: Token -> yearOf(token, years) != null }
        val afterJunk = isStrong(previous) || isYear(previous) ||
            (isWeak(previous) && before.any { isStrong(it) || isYear(it) })
        return start.takeIf { afterJunk }
    }
}
