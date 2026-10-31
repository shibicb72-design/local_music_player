package com.shibi.musicplayer.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Weeks with at least this many videos (or songs) get a fast-scroll dot: a magnetic stop for the thumb. */
const val MIN_VIDEOS_PER_WEEK_DOT = 25

/**
 * One header group in the video list: a month under the date sort, else the sort's letter or
 * band ([SortGroup]). [startIndex] is the header's entry; [count] counts the rows under it.
 */
data class VideoSection(
    val group: SortGroup,
    val startIndex: Int,
    val count: Int,
) {
    /** A month section; year and month are null for videos with no known date. */
    constructor(year: Int?, month: Int?, startIndex: Int, count: Int) :
        this(SortGroup.Month(year, month), startIndex, count)

    val year: Int? get() = (group as? SortGroup.Month)?.year
    val month: Int? get() = (group as? SortGroup.Month)?.month
}

sealed interface VideoListEntry {
    /** Stable lazy-list key. */
    val key: Any

    /** Lazy-list content type. */
    val contentType: String

    data class Header(val section: VideoSection) : VideoListEntry {
        // Unique in a list: the sorter puts each group in one run.
        override val key: Any = section.group.key
        override val contentType: String = "month_header"
    }

    data class Row(val video: Video, val sectionIndex: Int) : VideoListEntry {
        override val key: Any = video.id
        override val contentType: String = "video"
    }
}

data class VideoTimelineLayout(
    val entries: List<VideoListEntry>,
    val sections: List<VideoSection>,
)

/**
 * One calendar week of a list (videos or songs), in list order. [start] is the week's first
 * day by the locale's first day of the week, or null for the items with no known date.
 * [startIndex] is the entry where the week begins: its first row, or the header right above it.
 */
data class VideoWeek(
    val start: LocalDate?,
    val startIndex: Int,
    val count: Int,
)

/** Date ordering, header and week grouping and fast-scroll math for the Videos list. Pure Kotlin. */
object VideoTimeline {
    private const val SECONDS_LIMIT = 100_000_000_000L

    fun recordedAtMs(video: Video): Long? {
        if (video.dateTakenMs > 0L) {
            return if (video.dateTakenMs < SECONDS_LIMIT) video.dateTakenMs * 1000L else video.dateTakenMs
        }
        if (video.dateAddedSeconds > 0L) return video.dateAddedSeconds * 1000L
        return null
    }

    fun sortNewestFirst(videos: List<Video>): List<Video> =
        videos.sortedWith(
            compareByDescending<Video> { recordedAtMs(it) ?: Long.MIN_VALUE }
                .thenByDescending { it.id },
        )

    /** Month headers over [videos] in the order given, which should be newest or oldest first. */
    fun layout(videos: List<Video>, zone: ZoneId): VideoTimelineLayout =
        layout(videos, LibrarySorter.runs(videos.map { LibrarySorter.monthOf(recordedAtMs(it), zone) }))

    /** One header per group of a sorted list, so header keys never repeat. */
    fun layout(sorted: SortedList<Video>): VideoTimelineLayout = layout(sorted.items, sorted.runs)

    /** A header, then its rows, for each of [runs], which cover [videos] in order. */
    fun layout(videos: List<Video>, runs: List<SortRun>): VideoTimelineLayout {
        val entries = ArrayList<VideoListEntry>(videos.size + runs.size)
        val sections = ArrayList<VideoSection>(runs.size)
        for (run in runs) {
            val section = VideoSection(run.group, entries.size, run.count)
            sections += section
            entries += VideoListEntry.Header(section)
            for (index in run.start until run.start + run.count) {
                entries += VideoListEntry.Row(videos[index], sections.lastIndex)
            }
        }
        return VideoTimelineLayout(entries, sections)
    }

    /** "March 2025" for a month section; null for any other section, which the caller names. */
    fun headerLabel(section: VideoSection, locale: Locale): String? = monthLabel(section.year, section.month, locale)

    /** "March 2025"; null when the month is unknown. */
    fun monthLabel(year: Int?, month: Int?, locale: Locale): String? {
        if (year == null || month == null) return null
        return LocalDate.of(year, month, 1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    }

    fun entryIndexForFraction(fraction: Float, entryCount: Int): Int =
        com.shibi.musicplayer.domain.entryIndexForFraction(fraction, entryCount)

    fun sectionIndexForEntry(layout: VideoTimelineLayout, entryIndex: Int): Int {
        val sections = layout.sections
        if (sections.isEmpty()) return 0
        var low = 0
        var high = sections.lastIndex
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (sections[mid].startIndex <= entryIndex) low = mid else high = mid - 1
        }
        return low
    }

