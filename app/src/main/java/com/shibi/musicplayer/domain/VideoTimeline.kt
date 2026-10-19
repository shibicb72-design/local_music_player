package com.shibi.musicplayer.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

/** Date ordering, month grouping and fast-scroll math for the Videos list. Pure Kotlin. */
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

    fun bubbleLabel(section: VideoSection, locale: Locale): String? = format(section, "LLL yyyy", locale)

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

    fun snapIndexForFraction(layout: VideoTimelineLayout, fraction: Float): Int {
        if (layout.sections.isEmpty()) return 0
        val entry = entryIndexForFraction(fraction, layout.entries.size)
        return layout.sections[sectionIndexForEntry(layout, entry)].startIndex
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
}
