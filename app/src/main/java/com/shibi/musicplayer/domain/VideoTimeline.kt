package com.shibi.musicplayer.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Weeks with at least this many videos get a fast-scroll dot: a magnetic stop for the thumb. */
const val MIN_VIDEOS_PER_WEEK_DOT = 25

/** One month group in the video list; year and month are null for videos with no known date. */
data class VideoSection(
    val year: Int?,
    val month: Int?,
    val startIndex: Int,
    val count: Int,
)

sealed interface VideoListEntry {
    /** Stable lazy-list key. */
    val key: Any

    /** Lazy-list content type. */
    val contentType: String

    data class Header(val section: VideoSection) : VideoListEntry {
        override val key: Any =
            if (section.year == null || section.month == null) {
                "month_unknown"
            } else {
                "month_%04d_%02d".format(Locale.ROOT, section.year, section.month)
            }
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
 * One calendar week of the video list, in list order. [start] is the week's first day by the
 * locale's first day of the week, or null for the videos with no known date. [startIndex] is the
 * entry where the week begins: its first row, or the month header right above that row.
 */
data class VideoWeek(
    val start: LocalDate?,
    val startIndex: Int,
    val count: Int,
)

/** Date ordering, month and week grouping and fast-scroll math for the Videos list. Pure Kotlin. */
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

    fun layout(videos: List<Video>, zone: ZoneId): VideoTimelineLayout {
        val entries = mutableListOf<VideoListEntry>()
        val sections = mutableListOf<VideoSection>()
        var currentKey: Pair<Int, Int>? = null
        var hasCurrent = false
        var start = 0
        var count = 0
        var year: Int? = null
        var month: Int? = null

        fun close() {
            if (!hasCurrent) return
            val section = VideoSection(year, month, start, count)
            sections += section
            entries[start] = VideoListEntry.Header(section)
        }

        for (video in videos) {
            val ms = recordedAtMs(video)
            val key = ms?.let {
                val date = Instant.ofEpochMilli(it).atZone(zone)
                date.year to date.monthValue
            }
            if (!hasCurrent || key != currentKey) {
                close()
                hasCurrent = true
                currentKey = key
                year = key?.first
                month = key?.second
                start = entries.size
                count = 0
                // Placeholder, replaced with the real header when the section closes.
                entries += VideoListEntry.Header(VideoSection(year, month, start, 0))
            }
            entries += VideoListEntry.Row(video, sections.size)
            count++
        }
        close()
        return VideoTimelineLayout(entries, sections)
    }

    fun headerLabel(section: VideoSection, locale: Locale): String? = format(section, "LLLL yyyy", locale)

    private fun format(section: VideoSection, pattern: String, locale: Locale): String? {
        val year = section.year ?: return null
        val month = section.month ?: return null
        return java.time.LocalDate.of(year, month, 1).format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    fun entryIndexForFraction(fraction: Float, entryCount: Int): Int {
        if (entryCount <= 0) return 0
        val f = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
        return (f * (entryCount - 1)).toInt().coerceIn(0, entryCount - 1)
    }

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

    fun thumbFraction(firstVisibleIndex: Int, visibleCount: Int, entryCount: Int): Float {
        if (entryCount <= 0 || visibleCount >= entryCount) return 0f
        if (firstVisibleIndex + visibleCount >= entryCount) return 1f
        val maxFirst = entryCount - visibleCount
        return (firstVisibleIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
    }

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
    fun weeks(layout: VideoTimelineLayout, zone: ZoneId, locale: Locale): List<VideoWeek> {
        val firstDay = WeekFields.of(locale).firstDayOfWeek
        val weeks = mutableListOf<VideoWeek>()
        var current: LocalDate? = null
        var start = -1
        var count = 0
        layout.entries.forEachIndexed { index, entry ->
            if (entry !is VideoListEntry.Row) return@forEachIndexed
            val week = recordedAtMs(entry.video)?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(firstDay))
            }
            if (start < 0 || week != current) {
                if (start >= 0) weeks += VideoWeek(current, start, count)
                current = week
                start = if (layout.entries.getOrNull(index - 1) is VideoListEntry.Header) index - 1 else index
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
    fun dotFractions(stops: List<VideoWeek>, entryCount: Int): List<Float> {
        val denominator = maxOf(1, entryCount - 1)
        return stops.map { it.startIndex.toFloat() / denominator }
    }

    /** The week that holds [entryIndex], counting a month header as part of the week below it. */
    fun weekForEntry(weeks: List<VideoWeek>, entryIndex: Int): VideoWeek? {
        if (weeks.isEmpty()) return null
        val found = weeks.binarySearchBy(entryIndex) { it.startIndex }
        return weeks[if (found >= 0) found else (-found - 2).coerceAtLeast(0)]
    }

    /** Where a drag puts the list: the start of the locked dot's week, else the entry under the thumb. */
    fun dragTargetIndex(snap: MagneticSnap, stops: List<VideoWeek>, entryCount: Int): Int =
        snap.lockedIndex?.let { stops.getOrNull(it)?.startIndex }
            ?: entryIndexForFraction(snap.fraction, entryCount)

    /** The week's first day as "MMM d" (e.g. "Sep 4") for the bubble; null when the date is unknown. */
    fun weekLabel(week: VideoWeek, locale: Locale): String? =
        week.start?.format(DateTimeFormatter.ofPattern("MMM d", locale))
}
