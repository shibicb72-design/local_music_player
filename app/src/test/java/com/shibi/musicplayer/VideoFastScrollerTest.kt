package com.shibi.musicplayer

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.FAST_SCROLL_HIDE_DELAY_MS
import com.shibi.musicplayer.domain.LibrarySorter
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.ScrollSection
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoTimeline
import com.shibi.musicplayer.domain.VideoTimelineLayout
import com.shibi.musicplayer.domain.section
import com.shibi.musicplayer.domain.stopFractions
import com.shibi.musicplayer.domain.thinStops
import com.shibi.musicplayer.domain.thumbFraction
import com.shibi.musicplayer.ui.VideoFastScroller
import com.shibi.musicplayer.ui.VideoFastScrollerTag
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import java.text.Collator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class VideoFastScrollerTest {

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val layout = VideoTimeline.layout(VideoTimelineScreenshotTest.videos, zone)
    private lateinit var listState: LazyListState
    private lateinit var view: View
    private val ListTag = "video_list_under_test"

    /** Rows are 56 dp; the default list is two rows tall. */
    private fun setUp(
        layout: VideoTimelineLayout = this.layout,
        listHeight: Dp = 112.dp,
        sort: SortSpec = LibrarySorts.VideoDefault,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MusicPlayerTheme {
                listState = rememberLazyListState()
                view = LocalView.current
                // The outer box takes the tight window constraints, so the list box sits inside it.
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.TopStart).height(listHeight).width(300.dp)) {
                        LazyColumn(Modifier.fillMaxSize().testTag(ListTag), state = listState) {
                            items(layout.entries.size) { index ->
                                Text("Entry $index", Modifier.height(56.dp).width(200.dp))
                            }
                        }
                        VideoFastScroller(
                            listState = listState,
                            layout = layout,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            zone = zone,
                            sort = sort,
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
    }

    /** Swipes the list and lets the fling finish, so the hide timer starts from a known point. */
    private fun scrollAndSettle(up: Boolean = true) {
        compose.onNodeWithTag(ListTag).performTouchInput {
            if (up) swipeUp(durationMillis = 100) else swipeDown(durationMillis = 50)
        }
        var guard = 0
        while (guard++ < 400) {
            compose.mainClock.advanceTimeBy(50)
            if (!listState.isScrollInProgress) break
        }
    }

    /** [count] clips taken at noon local time on [day] March 2025, ids from [firstId] up. */
    private fun clips(day: Int, count: Int, firstId: Long): List<Video> {
        val at = LocalDateTime.of(2025, 3, day, 12, 0).atZone(zone).toInstant().toEpochMilli()
        return (0 until count).map {
            VideosScreenshotTest.video(firstId + it, "Clip $day-$it", 60_000L, 1_000_000L, 1920, 1080, "Camera")
                .copy(dateTakenMs = at)
        }
    }

    /** [count] clips taken at noon local time on [date], ids from [firstId] up. */
    private fun clipsOn(date: LocalDate, count: Int, firstId: Long): List<Video> {
        val at = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        return (0 until count).map {
            VideosScreenshotTest.video(firstId + it, "Clip $date-$it", 60_000L, 1_000_000L, 1920, 1080, "Camera")
                .copy(dateTakenMs = at)
        }
    }

    /** Drags the thumb from where the list is to [fraction] of a [travelPx] track, and holds it there. */
    private fun dragThumbTo(fraction: Float, travelPx: Float, entryCount: Int) {
        val start = thumbFraction(listState.firstVisibleItemIndex, listState.layoutInfo.visibleItemsInfo.size, entryCount)
        compose.onNodeWithTag(VideoFastScrollerTag).performTouchInput {
            down(center)
            moveBy(Offset(0f, viewConfiguration.touchSlop))
            moveBy(Offset(0f, (fraction - start) * travelPx))
        }
        compose.mainClock.advanceTimeBy(100)
    }

    @Test
    fun hiddenAtRestThenShownAfterScroll() {
        setUp()
        compose.onNodeWithTag(VideoFastScrollerTag).assertDoesNotExist()
        compose.onNodeWithTag(ListTag).performTouchInput { swipeUp(durationMillis = 100) }
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithTag(VideoFastScrollerTag).assertIsDisplayed()
    }

    @Test
    fun staysUntilTheHideDelayThenGoes() {
        setUp()
        scrollAndSettle()
        compose.onNodeWithTag(VideoFastScrollerTag).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(FAST_SCROLL_HIDE_DELAY_MS - 1)
        compose.onNodeWithTag(VideoFastScrollerTag).assertIsDisplayed()
        // The delay, then the 200 ms fade.
        compose.mainClock.advanceTimeBy(1 + 400)
        compose.onNodeWithTag(VideoFastScrollerTag).assertDoesNotExist()
    }

    @Test
    fun draggingToTheBottomReachesTheEndOfTheList() {
        setUp()
        scrollAndSettle()
        // Up to the top first, so the drag down has somewhere to go.
        compose.onNodeWithTag(VideoFastScrollerTag).performTouchInput {
            swipe(center, Offset(center.x, center.y - 600f), durationMillis = 200)
        }
        compose.mainClock.advanceTimeBy(500)
        assertEquals(0, listState.firstVisibleItemIndex)
        compose.onNodeWithTag(VideoFastScrollerTag).performTouchInput {
            swipe(center, Offset(center.x, center.y + 600f), durationMillis = 200)
        }
        compose.mainClock.advanceTimeBy(500)
        assertFalse(listState.canScrollForward)
    }

    @Test
    fun draggingOntoABusyWeeksDotHoldsTheListThereNamesTheWeekAndTicks() {
        // Wednesdays of three weeks in a row, the same weeks whichever day the locale starts on.
        // Entries: header 0, 10 rows, the 30-clip week from entry 11, 10 rows.
        val busy = VideoTimeline.layout(
            VideoTimeline.sortNewestFirst(clips(19, 10, 1_000) + clips(12, 30, 2_000) + clips(5, 10, 3_000)),
            zone,
        )
        val stop = VideoTimeline.weekStops(VideoTimeline.weeks(busy, zone, Locale.getDefault())).single()
        assertEquals(11, stop.startIndex)
        setUp(busy, listHeight = 400.dp)
        // List flings only, so no thumb drag has ticked yet.
        scrollAndSettle()
        scrollAndSettle(up = false)
        assertEquals(0, listState.firstVisibleItemIndex)
        assertNotEquals(HapticFeedbackConstants.SEGMENT_TICK, shadowOf(view).lastHapticFeedbackPerformed())

        // The dot sits at its week's share of the track: the list height less the 56 dp thumb.
        val dotY = with(compose.density) { (400.dp - 56.dp).toPx() } * stop.startIndex / (busy.entries.size - 1)
        compose.onNodeWithTag(VideoFastScrollerTag).performTouchInput {
            down(center)
            moveBy(Offset(0f, viewConfiguration.touchSlop))
            moveBy(Offset(0f, dotY))
        }
        compose.mainClock.advanceTimeBy(100)
        assertEquals(stop.startIndex, listState.firstVisibleItemIndex)
        compose.onNodeWithText("Week of " + VideoTimeline.weekLabel(stop, Locale.getDefault())).assertIsDisplayed()
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK, shadowOf(view).lastHapticFeedbackPerformed())
        // The list remains latched, but the halo must still follow small finger movements.
        val before = compose.onNodeWithTag(VideoFastScrollerTag).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag(VideoFastScrollerTag).performTouchInput {
            moveBy(Offset(0f, with(compose.density) { 4.dp.toPx() }))
        }
        compose.mainClock.advanceTimeByFrame()
        val after = compose.onNodeWithTag(VideoFastScrollerTag).fetchSemanticsNode().boundsInRoot.top
        assertTrue("Halo moves continuously while the list is latched", after > before)
        assertEquals(stop.startIndex, listState.firstVisibleItemIndex)

        compose.onNodeWithTag(VideoFastScrollerTag).performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Week of " + VideoTimeline.weekLabel(stop, Locale.getDefault())).assertExists()
        val released = compose.onNodeWithTag(VideoFastScrollerTag).fetchSemanticsNode().boundsInRoot.top
        assertEquals("Release fade holds the halo at the finger", after, released, 1f)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Week of " + VideoTimeline.weekLabel(stop, Locale.getDefault())).assertDoesNotExist()
    }

    @Test
    fun underTheDateSortBusyWeeksCloseTogetherKeepEveryDot() {
        // Wednesdays a week apart, newest first: six quiet weeks, a busy week of 25 and a busier
        // one of 40 right after it, then six quiet weeks. Thinning would keep only the 40.
        val newest = LocalDate.of(2025, 6, 25)
        val counts = List(6) { 20 } + listOf(25, 40) + List(6) { 20 }
        var id = 1L
        val videos = counts.flatMapIndexed { week, count ->
            clipsOn(newest.minusWeeks(week.toLong()), count, id).also { id += count }
        }
        val busy = VideoTimeline.layout(VideoTimeline.sortNewestFirst(videos), zone)
        val weeks = VideoTimeline.weekStops(VideoTimeline.weeks(busy, zone, Locale.getDefault()))
        val stops = weeks.map { it.section() }
        assertEquals(listOf(25, 40), stops.map { it.size })
        val listHeight = 200.dp
        val travelPx = with(compose.density) { (listHeight - 56.dp).toPx() }
        val snapPx = with(compose.density) { 16.dp.toPx() }
        // The two dots are inside one snap radius, so thinning would drop the smaller week's.
        assertEquals(listOf(stops[1]), thinStops(stops, busy.entries.size, travelPx, snapPx))

        setUp(busy, listHeight)
        scrollAndSettle()
        scrollAndSettle(up = false)
        dragThumbTo(stopFractions(stops, busy.entries.size)[0], travelPx, busy.entries.size)

        // As on Videos before the sort menu: both weeks keep a dot, so the smaller one holds the list.
        assertEquals(stops[0].startIndex, listState.firstVisibleItemIndex)
        compose.onNodeWithText("Week of " + VideoTimeline.weekLabel(weeks[0], Locale.getDefault())).assertIsDisplayed()
    }

    @Test
    fun underANameSortCrowdedLetterDotsAreStillThinned() {
        // 150 titles under A, one under B and 150 under C: B's dot sits a few pixels from C's.
        val titles = List(150) { "Alpha $it" } + "Bravo" + List(150) { "Charlie $it" }
        val videos = titles.mapIndexed { index, title ->
            VideosScreenshotTest.video(index + 1L, title, 60_000L, 1_000_000L, 1920, 1080, "Camera")
        }
        val byName = SortSpec(SortField.Name, ascending = true)
        val named = VideoTimeline.layout(LibrarySorter.videos(videos, byName, Collator.getInstance(), zone, "Unknown title"))
        val stops = named.sections.map { ScrollSection(it.startIndex, it.count) }
        assertEquals(listOf(150, 1, 150), stops.map { it.size })
        val listHeight = 200.dp
        val travelPx = with(compose.density) { (listHeight - 56.dp).toPx() }
        val snapPx = with(compose.density) { 16.dp.toPx() }
        assertEquals(listOf(stops[0], stops[2]), thinStops(stops, named.entries.size, travelPx, snapPx))

        setUp(named, listHeight, sort = byName)
        scrollAndSettle()
        scrollAndSettle(up = false)
        dragThumbTo(stopFractions(stops, named.entries.size)[1], travelPx, named.entries.size)

        // B has no dot of its own, so the thumb takes C's beside it.
        assertEquals(stops[2].startIndex, listState.firstVisibleItemIndex)
    }

    @Test
    fun thumbTouchTargetIsAtLeast48By56() {
        setUp()
        scrollAndSettle()
        compose.onNodeWithTag(VideoFastScrollerTag)
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(56.dp)
    }
}