    fun thumbFraction(firstVisibleIndex: Int, visibleCount: Int, entryCount: Int): Float =
        com.shibi.musicplayer.domain.thumbFraction(firstVisibleIndex, visibleCount, entryCount)

    fun yearTicks(layout: VideoTimelineLayout): List<Pair<Int, Float>> {
        val denominator = maxOf(1, layout.entries.size - 1)
        val seen = mutableSetOf<Int>()
        val ticks = mutableListOf<Pair<Int, Float>>()
        for (section in layout.sections) {
            val year = section.year ?: continue
            if (seen.add(year)) ticks += year to section.startIndex.toFloat() / denominator
        }
        return ticks
    }

    /**
     * The calendar weeks of [layout], in list order. A week starts on the [locale]'s first day of
     * the week, so it can run across a month header or into a new year; the undated videos at the
     * end form one week with a null start.
     */
    fun weeks(layout: VideoTimelineLayout, zone: ZoneId, locale: Locale): List<VideoWeek> =
        weeks(
            entryCount = layout.entries.size,
            zone = zone,
            locale = locale,
            isHeader = { layout.entries[it] is VideoListEntry.Header },
            timeAt = { (layout.entries[it] as? VideoListEntry.Row)?.video?.let(::recordedAtMs) },
        )

    /**
     * The calendar weeks of any list of [entryCount] entries (videos with headers, songs without),
     * in list order. [timeAt] is a row's time in ms (null: no date); a header ([isHeader]) counts
     * as part of the week of the row below it.
     */
    fun weeks(
        entryCount: Int,
        zone: ZoneId,
        locale: Locale,
        isHeader: (Int) -> Boolean,
        timeAt: (Int) -> Long?,
    ): List<VideoWeek> {
        val firstDay = WeekFields.of(locale).firstDayOfWeek
        val weeks = mutableListOf<VideoWeek>()
        var current: LocalDate? = null
        var start = -1
        var count = 0
        for (index in 0 until entryCount) {
            if (isHeader(index)) continue
            val week = timeAt(index)?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(firstDay))
            }
            if (start < 0 || week != current) {
                if (start >= 0) weeks += VideoWeek(current, start, count)
                current = week
                start = if (index > 0 && isHeader(index - 1)) index - 1 else index
                count = 0
            }
            count++
        }
        if (start >= 0) weeks += VideoWeek(current, start, count)
        return weeks
    }

    /** The weeks busy enough for a fast-scroll dot, in list order. Undated videos never get one. */
    fun weekStops(weeks: List<VideoWeek>, minVideos: Int = MIN_VIDEOS_PER_WEEK_DOT): List<VideoWeek> =
        weeks.filter { it.start != null && it.count >= minVideos }

    /** Each stop's place on the track, on the same scale as [entryIndexForFraction] and [yearTicks]. */
    fun dotFractions(stops: List<VideoWeek>, entryCount: Int): List<Float> =
        stopFractions(stops.map { it.section() }, entryCount)

    /** The week that holds [entryIndex], counting a month header as part of the week below it. */
    fun weekForEntry(weeks: List<VideoWeek>, entryIndex: Int): VideoWeek? =
        sectionIndexAt(weeks.map { it.section() }, entryIndex)?.let(weeks::get)

    /** Where a drag puts the list: the start of the locked dot's week, else the entry under the thumb. */
    fun dragTargetIndex(snap: MagneticSnap, stops: List<VideoWeek>, entryCount: Int): Int =
        com.shibi.musicplayer.domain.dragTargetIndex(snap, stops.map { it.section() }, entryCount)

    /** The week's first day as "MMM d" (e.g. "Sep 4") for the bubble; null when the date is unknown. */
    fun weekLabel(week: VideoWeek, locale: Locale): String? =
        week.start?.format(DateTimeFormatter.ofPattern("MMM d", locale))
}

/** The week as a generic fast-scroll section. */
fun VideoWeek.section(): ScrollSection = ScrollSection(startIndex, count)
