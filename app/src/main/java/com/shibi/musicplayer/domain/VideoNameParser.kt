package com.shibi.musicplayer.domain

import java.time.Year

/**
 * Sorts a video into Movie, TV episode, Personal clip or Other from its MediaStore file name
 * (`DISPLAY_NAME`, with extension) and folder (`BUCKET_DISPLAY_NAME`), and cleans its title.
 *
 * Rules, first match wins:
 * 1. Camera, phone, screen-recording or messaging-app file names -> [VideoIdentity.Personal].
 * 2. An episode marker (`S01E02`, `S01E02E03`, `1x02`, `Season 1 Episode 2`) -> [VideoIdentity.Episode].
 * 3. A year (1920 to next year) in brackets, before a junk token, or last -> [VideoIdentity.Movie].
 * 4. A strong junk token (resolution, source, codec) -> [VideoIdentity.Movie] without a year.
 * 5. A camera folder (`Camera`, `DCIM`, ...) -> [VideoIdentity.Personal].
 * 6. Still Other, or a Movie title of 2 characters or fewer: the folder's Movie, if it parses as one.
 * 7. Otherwise [VideoIdentity.Other].
 *
 * Known false positive: a trailing year always qualifies, so `Vacation 2019.mp4` is
 * `Movie("Vacation", 2019)` even when it is a home video.
 */
object VideoNameParser {
    fun parse(fileName: String, folder: String?): VideoIdentity =
        parse(fileName, folder, Year.now().value)

    /** Pure: same input, same output. [currentYear] bounds the accepted years at `currentYear + 1`. */
    fun parse(fileName: String, folder: String?, currentYear: Int): VideoIdentity {
        val years = MIN_YEAR..currentYear + 1
        val stem = fileName.substringBeforeLast('.', fileName).ifEmpty { fileName }

        cameraSource(stem)?.let { return VideoIdentity.Personal(it) }
        episode(stem, folder, years)?.let { return it }
        val movie = movie(stem, years)
        if (movie != null && movie.title.length > SHORT_TITLE) return movie
        if (movie == null && folder != null && folder.trim().lowercase() in CAMERA_FOLDERS) {
            return VideoIdentity.Personal(CameraSource.OTHER_CAMERA)
        }
        val folderMovie = folder?.let { movie(it, years) }
        return folderMovie ?: movie ?: VideoIdentity.Other(titleOrStem(stem, stem, years))
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

    /** A dot or underscore to turn into a space, unless it sits inside a joined junk token. */
    private val TITLE_SEPARATOR = Regex(
        """(?<![^$SEP])($JOINED_JUNK)(?![^$SEP])|[._]""",
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
        """S(\d{1,2})[ ._-]?E(\d{1,3})(?:-?E(\d{1,3}))?""",
        """(\d{1,2})x(\d{2,3})""",
        """Season[ ._-]*(\d{1,3})[ ._-]*Episode[ ._-]*(\d{1,4})""",
    ).map { Regex("""(?<![A-Za-z0-9])(?:$it)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE) }

    private val SEASON_FOLDER = Regex("""Season[ ._-]*\d+|S\d+""", RegexOption.IGNORE_CASE)

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

    private fun cameraSource(stem: String): CameraSource? =
        CAMERA_PREFIXES.firstOrNull { (regex, _) -> regex.containsMatchIn(stem) }?.second
            ?: CAMERA_STEMS.firstOrNull { (regex, _) -> regex.matches(stem) }?.second

    private fun episode(stem: String, folder: String?, years: IntRange): VideoIdentity.Episode? {
        val marker = EPISODE_MARKERS.mapNotNull { it.find(stem) }.minByOrNull { it.range.first } ?: return null
        val season = marker.groupValues[1].toInt()
        val episode = marker.groupValues[2].toInt()
        val lastEpisode = marker.groupValues.getOrNull(3)?.toIntOrNull()?.takeIf { it > episode }

        val show = clean(stem.substring(0, marker.range.first), years).ifEmpty { null }
            ?: folder?.takeUnless { SEASON_FOLDER.matches(it.trim()) }?.let { clean(it, years) }?.ifEmpty { null }
            ?: titleOrStem(stem, stem, years)

        val titleStart = marker.range.last + 1
        val titleEnd = tokens(stem).firstOrNull { it.start >= titleStart && isStrong(it) }?.start ?: stem.length
        val episodeTitle = clean(stem.substring(titleStart, titleEnd), years).ifEmpty { null }

        return VideoIdentity.Episode(show, season, episode, lastEpisode, episodeTitle)
    }

    /** Rules 3 and 4 on [text], a file stem or a folder name. */
    private fun movie(text: String, years: IntRange): VideoIdentity.Movie? {
        val tokens = tokens(text)
        for (i in tokens.indices.reversed()) {
            val token = tokens[i]
            val year = yearOf(token, years) ?: continue
            val enclosed = token.start > 0 && token.end < text.length &&
                text[token.start - 1] in "([" && text[token.end] in ")]"
            val next = tokens.getOrNull(i + 1)
            if (!enclosed && next != null && !isStrong(next) && !isWeak(next)) continue
            val title = clean(text.substring(0, if (enclosed) token.start - 1 else token.start), years)
            if (title.isNotEmpty()) return VideoIdentity.Movie(title, year)
        }
        val firstStrong = tokens.firstOrNull(::isStrong) ?: return null
        return VideoIdentity.Movie(titleOrStem(text.substring(0, firstStrong.start), text, years), null)
    }

    /** The cleaned [part], else the cleaned [whole], else [whole] as is: never empty unless [whole] is. */
    private fun titleOrStem(part: String, whole: String, years: IntRange): String =
        clean(part, years).ifEmpty { clean(whole, years) }.ifEmpty { whole.trim() }.ifEmpty { whole }

    private fun clean(text: String, years: IntRange): String {
        var s = SQUARE_GROUP.replace(text) { group ->
            if (tokens(group.value).any { yearOf(it, years) != null }) group.value else " "
        }
        s = ROUND_GROUP.replace(s) { group ->
            val inner = group.value.substring(1, group.value.length - 1).trim()
            if (yearOf(Token(inner, 0, inner.length), years) != null) group.value else " "
        }
        s = dropTrailingGroup(s, years)
        s = TITLE_SEPARATOR.replace(s) { if (it.groups[1] != null) it.value else " " }
        return WHITESPACE.replace(s, " ").trim { it in TRIM_CHARS }
    }

    /** Drops a release-group suffix (`-GROUP`) only right after a junk token or a year, so `Spider-Man` stays. */
    private fun dropTrailingGroup(text: String, years: IntRange): String {
        val group = TRAILING_GROUP.find(text) ?: return text
        val before = tokens(text.substring(0, group.range.first))
        val previous = before.lastOrNull()?.takeIf { it.end == group.range.first } ?: return text
        val isYear = { token: Token -> yearOf(token, years) != null }
        val afterJunk = isStrong(previous) || isYear(previous) ||
            (isWeak(previous) && before.any { isStrong(it) || isYear(it) })
        return if (afterJunk) text.substring(0, group.range.first) else text
    }
}
