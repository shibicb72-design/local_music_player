package com.shibi.musicplayer

import android.app.Application
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.AspectMode
import com.shibi.musicplayer.ui.FullscreenVideoContent
import com.shibi.musicplayer.ui.FullscreenVideoTag
import com.shibi.musicplayer.ui.PlayerExtras
import com.shibi.musicplayer.ui.StreamVolume
import com.shibi.musicplayer.ui.VideoPlayerBox
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val BoxTag = "gesture_box"
private const val Start = 100_000L
private const val Duration = 600_000L
private val NoEdges = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)

/** What the owner saw, in order: "preview:ms" and "seek:ms". */
private class Log {
    val events = mutableListOf<String>()
    val seeks get() = events.filter { it.startsWith("seek:") }.map { it.removePrefix("seek:").toLong() }
    val previews get() = events.filter { it.startsWith("preview:") }
    var toggles = 0
}

private class FakeVolume : StreamVolume {
    override val max = 15
    var level = 5
    val sets = mutableListOf<Int>()
    override fun current() = level
    override fun set(volume: Int) {
        level = volume
        sets += volume
    }
}

private fun playing(positionMs: Long = Start) = VideoUiState(
    status = VideoStatus.Playing,
    positionMs = positionMs,
    durationMs = Duration,
)

private fun Log.extras() = PlayerExtras(onRequestPreview = { events += "preview:$it" })

