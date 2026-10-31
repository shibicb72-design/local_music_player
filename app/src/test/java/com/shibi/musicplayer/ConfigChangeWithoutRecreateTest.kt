package com.shibi.musicplayer

import android.content.pm.ActivityInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.ui.FullscreenVideoTag
import com.shibi.musicplayer.ui.InlineVideoTag
import com.shibi.musicplayer.ui.VideoPaneTag
import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `MainActivity` handles orientation and size changes itself (V6 `configChanges`), so a
 * rotation or a fold re-lays out Compose in the same activity: no recreate, no new surface
 * from scratch, no black flash. [TwoPaneNavigationTest] still covers the recreate path.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = CompactPortrait)
class ConfigChangeWithoutRecreateTest {

    private val compose = createEmptyComposeRule()
    private val engines = mutableListOf<FakeVideoEngine>()
    private var controller: ActivityController<MainActivity>? = null

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(MediaSetup(engines)).around(compose)

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
    }

    /** Folded phone, Videos playing in the box, turned sideways: fullscreen, same activity. */
    @Test
    fun rotatingTheFoldedPhoneGoesFullscreenWithoutRecreating() {
        val activity = launch()
        playVideo("Beach day")
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Time(42_000L))
        assertTagShown(InlineVideoTag)

        change(CompactLandscape)
        assertSame("the rotation recreated the activity", activity, controller!!.get())
        assertTagShown(FullscreenVideoTag)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.requestedOrientation)
        assertNotNull("fullscreen attached the picture", engine.attachedView)
        assertStillPlaying(engine)

        // Back: the bars and the orientation return; held sideways, the wide layout shows the pane.
        activity.onBackPressedDispatcher.onBackPressed()
        settle()
        assertTagGone(FullscreenVideoTag)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
        assertTagShown(VideoPaneTag)

        // Upright again: the box, and still no second fullscreen jump.
        change(CompactPortrait)
        assertSame(activity, controller!!.get())
        assertTagShown(InlineVideoTag)
        assertTagGone(FullscreenVideoTag)
        assertStillPlaying(engine)
    }

    /** Unfolding with configChanges keeps the activity and moves the video into the pane. */
    @Test
    fun unfoldingMidVideoMovesItToThePaneWithoutRecreating() {
        val activity = launch()
        playVideo("Beach day")
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Time(42_000L))
        val boxView = engine.attachedView

        change(Expanded)
        assertSame("the unfold recreated the activity", activity, controller!!.get())
        assertTagShown(VideoPaneTag)
        assertTagGone(InlineVideoTag)
        assertTagGone(FullscreenVideoTag)
        assertTrue("the pane re-attached a new view", engine.attachedView != null && engine.attachedView !== boxView)
        assertStillPlaying(engine)

        change(CompactPortrait)
        assertSame(activity, controller!!.get())
        assertTagShown(InlineVideoTag)
        assertStillPlaying(engine)
        assertEquals("one engine for the whole session", 1, engines.size)
    }

    /**
     * A phone held sideways is the wide layout: the end leaves fullscreen for the pane, which
     * keeps the video with "Play again"; upright again, the box keeps it too.
     */
    @Test
    fun aVideoEndingSidewaysStaysInThePaneAndThenInTheBox() {
        val activity = launch()
        playVideo("Beach day")
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        change(CompactLandscape)
        assertTagShown(FullscreenVideoTag)

        engine.emit(VideoEngineEvent.Ended)
        settle()
        assertTagGone(FullscreenVideoTag)
        assertTagShown(VideoPaneTag)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
        assertPlayAgainShown()

        change(CompactPortrait)
        assertSame(activity, controller!!.get())
        assertTagShown(InlineVideoTag)
        assertTagGone(FullscreenVideoTag)
        assertPlayAgainShown()
        assertEndedAndKept(engine)
    }

    /** Only the end itself leaves fullscreen: unfolding afterwards keeps it, as for a playing video. */
    @Test
    fun unfoldingWithAnEndedVideoInFullscreenKeepsFullscreen() {
        val activity = launch()
        playVideo("Beach day")
        compose.onNode(hasContentDescription("Fullscreen")).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Ended)
        settle()
        assertTagShown(FullscreenVideoTag)

        change(Expanded)
        assertSame(activity, controller!!.get())
        assertTagShown(FullscreenVideoTag)
        assertPlayAgainShown()
        assertEndedAndKept(engine)
    }

    private fun assertPlayAgainShown() {
        val found = compose.onAllNodes(hasContentDescription("Play again")).fetchSemanticsNodes().isNotEmpty()
        assertTrue("\"Play again\" is not on screen", found)
    }

    private fun assertEndedAndKept(engine: FakeVideoEngine) {
        val state = ViewModelProvider(controller!!.get())[VideoViewModel::class.java].uiState.value
        assertEquals("Beach day", state.video?.title)
        assertEquals(VideoStatus.Ended, state.status)
        assertFalse("the end released the video", engine.released)
        assertEquals(1, engine.calls.count { it.startsWith("load:") })
    }

    private fun launch(): MainActivity {
        val created = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller = created
        compose.mainClock.autoAdvance = false
        settle()
        return created.get()
    }

    private fun change(qualifiers: String) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        controller!!.configurationChange()
        settle()
    }

    private fun assertStillPlaying(engine: FakeVideoEngine) {
        val state = ViewModelProvider(controller!!.get())[VideoViewModel::class.java].uiState.value
        assertEquals("Beach day", state.video?.title)
        assertEquals(VideoStatus.Playing, state.status)
        assertEquals(42_000L, state.positionMs)
        assertFalse("the change paused or released the video", "pause" in engine.calls || engine.released)
        assertEquals(1, engine.calls.count { it.startsWith("load:") })
    }

    private fun playVideo(title: String) {
        compose.onNode(hasContentDescription("Videos").and(IsTab)).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        val found = runCatching {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        assertTrue("\"$title\" is not on screen", found)
        compose.onAllNodesWithText(title)[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    private fun assertTagShown(tag: String) {
        val found = runCatching {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        assertTrue("'$tag' is not on screen", found)
    }

    private fun assertTagGone(tag: String) {
        val found = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        assertFalse("'$tag' is still on screen", found)
    }
}

/** The folded cover screen turned sideways: wide, but still a phone (smallest width 411dp). */
private const val CompactLandscape = "w891dp-h411dp-land-xxhdpi"
/** The inner screen at the cover screen's density: unfolding changes size, not density. */
private const val Expanded = "w900dp-h600dp-land-xxhdpi"
