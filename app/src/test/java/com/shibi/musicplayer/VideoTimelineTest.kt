package com.shibi.musicplayer

import com.shibi.musicplayer.domain.FAST_SCROLL_HIDE_DELAY_MS
import com.shibi.musicplayer.domain.MIN_VIDEOS_PER_WEEK_DOT
import com.shibi.musicplayer.domain.MagneticSnap
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoListEntry
import com.shibi.musicplayer.domain.VideoSection
import com.shibi.musicplayer.domain.VideoTimeline
import com.shibi.musicplayer.domain.VideoWeek
import com.shibi.musicplayer.domain.fastScrollVisible
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTimelineTest {
    private val ny = ZoneId.of("America/New_York")
    private val utc = ZoneId.of("UTC")

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
    }

    @Test
    fun headerLabelFormatsMonthAndYear() {
        val section = VideoSection(2025, 3, 0, 1)
        assertEquals("March 2025", VideoTimeline.headerLabel(section, Locale.US))
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

    /** [count] videos taken at [iso], ids from [firstId] up. */
    private fun clips(iso: String, count: Int, firstId: Long): List<Video> =
        (0 until count).map { video(firstId + it, takenMs = ms(iso)) }

    private fun utcLayout(videos: List<Video>) = VideoTimeline.layout(VideoTimeline.sortNewestFirst(videos), utc)

    /**
     * 25, 24 and 26 videos on the Wednesdays of three weeks in a row (the same weeks whichever day
     * the locale starts on). Entries: header 0, rows 1-25, rows 26-49, rows 50-75.
     */
    private fun threeWeeks() = utcLayout(
        clips("2025-03-19T12:00:00Z", 25, 100) +
            clips("2025-03-12T12:00:00Z", 24, 200) +
            clips("2025-03-05T12:00:00Z", 26, 300),
    )

    @Test
    fun weeksStartOnTheLocalesFirstDayOfTheWeek() {
        // Monday 10 and Sunday 9 March 2025. Entries: header, row, row.
        val layout = utcLayout(
            listOf(video(2, takenMs = ms("2025-03-10T12:00:00Z")), video(1, takenMs = ms("2025-03-09T12:00:00Z"))),
        )
        assertEquals(
            listOf(VideoWeek(LocalDate.of(2025, 3, 9), 0, 2)),
            VideoTimeline.weeks(layout, utc, Locale.US),
        )
        assertEquals(
            listOf(VideoWeek(LocalDate.of(2025, 3, 10), 0, 1), VideoWeek(LocalDate.of(2025, 3, 3), 2, 1)),
            VideoTimeline.weeks(layout, utc, Locale.UK),
        )
    }

    @Test
    fun weeksUseTheGivenZone() {
        // 03:00 UTC on Monday 10 March 2025 is still Sunday evening in New York.
        val videos = listOf(video(1, takenMs = ms("2025-03-10T03:00:00Z")))
        val inUtc = VideoTimeline.weeks(VideoTimeline.layout(videos, utc), utc, Locale.UK)
        assertEquals(LocalDate.of(2025, 3, 10), inUtc.single().start)
        val inNewYork = VideoTimeline.weeks(VideoTimeline.layout(videos, ny), ny, Locale.UK)
        assertEquals(LocalDate.of(2025, 3, 3), inNewYork.single().start)
    }

    @Test
    fun aWeekRunsAcrossTheNewYearAndBothMonthHeaders() {
        // Friday 2 January 2026 and Tuesday 30 December 2025. Entries: Jan header, row, Dec header, row.
        val layout = utcLayout(
            listOf(video(2, takenMs = ms("2026-01-02T12:00:00Z")), video(1, takenMs = ms("2025-12-30T12:00:00Z"))),
        )
        val us = VideoTimeline.weeks(layout, utc, Locale.US)
        assertEquals(listOf(VideoWeek(LocalDate.of(2025, 12, 28), 0, 2)), us)
        assertEquals(listOf(VideoWeek(LocalDate.of(2025, 12, 29), 0, 2)), VideoTimeline.weeks(layout, utc, Locale.UK))
        assertEquals(us.single(), VideoTimeline.weekForEntry(us, 2))
        assertEquals("Dec 28", VideoTimeline.weekLabel(us.single(), Locale.US))
    }

    @Test
    fun onlyWeeksWithAtLeast25VideosBecomeStops() {
        assertEquals(25, MIN_VIDEOS_PER_WEEK_DOT)
        val layout = threeWeeks()
        val weeks = VideoTimeline.weeks(layout, utc, Locale.US)
        assertEquals(listOf(25, 24, 26), weeks.map { it.count })
        assertEquals(listOf(0, 26, 50), weeks.map { it.startIndex })
        val stops = VideoTimeline.weekStops(weeks)
        assertEquals(listOf(weeks[0], weeks[2]), stops)
        assertEquals(listOf(0f, 50f / 75f), VideoTimeline.dotFractions(stops, layout.entries.size))
        // The 24-video week is one short; with the bar at 24 it gets a dot too.
        assertEquals(weeks, VideoTimeline.weekStops(weeks, minVideos = 24))
    }

    @Test
    fun weekForEntryFindsTheWeekOfARowOrHeader() {
        val weeks = VideoTimeline.weeks(threeWeeks(), utc, Locale.US)
        assertEquals(weeks[0], VideoTimeline.weekForEntry(weeks, 0))
        assertEquals(weeks[0], VideoTimeline.weekForEntry(weeks, 25))
        assertEquals(weeks[1], VideoTimeline.weekForEntry(weeks, 26))
        assertEquals(weeks[1], VideoTimeline.weekForEntry(weeks, 49))
        assertEquals(weeks[2], VideoTimeline.weekForEntry(weeks, 50))
        assertEquals(weeks[2], VideoTimeline.weekForEntry(weeks, 75))
        assertEquals(weeks[0], VideoTimeline.weekForEntry(weeks, -1))
    }

    @Test
    fun undatedVideosShareOneWeekThatNeverGetsADot() {
        val undated = (1..30L).map { video(it) }
        // Entries: Mar header 0, rows 1-25, unknown header 26, rows 27-56.
        val weeks = VideoTimeline.weeks(utcLayout(clips("2025-03-12T12:00:00Z", 25, 100) + undated), utc, Locale.US)
        assertEquals(listOf(VideoWeek(LocalDate.of(2025, 3, 9), 0, 25), VideoWeek(null, 26, 30)), weeks)
        assertEquals(listOf(weeks[0]), VideoTimeline.weekStops(weeks))
        assertEquals(weeks[1], VideoTimeline.weekForEntry(weeks, 26))
        assertNull(VideoTimeline.weekLabel(weeks[1], Locale.US))
    }

    @Test
    fun anEmptyListHasNoWeeksOrDots() {
        val weeks = VideoTimeline.weeks(utcLayout(emptyList()), utc, Locale.US)
        assertTrue(weeks.isEmpty())
        assertTrue(VideoTimeline.weekStops(weeks).isEmpty())
        assertTrue(VideoTimeline.dotFractions(emptyList(), 0).isEmpty())
        assertNull(VideoTimeline.weekForEntry(weeks, 0))
        assertEquals(0, VideoTimeline.dragTargetIndex(MagneticSnap(0.5f, lockedIndex = 0), emptyList(), 0))
    }

    @Test
    fun weekLabelIsTheWeeksFirstDay() {
        // Wednesday 7 September 2022; in the US its week starts on Sunday the 4th.
        val layout = utcLayout(listOf(video(1, takenMs = ms("2022-09-07T12:00:00Z"))))
        val week = VideoTimeline.weeks(layout, utc, Locale.US).single()
        assertEquals("Sep 4", VideoTimeline.weekLabel(week, Locale.US))
    }

    @Test
    fun aDragHoldsTheListAtTheLockedWeekElseFollowsTheThumb() {
        val layout = threeWeeks()
        val stops = VideoTimeline.weekStops(VideoTimeline.weeks(layout, utc, Locale.US))
        val count = layout.entries.size
        assertEquals(50, VideoTimeline.dragTargetIndex(MagneticSnap(0.64f, lockedIndex = 1), stops, count))
        assertEquals(30, VideoTimeline.dragTargetIndex(MagneticSnap(0.41f), stops, count))
        // A lock on a dot that no longer exists falls back to the thumb.
        assertEquals(30, VideoTimeline.dragTargetIndex(MagneticSnap(0.41f, lockedIndex = 5), stops, count))
    }
}
