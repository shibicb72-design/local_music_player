package com.shibi.musicplayer

import com.shibi.musicplayer.domain.DayLabel
import com.shibi.musicplayer.domain.HistoryDays
import com.shibi.musicplayer.domain.HistoryType
import com.shibi.musicplayer.domain.PlayHistoryDedupe
import com.shibi.musicplayer.domain.PlayHistoryTracker
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayHistoryTest {

    @Test
    fun trackerRecordsOnceAtFiveSeconds() {
        val tracker = PlayHistoryTracker()
        assertNull(tracker.feed("song:1", 4_999L, 10_000L))
        assertEquals("song:1", tracker.feed("song:1", 5_000L, 10_000L))
        assertNull(tracker.feed("song:1", 8_000L, 10_000L))
    }

    @Test
    fun shortItemRecordsAtTheEnd() {
        val tracker = PlayHistoryTracker()
        assertNull(tracker.feed("song:2", 1_999L, 3_000L))
        assertEquals("song:2", tracker.feed("song:2", 3_000L, 3_000L))
    }

    @Test
    fun aNewKeyResetsTheTracker() {
        val tracker = PlayHistoryTracker()
        assertEquals("song:1", tracker.feed("song:1", 5_000L, 10_000L))
        assertEquals("song:2", tracker.feed("song:2", 5_000L, 10_000L))
    }

    @Test
    fun aReplayAfterGoingBackUnderOneSecondRecordsAgain() {
        val tracker = PlayHistoryTracker()
        assertEquals("song:1", tracker.feed("song:1", 5_000L, 10_000L))
        assertNull(tracker.feed("song:1", 500L, 10_000L))
        assertEquals("song:1", tracker.feed("song:1", 5_000L, 10_000L))
    }

    @Test
    fun aNullKeyNeverRecords() {
        val tracker = PlayHistoryTracker()
        assertNull(tracker.feed(null, 5_000L, 10_000L))
        assertNull(tracker.feed(null, 60_000L, 10_000L))
    }

    @Test
    fun dedupeIsTrueForTheSameItem() {
        assertTrue(PlayHistoryDedupe.isReplay(HistoryType.Song, 42L, HistoryType.Song, 42L))
    }

    @Test
    fun dedupeIsFalseForADifferentItem() {
        assertFalse(PlayHistoryDedupe.isReplay(HistoryType.Song, 42L, HistoryType.Video, 42L))
        assertFalse(PlayHistoryDedupe.isReplay(HistoryType.Song, 42L, HistoryType.Song, 43L))
        assertFalse(PlayHistoryDedupe.isReplay(null, null, HistoryType.Song, 42L))
    }

    @Test
    fun midnightIsTheBoundaryInNewYork() {
        val zone = ZoneId.of("America/New_York")
        val now = localMs(zone, 2026, 10, 18, 0, 1)
        assertEquals(DayLabel.Yesterday, HistoryDays.labelFor(localMs(zone, 2026, 10, 17, 23, 59), now, zone))
        assertEquals(DayLabel.Today, HistoryDays.labelFor(now, now, zone))
    }

    @Test
    fun theSameInstantFallsOnDifferentDaysInTokyoAndLosAngeles() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val losAngeles = ZoneId.of("America/Los_Angeles")
        val now = Instant.parse("2026-06-15T16:00:00Z").toEpochMilli()
        val played = Instant.parse("2026-06-15T10:00:00Z").toEpochMilli()
        assertEquals(DayLabel.Yesterday, HistoryDays.labelFor(played, now, tokyo))
        assertEquals(DayLabel.Today, HistoryDays.labelFor(played, now, losAngeles))
    }

    @Test
    fun threeDaysAgoIsAWeekday() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()
        val played = Instant.parse("2026-06-12T12:00:00Z").toEpochMilli()
        assertEquals(DayLabel.Weekday(LocalDate.of(2026, 6, 12).dayOfWeek), HistoryDays.labelFor(played, now, zone))
    }

    @Test
    fun sevenDaysAgoIsADateWithoutAYear() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()
        val played = Instant.parse("2026-06-08T12:00:00Z").toEpochMilli()
        assertEquals(DayLabel.Date(Month.JUNE, 8, null), HistoryDays.labelFor(played, now, zone))
    }

    @Test
    fun aDateLastYearKeepsItsYear() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-01-05T12:00:00Z").toEpochMilli()
        val played = Instant.parse("2025-06-01T12:00:00Z").toEpochMilli()
        assertEquals(DayLabel.Date(Month.JUNE, 1, 2025), HistoryDays.labelFor(played, now, zone))
    }

    @Test
    fun aFutureTimeCountsAsToday() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()
        val played = Instant.parse("2026-06-15T13:00:00Z").toEpochMilli()
        assertEquals(DayLabel.Today, HistoryDays.labelFor(played, now, zone))
    }

    @Test
    fun groupKeepsOrderAndMakesRuns() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()
        val items = listOf(
            "a" to Instant.parse("2026-06-15T10:00:00Z").toEpochMilli(),
            "b" to Instant.parse("2026-06-15T09:00:00Z").toEpochMilli(),
            "c" to Instant.parse("2026-06-14T09:00:00Z").toEpochMilli(),
            "d" to Instant.parse("2026-06-01T09:00:00Z").toEpochMilli(),
            "e" to Instant.parse("2026-06-01T08:00:00Z").toEpochMilli(),
        )
        val groups = HistoryDays.group(items = items, playedAt = { it.second }, nowMs = now, zone = zone)
        assertEquals(3, groups.size)
        assertEquals(DayLabel.Today, groups[0].first)
        assertEquals(listOf("a", "b"), groups[0].second.map { it.first })
        assertEquals(DayLabel.Yesterday, groups[1].first)
        assertEquals(listOf("c"), groups[1].second.map { it.first })
        assertEquals(DayLabel.Date(Month.JUNE, 1, null), groups[2].first)
        assertEquals(listOf("d", "e"), groups[2].second.map { it.first })
    }

    private fun localMs(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
}
