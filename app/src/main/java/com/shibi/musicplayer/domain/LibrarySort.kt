package com.shibi.musicplayer.domain

import java.text.CollationKey
import java.text.Collator
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** What a library list is ordered by. Songs offer all six; Videos have no artist or album. */
enum class SortField { Date, Name, Artist, Album, Size, Length }

/**
 * A list's order: [field] from its low end ([ascending]: oldest, A, smallest, shortest) or from
 * its high end. Items with no value for the field (no date, an unknown artist, no size) come
 * last in both directions.
 */
data class SortSpec(val field: SortField, val ascending: Boolean) {
    /** The stored form, e.g. `date_desc`. */
    fun encode(): String = field.name.lowercase(Locale.ROOT) + if (ascending) "_asc" else "_desc"

    companion object {
        /** The option [raw] names, else [fallback]: a missing, old or foreign value never sticks. */
        fun decode(raw: String?, options: List<SortSpec>, fallback: SortSpec): SortSpec =
            options.firstOrNull { it.encode() == raw } ?: fallback
    }
}

/** Each list's menu options, in menu order, and its default. */
object LibrarySorts {
    val VideoDefault = SortSpec(SortField.Date, ascending = false)
    val MusicDefault = SortSpec(SortField.Date, ascending = false)

    val VideoOptions: List<SortSpec> = listOf(
        SortSpec(SortField.Date, ascending = false),
        SortSpec(SortField.Date, ascending = true),
        SortSpec(SortField.Name, ascending = true),
        SortSpec(SortField.Name, ascending = false),
        SortSpec(SortField.Size, ascending = false),
        SortSpec(SortField.Size, ascending = true),
        SortSpec(SortField.Length, ascending = false),
        SortSpec(SortField.Length, ascending = true),
    )

    val MusicOptions: List<SortSpec> = listOf(
        SortSpec(SortField.Date, ascending = false),
        SortSpec(SortField.Date, ascending = true),
        SortSpec(SortField.Name, ascending = true),
        SortSpec(SortField.Name, ascending = false),
        SortSpec(SortField.Artist, ascending = true),
        SortSpec(SortField.Artist, ascending = false),
        SortSpec(SortField.Album, ascending = true),
        SortSpec(SortField.Album, ascending = false),
        SortSpec(SortField.Size, ascending = false),
        SortSpec(SortField.Size, ascending = true),
        SortSpec(SortField.Length, ascending = false),
        SortSpec(SortField.Length, ascending = true),
    )
}

/**
 * The group an item falls in under a sort: its header in the Videos list and its fast-scroll
 * section. The sorter orders by group first, so each group is one run and [key] is unique in
 * a list (it is also the header's lazy-list key).
 */
sealed interface SortGroup {
    val key: String

    /** No date, name, size or length: always the last run. */
    val isUnknown: Boolean

    /** A calendar month under the date sort; [year] and [month] are null for an unknown date. */
    data class Month(val year: Int?, val month: Int?) : SortGroup {
        override val key: String =
            if (year == null || month == null) "month_unknown" else "month_%04d_%02d".format(Locale.ROOT, year, month)
        override val isUnknown: Boolean get() = year == null || month == null
    }

    /** A first letter ([SortLetters.of]) under a name, artist or album sort; null for an unknown name. */
    data class Letter(val letter: String?) : SortGroup {
        override val key: String = "letter_" + (letter ?: "unknown")
        override val isUnknown: Boolean get() = letter == null
    }

    /** A range from [SortBands] under a size or length sort; null when the value is unknown. */
    data class Band(val index: Int?) : SortGroup {
        override val key: String = "band_" + (index?.toString() ?: "unknown")
        override val isUnknown: Boolean get() = index == null
    }
}

/** One group's items: [count] items from [start] of [SortedList.items]. */
data class SortRun(val group: SortGroup, val start: Int, val count: Int)

/** A list in [spec] order and its groups, in list order, one run per group. */
data class SortedList<T>(val spec: SortSpec, val items: List<T>, val runs: List<SortRun>)

/**
 * Size and length ranges for the size and length sorts, as each band's lower edge: band i
 * holds [edges[i], edges[i + 1]) and the last band is open. Sizes are decimal (1 MB is
 * 1,000,000 bytes, like [VideoFormat.size]) and are banded by the size a row shows: a 9.97 MB
 * file reads "10 MB", so it is in 10-100 MB, never under "Under 10 MB". Lengths are exact,
 * since a row floors its seconds: 59.9 s reads "0:59" and is under a minute.
 */
object SortBands {
    private const val MB = 1_000_000L
    private const val GB = 1_000_000_000L
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    /** Under 10 MB, 10-100 MB, 100 MB-1 GB, 1-4 GB, 4 GB and up. */
    val VideoSize: List<Long> = listOf(0L, 10 * MB, 100 * MB, GB, 4 * GB)