@Composable
private fun Fullscreen(
    log: Log,
    volume: StreamVolume? = null,
    edges: WindowInsets = NoEdges,
    positionMs: Long = Start,
) {
    MusicPlayerTheme {
        FullscreenVideoContent(
            state = playing(positionMs),
            brightnessSwipe = false,
            onTogglePlayPause = {},
            onSeek = { log.events += "seek:$it" },
            onCycleAspect = { AspectMode.Fit },
            onExit = {},
            surface = {},
            volume = volume,
            brightness = null,
            extras = log.extras(),
            gestureEdges = edges,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun Box(log: Log, edges: WindowInsets = NoEdges, modifier: Modifier = Modifier) {
    MusicPlayerTheme {
        VideoPlayerBox(
            state = playing(),
            onTogglePlayPause = {},
            onSeek = { log.events += "seek:$it" },
            onClose = {},
            surface = {},
            extras = log.extras(),
            gestureEdges = edges,
            modifier = modifier.testTag(BoxTag),
        )
    }
}

/** The axis locks after the touch slop; the other axis is never touched. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GestureAxisLockTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aMostlyHorizontalSwipeSeeksOnReleaseOnly() {
        val log = Log()
        compose.setContent { Fullscreen(log) }
        compose.onNodeWithTag(FullscreenVideoTag).performTouchInput {
            swipe(Offset(width * 0.3f, height * 0.5f), Offset(width * 0.3f + 200f, height * 0.5f + 60f), 300)
        }
        compose.waitForIdle()
        assertEquals("one seek", 1, log.seeks.size)
        assertTrue("the seek is forward", log.seeks.single() > Start)
        assertTrue("previews were asked for", log.previews.isNotEmpty())
        assertTrue("the seek is last, on release", log.events.last().startsWith("seek:"))
    }

    @Test
    fun aMostlyVerticalSwipeOnTheRightChangesTheVolumeAndNeverSeeks() {
        val log = Log()
        val volume = FakeVolume()
        compose.setContent { Fullscreen(log, volume) }
        compose.onNodeWithTag(FullscreenVideoTag).performTouchInput {
            swipe(Offset(width * 0.8f, height * 0.7f), Offset(width * 0.8f + 20f, height * 0.3f), 300)
        }
        compose.waitForIdle()
        assertTrue("volume set", volume.sets.isNotEmpty())
        assertTrue("volume went up", volume.level > 5)
        assertTrue("no seek", log.seeks.isEmpty())
        assertTrue("no preview", log.previews.isEmpty())
    }

    @Test
    fun anInlineVerticalDragScrollsThePageAndDoesNotSeek() {
        val log = Log()
        val scroll = ScrollState(0)
        compose.setContent {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Box(log, modifier = Modifier.fillMaxWidth().height(200.dp))
                Spacer(Modifier.height(2_000.dp))
            }
        }
        compose.onNodeWithTag(BoxTag).performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue("the page scrolled: ${scroll.value}", scroll.value > 0)
        assertTrue("no seek", log.seeks.isEmpty())
    }
}

/** Double tap, chained taps, and the timeouts, with the clock under the test's control. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DoubleTapAccumulationTest {

    @get:Rule
    val compose = createComposeRule()

    private val pause: String
        get() = ApplicationProvider.getApplicationContext<Application>().getString(R.string.pause)

    private fun controlsShown() = compose.onAllNodesWithContentDescription(pause).fetchSemanticsNodes().isNotEmpty()

    /** Composes with the clock held, then lets the first frame lay the player out. */
    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent(content)
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun rightTapsAccumulateTenSecondsFromTheStartOfTheChain() {
        val log = Log()
        show { Fullscreen(log) }
        compose.onNodeWithTag(FullscreenVideoTag).performTouchInput {
            val tap = Offset(width * 0.9f, height * 0.5f)
            click(tap)
            advanceEventTime(100)
            click(tap)
            advanceEventTime(150)
            click(tap)
            advanceEventTime(150)
            click(tap)
        }
        assertEquals(listOf(Start + 10_000L, Start + 20_000L, Start + 30_000L), log.seeks)
    }

    @Test
    fun leftTapsGoBackAndClampAtZero() {
        val log = Log()
        show { Fullscreen(log, positionMs = 15_000L) }
        compose.onNodeWithTag(FullscreenVideoTag).performTouchInput {
            val tap = Offset(width * 0.1f, height * 0.5f)
            click(tap)
            advanceEventTime(100)
            click(tap)
            advanceEventTime(150)
            click(tap)
        }
        assertEquals(listOf(5_000L, 0L), log.seeks)
    }

    @Test
    fun aCentreTapTogglesTheControlsAtOnce() {
        val log = Log()
        show { Fullscreen(log) }
        compose.onNodeWithTag(FullscreenVideoTag).performTouchInput { click(center) }
        // Let the snapshot write made inside the event reach the recomposer, then a few frames:
        // well under the 300 ms double-tap timeout a side tap would wait for.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64)
        assertTrue("controls appeared within 64 ms", controlsShown())
        assertTrue(log.seeks.isEmpty())
    }

    @Test
    fun aSingleSideTapTogglesAfterTheDoubleTapTimeout() {
        val log = Log()
        var timeout = 0L
        show {
            timeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
            Fullscreen(log)
        }
        compose.onNodeWithTag(FullscreenVideoTag).performTouchInput { click(Offset(width * 0.9f, height * 0.5f)) }
        compose.mainClock.advanceTimeBy(timeout - 1)
        assertTrue("not yet", !controlsShown())
        compose.mainClock.advanceTimeBy(timeout + 1)
        compose.mainClock.advanceTimeByFrame()
        assertTrue("controls shown after the timeout", controlsShown())
        assertTrue("a single tap never seeks", log.seeks.isEmpty())
    }

    @Test
    fun anInlineVerticalScrollAfterASideTapCancelsThePendingToggle() {
        val log = Log()
        var timeout = 0L
        show {
            timeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
            Column(Modifier.fillMaxSize().verticalScroll(ScrollState(0))) {
                Box(log, modifier = Modifier.fillMaxWidth().height(200.dp))
                Spacer(Modifier.height(2_000.dp))
            }
        }
        compose.onNodeWithTag(BoxTag).performTouchInput { click(Offset(width * 0.9f, height * 0.5f)) }
        compose.onNodeWithTag(BoxTag).performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(timeout * 2)
        compose.mainClock.advanceTimeByFrame()
        assertTrue("the controls did not toggle after the timeout", !controlsShown())
    }
}

/** A pointer that goes down in the system gesture edges is ignored entirely. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EdgeExclusionTest {

    @get:Rule
    val compose = createComposeRule()

    private val edges = WindowInsets(left = 24.dp, right = 24.dp)

    private fun swipeFrom(startDp: Float): Log {
        val log = Log()
        compose.setContent { Fullscreen(log, edges = edges) }
        compose.onNodeWithTag(FullscreenVideoTag).performTouchInput {
            val x = startDp.dp.toPx()
            swipe(Offset(x, height * 0.5f), Offset(x + 150f, height * 0.5f), 300)
        }
        compose.waitForIdle()
        return log
    }

    @Test
    fun aDragFromInsideTheEdgeDoesNothing() {
        val log = swipeFrom(4f)
        assertTrue("no seek", log.seeks.isEmpty())
        assertTrue("no preview", log.previews.isEmpty())
    }

    @Test
    fun aDragFromOutsideTheEdgeSeeks() {
        val log = swipeFrom(60f)
        assertEquals(1, log.seeks.size)
    }
}
