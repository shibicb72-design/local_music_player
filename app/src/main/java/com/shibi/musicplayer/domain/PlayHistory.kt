package com.shibi.musicplayer.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class HistoryType {
    Song,
    Video,
}

/** Records one entry per playthrough: once the item has played 5 s, or reached its end if shorter. */
class PlayHistoryTracker {
    companion object {
        const val ThresholdMs = 5_000L
        const val EndSlackMs = 1_000L
    }

    private var currentKey: String? = null
    private var recorded = false

    /** Feed every position update. Returns the key to record now, or null. [key] identifies the item (e.g. "song:42"). */
    fun feed(key: String?, positionMs: Long, durationMs: Long): String? {
        if (key != currentKey) {
            currentKey = key
            recorded = false
        }
        if (key == null) return null
        if (recorded) {
            if (positionMs < ReplayResetMs) recorded = false
            return null
        }
        val reachedThreshold = positionMs >= ThresholdMs
        val reachedEnd = durationMs > 0L && positionMs >= durationMs - EndSlackMs
        if (!reachedThreshold && !reachedEnd) return null
        recorded = true
        return key
    }
}

private const val ReplayResetMs = 1_000L

/** A consecutive replay of the same item updates the last entry's time instead of adding a row. */
object PlayHistoryDedupe {
    const val Limit = 500

    fun isReplay(lastType: HistoryType?, lastMediaId: Long?, type: HistoryType, mediaId: Long): Boolean =
        lastType == type && lastMediaId == mediaId
}

sealed interface DayLabel {
    data object Today : DayLabel
    data object Yesterday : DayLabel
    data class Weekday(val day: DayOfWeek) : DayLabel
    data class Date(val month: Month, val day: Int, val year: Int?) : DayLabel
}

object HistoryDays {
    /** Today, Yesterday, the weekday for the 5 days before that (within the last 7 days), then a date; the year only when it is not the current year. Calendar days in [zone], so midnight is the boundary. */
    fun labelFor(playedAtMs: Long, nowMs: Long, zone: ZoneId): DayLabel {
        val today = localDate(nowMs, zone)
        val played = localDate(playedAtMs, zone)
        val days = ChronoUnit.DAYS.between(played, today)
        return when {
            days <= 0L -> DayLabel.Today
            days == 1L -> DayLabel.Yesterday
            days <= 6L -> DayLabel.Weekday(played.dayOfWeek)
            else -> DayLabel.Date(
                month = played.month,
                day = played.dayOfMonth,
                year = played.year.takeIf { it != today.year },
            )
        }
    }

    /** Groups items (already newest first) into consecutive runs with the same label, keeping order. */
    fun <T> group(
        items: List<T>,
        playedAt: (T) -> Long,
        nowMs: Long,
        zone: ZoneId,
    ): List<Pair<DayLabel, List<T>>> {
        val runs = mutableListOf<Pair<DayLabel, MutableList<T>>>()
        for (item in items) {
            val label = labelFor(playedAt(item), nowMs, zone)
            val last = runs.lastOrNull()
            if (last != null && last.first == label) {
                last.second.add(item)
            } else {
                runs.add(label to mutableListOf(item))
            }
        }
        return runs.map { (label, run) -> label to run.toList() }
    }

    private fun localDate(ms: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}