    /** Under 1 min, 1-5, 5-20, 20-60 min, 1-2 h, 2 h and up. */
    val VideoLength: List<Long> = listOf(0L, MINUTE, 5 * MINUTE, 20 * MINUTE, HOUR, 2 * HOUR)

    /** Under 5 MB, 5-10, 10-20, 20-50, 50 MB and up: songs would otherwise all be 10-100 MB. */
    val MusicSize: List<Long> = listOf(0L, 5 * MB, 10 * MB, 20 * MB, 50 * MB)

    /** Under 2 min, 2-4, 4-6, 6-10, 10 min and up. */
    val MusicLength: List<Long> = listOf(0L, 2 * MINUTE, 4 * MINUTE, 6 * MINUTE, 10 * MINUTE)

    /** The band [value] falls in: the last one whose lower edge it reaches. */
    fun band(value: Long, edges: List<Long>): Int = edges.indexOfLast { value >= it }.coerceAtLeast(0)
}

/** First-letter groups for the name, artist and album sorts. */
object SortLetters {
    /** Names that start with a digit, or have no letter at all. */
    const val HASH = "#"

    /**
     * The group letter of [text]: its first letter, upper-cased, with accents folded away (É is
     * E, ñ is N). A letter that is more than a base plus accents stays as it is (ஔ, Æ, 가), so
     * Tamil letters keep their own stops. Leading spaces and punctuation are skipped; a name that
     * starts with a digit, or has no letter, is [HASH].
     */
    fun of(text: String): String {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (Character.isDigit(codePoint)) return HASH
            if (Character.isLetter(codePoint)) return fold(codePoint)
            index += Character.charCount(codePoint)
        }
        return HASH
    }

    private fun fold(codePoint: Int): String {
        val letter = String(Character.toChars(codePoint))
        val decomposed = Normalizer.normalize(letter, Normalizer.Form.NFD)
        val base = decomposed.codePointAt(0)
        val onlyAccents = decomposed.substring(Character.charCount(base)).codePoints()
            .allMatch { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        val folded = if (Character.isLetter(base) && onlyAccents) String(Character.toChars(base)) else letter
        // ß upper-cases to "SS": the group is its first letter.
        val upper = folded.uppercase(Locale.ROOT)
        return String(Character.toChars(upper.codePointAt(0)))
    }
}

/** The app's own placeholders for missing tags, which sort as unknown (in any case) like blank and `<unknown>`. */
data class UnknownNames(val title: String, val artist: String = "", val album: String = "")

/**
 * Sorts the Videos and Songs lists. Pure and allocation-light, so the view models run it off the
 * main thread: every item's keys (collation keys, group, number) are computed once, then
 * compared. The order is group first, then the field's value, then (for artist and album) the
 * title, then the MediaStore id, so equal values keep a stable order and descending is the
 * exact reverse of ascending. Unknown values come last in both directions.
 */
object LibrarySorter {

    fun videos(
        videos: List<Video>,
        spec: SortSpec,
        collator: Collator,
        zone: ZoneId,
        unknownTitle: String,
        /** The length a row shows: MediaStore's, else the one libVLC reported ([ResumePolicy.displayDuration]). */
        lengthOf: (Video) -> Long? = { it.durationMs },
    ): SortedList<Video> {
        val letters = LetterKeys(collator)
        return sort(videos, spec) { video ->
            when (spec.field) {
                SortField.Date -> dateKey(video, video.id, VideoTimeline.recordedAtMs(video), zone)
                SortField.Size -> bandKey(video, video.id, video.sizeBytes?.takeIf { it > 0L }, SortBands.VideoSize, sizes = true)
                SortField.Length -> bandKey(video, video.id, lengthOf(video)?.takeIf { it > 0L }, SortBands.VideoLength, sizes = false)
                // Videos have no artist or album; a stray spec sorts by name.
                SortField.Name, SortField.Artist, SortField.Album ->
                    letters.key(
                        video,
                        video.id,
                        video.title.trim().takeUnless { it.isEmpty() || it.equals(unknownTitle, ignoreCase = true) },
                        null,
                    )
            }
        }
    }

    fun tracks(
        tracks: List<Track>,
        spec: SortSpec,
        collator: Collator,
        zone: ZoneId,
        unknown: UnknownNames,
    ): SortedList<Track> {
        val letters = LetterKeys(collator)
        return sort(tracks, spec) { track ->
            // A placeholder in any case ("Unknown Artist", as Windows Media Player writes it) is unknown.
            val title = TrackDisplay.title(track.title).takeUnless { it.isBlank() || it.equals(unknown.title, ignoreCase = true) }
            when (spec.field) {
                SortField.Date -> dateKey(track, track.id, track.dateAddedSeconds.takeIf { it > 0L }?.times(1_000L), zone)
                SortField.Name -> letters.key(track, track.id, title, null)
                SortField.Artist -> letters.key(
                    track,
                    track.id,
                    TrackDisplay.artist(track.artist)?.takeUnless { it.equals(unknown.artist, ignoreCase = true) },
                    title,
                )
                SortField.Album -> letters.key(
                    track,
                    track.id,
                    TrackDisplay.album(track.album)?.takeUnless { it.equals(unknown.album, ignoreCase = true) },
                    title,
                )
                SortField.Size -> bandKey(track, track.id, track.sizeBytes?.takeIf { it > 0L }, SortBands.MusicSize, sizes = true)
                SortField.Length -> bandKey(track, track.id, track.durationMs.takeIf { it > 0L }, SortBands.MusicLength, sizes = false)
            }
        }
    }

