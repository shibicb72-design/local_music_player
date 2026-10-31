package com.shibi.musicplayer

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.FAST_SCROLL_HIDE_DELAY_MS
import com.shibi.musicplayer.domain.VideoTimeline
import com.shibi.musicplayer.ui.VideoFastScroller
import com.shibi.musicplayer.ui.VideoFastScrollerTag
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class VideoFastScrollerTest {

    @get:Rule
    val compose = createComposeRule()

    private val layout = VideoTimeline.layout(VideoTimelineScreenshotTest.videos, ZoneId.systemDefault())
    private lateinit var listState: LazyListState
    private val ListTag = "video_list_under_test"

    private fun setUp() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MusicPlayerTheme {
                listState = rememberLazyListState()
                // Two rows tall, so the last month header can be the first visible item.
                // The outer box takes the tight window constraints, so the list box sits inside it.
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.TopStart).height(112.dp).width(300.dp)) {
                        LazyColumn(Modifier.fillMaxSize().testTag(ListTag), state = listState) {
                            items(layout.entries.size) { index ->
                                Text("Entry $index", Modifier.height(56.dp).width(200.dp))
                            }
                        }
                        VideoFastScroller(
                            listState = listState,
                            layout = layout,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
    }

    /** Swipes the list and lets the fling finish, so the hide timer starts from a known point. */
    private fun scrollAndSettle() {
        compose.onNodeWithTag(ListTag).performTouchInput { swipeUp(durationMillis = 100) }
        var guard = 0
        while (guard++ < 400) {
            compose.mainClock.advanceTimeBy(50)
            if (!listState.isScrollInProgress) break
        }
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
    fun draggingToTheBottomScrollsToTheLastMonthHeader() {
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
        assertEquals(layout.sections.last().startIndex, listState.firstVisibleItemIndex)
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
