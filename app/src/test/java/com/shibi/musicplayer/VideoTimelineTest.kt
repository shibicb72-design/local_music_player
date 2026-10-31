package com.shibi.musicplayer

import com.shibi.musicplayer.domain.FAST_SCROLL_HIDE_DELAY_MS
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoListEntry
import com.shibi.musicplayer.domain.VideoSection
import com.shibi.musicplayer.domain.VideoTimeline
import com.shibi.musicplayer.domain.fastScrollVisible
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTimelineTest {
    private val ny = ZoneId.of("America/New_York")

    private fun ms(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun video(id: Long, takenMs: Long = 0L, addedSeconds: Long = 0L) = Video(
        id = id,
        title = "v$id",
        displayName = "v$id.mp4",
        durationMs = null,
        sizeBytes = null,
        width = null,
        height = null,
        mimeType = null,
        dateAddedSeconds = addedSeconds,
        folder = null,
        contentUri = "content://v/$id",
        dateTakenMs = takenMs,
    )

    @Test
    fun dateTakenInMillisecondsIsUsed() {
        val t = ms("2025-03-10T12:00:00Z")
        assertEquals(t, VideoTimeline.recordedAtMs(video(1, takenMs = t, addedSeconds = 5)))
    }

    @Test
    fun dateTakenInSecondsIsNormalised() {
        val t = ms("2025-03-10T12:00:00Z")
        assertEquals(t, VideoTimeline.recordedAtMs(video(1, takenMs = t / 1000)))
    }

    @Test
    fun missingOrNegativeDateTakenFallsBackToDateAdded() {
        assertEquals(7_000L, VideoTimeline.recordedAtMs(video(1, takenMs = 0, addedSeconds = 7)))
        assertEquals(7_000L, VideoTimeline.recordedAtMs(video(1, takenMs = -5, addedSeconds = 7)))
    }

    @Test
    fun noDatesIsNullAndSortsLast() {
        val none = video(1)
        val dated = video(2, addedSeconds = 10)
        assertNull(VideoTimeline.recordedAtMs(none))
        assertEquals(listOf(2L, 1L), VideoTimeline.sortNewestFirst(listOf(none, dated)).map { it.id })
    }

    @Test
    fun sortsNewestFirstWithTiesByIdDescending() {
        val t = ms("2025-03-10T12:00:00Z")
        val sorted = VideoTimeline.sortNewestFirst(
            listOf(video(1, takenMs = t), video(3, takenMs = t), video(2, takenMs = t + 1)),
        )
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    private fun sampleLayout() = VideoTimeline.layout(
        listOf(
            video(5, takenMs = ms("2026-01-15T12:00:00Z")),
            video(4, takenMs = ms("2025-03-15T12:00:00Z")),
            video(3, takenMs = ms("2025-03-02T12:00:00Z")),
            video(2, takenMs = ms("2025-01-02T12:00:00Z")),
            video(1, takenMs = ms("2024-12-20T12:00:00Z")),
        ),
        ny,
    )

    @Test
    fun layoutGroupsConsecutiveMonthsIntoHeadersAndRows() {
        val layout = sampleLayout()
        assertEquals(
            listOf(
                VideoSection(2026, 1, 0, 1),
                VideoSection(2025, 3, 2, 2),
                VideoSection(2025, 1, 5, 1),
                VideoSection(2024, 12, 7, 1),
            ),
            layout.sections,
        )
        assertEquals(9, layout.entries.size)
        val header = layout.entries[2] as VideoListEntry.Header
        assertEquals("month_2025_03", header.key)
        assertEquals("month_header", header.contentType)
        assertEquals(layout.sections[1], header.section)
        val row = layout.entries[3] as VideoListEntry.Row
        assertEquals(4L, row.key)
        assertEquals("video", row.contentType)
        assertEquals(1, row.sectionIndex)
    }

    @Test
    fun layoutUsesTheGivenZoneAtAMonthBoundary() {
        val boundary = ms("2025-03-01T03:00:00Z")
        val inNewYork = VideoTimeline.layout(listOf(video(1, takenMs = boundary)), ny)
        assertEquals(2, inNewYork.sections.single().month)
        val inUtc = VideoTimeline.layout(listOf(video(1, takenMs = boundary)), ZoneId.of("UTC"))
        assertEquals(3, inUtc.sections.single().month)
    }

    @Test
    fun unknownDateGetsAnUnknownSectionWithNullLabels() {
        val layout = VideoTimeline.layout(listOf(video(1)), ny)
        val section = layout.sections.single()
        assertNull(section.year)
        assertEquals("month_unknown", (layout.entries[0] as VideoListEntry.Header).key)
        assertNull(VideoTimeline.headerLabel(section, Locale.US))
        assertNull(VideoTimeline.bubbleLabel(section, Locale.US))
    }

    @Test
    fun labelsFormatMonthAndYear() {
        val section = VideoSection(2025, 3, 0, 1)
        assertEquals("March 2025", VideoTimeline.headerLabel(section, Locale.US))
        assertEquals("Mar 2025", VideoTimeline.bubbleLabel(section, Locale.US))
    }

    @Test
    fun entryIndexForFractionClamps() {
        assertEquals(0, VideoTimeline.entryIndexForFraction(0f, 10))
        assertEquals(4, VideoTimeline.entryIndexForFraction(0.5f, 10))
        assertEquals(9, VideoTimeline.entryIndexForFraction(1f, 10))
        assertEquals(0, VideoTimeline.entryIndexForFraction(-3f, 10))
        assertEquals(9, VideoTimeline.entryIndexForFraction(7f, 10))
        assertEquals(0, VideoTimeline.entryIndexForFraction(0.5f, 0))
    }

    @Test
    fun sectionIndexForEntryFindsTheContainingSection() {
        val layout = sampleLayout()
        assertEquals(0, VideoTimeline.sectionIndexForEntry(layout, 0))
        assertEquals(0, VideoTimeline.sectionIndexForEntry(layout, 1))
        assertEquals(1, VideoTimeline.sectionIndexForEntry(layout, 2))
        assertEquals(1, VideoTimeline.sectionIndexForEntry(layout, 4))
        assertEquals(2, VideoTimeline.sectionIndexForEntry(layout, 5))
        assertEquals(3, VideoTimeline.sectionIndexForEntry(layout, 8))
    }

    @Test
    fun snapIndexLandsOnAHeader() {
        val layout = sampleLayout()
        val headerStarts = layout.sections.map { it.startIndex }.toSet()
        for (i in 0..20) {
            assertTrue(VideoTimeline.snapIndexForFraction(layout, i / 20f) in headerStarts)
        }
        assertEquals(0, VideoTimeline.snapIndexForFraction(layout, 0f))
        assertEquals(7, VideoTimeline.snapIndexForFraction(layout, 1f))
    }

    @Test
    fun thumbFractionEdges() {
        assertEquals(0f, VideoTimeline.thumbFraction(0, 5, 20), 0f)
        assertEquals(1f, VideoTimeline.thumbFraction(15, 5, 20), 0f)
        assertEquals(0.5f, VideoTimeline.thumbFraction(5, 5, 20) * 1.5f, 0.0001f)
        assertEquals(0f, VideoTimeline.thumbFraction(0, 10, 10), 0f)
        assertEquals(0f, VideoTimeline.thumbFraction(0, 0, 0), 0f)
    }

    @Test
    fun yearTicksUseEachYearsFirstSection() {
        val layout = VideoTimeline.layout(
            listOf(
                video(4, takenMs = ms("2026-02-15T12:00:00Z")),
                video(3, takenMs = ms("2025-06-15T12:00:00Z")),
                video(2, takenMs = ms("2025-03-15T12:00:00Z")),
                video(1, takenMs = ms("2024-03-15T12:00:00Z")),
            ),
            ny,
        )
        // Entries: H R H R H R H R = 8 entries, denominator 7.
        assertEquals(
            listOf(2026 to 0f, 2025 to 2f / 7f, 2024 to 6f / 7f),
            VideoTimeline.yearTicks(layout),
        )
    }

    @Test
    fun fastScrollVisibleWhileActiveAndForTheHideDelay() {
        assertEquals(2_000L, FAST_SCROLL_HIDE_DELAY_MS)
        assertTrue(fastScrollVisible(true, null, 100_000L))
        assertTrue(fastScrollVisible(false, 1_000L, 1_000L))
        assertTrue(fastScrollVisible(false, 1_000L, 2_999L))
        assertFalse(fastScrollVisible(false, 1_000L, 3_000L))
        assertFalse(fastScrollVisible(false, null, 0L))
    }
}