    /** The runs of [groups] in list order. Only sorted input has one run per group. */
    fun runs(groups: List<SortGroup>): List<SortRun> {
        val runs = mutableListOf<SortRun>()
        var start = 0
        for (index in 1..groups.size) {
            if (index == groups.size || groups[index] != groups[start]) {
                if (index > start) runs += SortRun(groups[start], start, index - start)
                start = index
            }
        }
        return runs
    }

    /** The month group of a time in [zone]; null [ms] is the unknown month. */
    fun monthOf(ms: Long?, zone: ZoneId): SortGroup.Month {
        if (ms == null) return SortGroup.Month(null, null)
        val date = Instant.ofEpochMilli(ms).atZone(zone)
        return SortGroup.Month(date.year, date.monthValue)
    }

    private fun <T> sort(items: List<T>, spec: SortSpec, keyOf: (T) -> Keyed<T>): SortedList<T> {
        val (known, unknown) = items.map(keyOf).partition { !it.group.isUnknown }
        val sign = if (spec.ascending) 1 else -1
        val ordered = known.sortedWith { a, b -> sign * compareKnown(a, b) } +
            unknown.sortedWith { a, b -> sign * a.id.compareTo(b.id) }
        return SortedList(spec, ordered.map { it.item }, runs(ordered.map { it.group }))
    }

    private fun compareKnown(a: Keyed<*>, b: Keyed<*>): Int {
        var result = a.groupRank.compareTo(b.groupRank)
        if (result != 0) return result
        result = compareCollation(a.groupText, b.groupText)
        if (result != 0) return result
        // Two letters the collator calls equal still form two groups, never one split in two.
        result = a.group.key.compareTo(b.group.key)
        if (result != 0) return result
        result = compareCollation(a.text, b.text)
        if (result != 0) return result
        result = compareCollation(a.secondText, b.secondText)
        if (result != 0) return result
        result = a.number.compareTo(b.number)
        if (result != 0) return result
        return a.id.compareTo(b.id)
    }

    private fun compareCollation(a: CollationKey?, b: CollationKey?): Int = when {
        a == null && b == null -> 0
        a == null -> -1
        b == null -> 1
        else -> a.compareTo(b)
    }

    private fun <T> dateKey(item: T, id: Long, ms: Long?, zone: ZoneId): Keyed<T> {
        val month = monthOf(ms, zone)
        val rank = if (month.year == null || month.month == null) 0L else month.year * 12L + month.month
        return Keyed(item, id, month, rank, number = ms ?: 0L)
    }

    private fun <T> bandKey(item: T, id: Long, value: Long?, edges: List<Long>, sizes: Boolean): Keyed<T> {
        if (value == null) return Keyed(item, id, SortGroup.Band(null), 0L)
        val shown = if (sizes) VideoFormat.shownBytes(value) else value
        val band = SortBands.band(shown, edges)
        return Keyed(item, id, SortGroup.Band(band), band.toLong(), number = value)
    }

    /** Collation keys for one sort; a letter's own key is made once, however many names share it. */
    private class LetterKeys(private val collator: Collator) {
        private val letterKeys = HashMap<String, CollationKey>()

        fun <T> key(item: T, id: Long, text: String?, secondText: String?): Keyed<T> {
            if (text == null) return Keyed(item, id, SortGroup.Letter(null), 0L)
            val letter = SortLetters.of(text)
            val hash = letter == SortLetters.HASH
            return Keyed(
                item = item,
                id = id,
                group = SortGroup.Letter(letter),
                // Digits and symbols first, like the collator puts "2Pac" before "ABBA".
                groupRank = if (hash) 0L else 1L,
                groupText = if (hash) null else letterKeys.getOrPut(letter) { collator.getCollationKey(letter) },
                text = collator.getCollationKey(text),
                secondText = secondText?.let(collator::getCollationKey),
            )
        }
    }

    /** One item's precomputed keys, compared in the order of [compareKnown]. */
    private class Keyed<T>(
        val item: T,
        val id: Long,
        val group: SortGroup,
        /** Orders the groups: year * 12 + month, a band's index, or 0 for "#" and 1 for a letter. */
        val groupRank: Long,
        val groupText: CollationKey? = null,
        val text: CollationKey? = null,
        val secondText: CollationKey? = null,
        /** The time, size or length. */
        val number: Long = 0L,
    )
}
