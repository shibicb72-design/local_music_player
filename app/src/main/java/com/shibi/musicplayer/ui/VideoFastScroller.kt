package com.shibi.musicplayer.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.MIN_VIDEOS_PER_WEEK_DOT
import com.shibi.musicplayer.domain.ScrollSection
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.VideoTimeline
import com.shibi.musicplayer.domain.VideoTimelineLayout
import com.shibi.musicplayer.domain.section
import java.time.ZoneId
import java.util.Locale

/** Test tag of the thumb's touch area. */
internal const val VideoFastScrollerTag = "video_fast_scroller"

/**
 * Fast scroll for the Videos list, by its [sort]. By date, each week with at least
 * [MIN_VIDEOS_PER_WEEK_DOT] videos gets a dot, the bubble names the week and year ticks mark the
 * track; [zone] must be the one [layout] was built with. By name, size or length every header
 * (a letter, a range) is a stop, and the bubble names it. [initialDragFraction] (tests only)
 * starts mid-drag so screenshots are deterministic.
 */
@Composable
internal fun VideoFastScroller(
    listState: LazyListState,
    layout: VideoTimelineLayout,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    initialDragFraction: Float? = null,
    sort: SortSpec = LibrarySorts.VideoDefault,
) {
    val entryCount = layout.entries.size
    val description = stringResource(fastScrollDescriptionRes(sort.field))
    if (sort.field == SortField.Date) {
        val locale = Locale.getDefault()
        val weekOf = stringResource(R.string.video_week_of)
        val unknown = stringResource(R.string.video_date_unknown)
        val weeks = remember(layout, zone, locale) { VideoTimeline.weeks(layout, zone, locale) }
        val sections = remember(weeks) { weeks.map { it.section() } }
        val stops = remember(weeks) { VideoTimeline.weekStops(weeks).map { it.section() } }
        val ticks = remember(layout) {
            VideoTimeline.yearTicks(layout).map { (year, fraction) -> FastScrollTick(year.toString(), fraction) }
        }
        FastScroller(
            listState = listState,
            entryCount = entryCount,
            sections = sections,
            stops = stops,
            label = { index ->
                index?.let(weeks::getOrNull)?.let { VideoTimeline.weekLabel(it, locale) }
                    ?.let { weekOf.format(locale, it) } ?: unknown
            },
            description = description,
            modifier = modifier,
            ticks = ticks,
            resetKey = sort,
            initialDragFraction = initialDragFraction,
            // Every busy week keeps its dot, as before the sort menu.
            thin = false,
        )
    } else {
        val names = rememberSortGroupNames(music = false)
        val sections = remember(layout) { layout.sections.map { ScrollSection(it.startIndex, it.count) } }
        // The unknown group (no size, no length) is named but gets no dot, like undated weeks.
        val stops = remember(layout) {
            layout.sections.filterNot { it.group.isUnknown }.map { ScrollSection(it.startIndex, it.count) }
        }
        FastScroller(
            listState = listState,
            entryCount = entryCount,
            sections = sections,
            stops = stops,
            label = { index ->
                val group = index?.let(layout.sections::getOrNull)?.group
                if (group != null) names.name(group, sort.field) else ""
            },
            description = description,
            modifier = modifier,
            resetKey = sort,
            initialDragFraction = initialDragFraction,
        )
    }
}
