package com.shibi.musicplayer

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.ui.FullscreenVideoTag
import com.shibi.musicplayer.ui.InlineVideoTag
import com.shibi.musicplayer.video.VideoEngine
import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoSessionControls
import com.shibi.musicplayer.video.VideoSessionLink
import com.shibi.musicplayer.video.VideoSessionSnapshot
import com.shibi.musicplayer.video.VideoSessionStatus
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * V8 in the real activity: locking the phone during a video keeps the sound and drops the
 * picture; unlocking puts the picture back on the same box, where the sound is; leaving the
 * app pauses; the video notification's tap opens Videos. The session service is replaced by
 * a recording link (Robolectric cannot bind it for real).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = CompactPortrait)
class VideoLockScreenTest {

    private val compose = createEmptyComposeRule()
    private val engines = mutableListOf<FakeVideoEngine>()
    private val link = RecordingLink()
    private var controller: ActivityController<MainActivity>? = null

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(MediaSetup(engines)).around(compose)

    @Before
    fun setUp() {
        VideoViewModel.testSessionLink = { link }
    }

    @After
    fun tearDown() {
        screenOn(true)
        controller?.pause()?.stop()?.destroy()
        VideoViewModel.testSessionLink = null
    }

    @Test
    fun lockingKeepsTheSoundAndUnlockingResyncsThePicture() {
        val activity = launch()
        val engine = playBeachDay()
        val boxView = engine.attachedView
        assertNotNull(boxView)
        assertEquals(VideoSessionStatus.Playing, link.shown.last().status)

        screenOn(false)
        controller!!.pause().stop()
        assertFalse("the lock paused the video", "pause" in engine.calls)
        assertFalse(engine.pictureEnabled)
        assertEquals(VideoStatus.Playing, videoViewModel().uiState.value.status)
        assertEquals("the session ended on lock", 0, link.ends)

        // The sound plays on for a while.
        engine.emit(VideoEngineEvent.Time(642_000L))
        engine.calls.clear()
        screenOn(true)
        controller!!.restart().resume()
        settle()
        assertSame("the unlock recreated the activity", activity, controller!!.get())
        assertTrue(engine.pictureEnabled)
        assertSame("the picture went back on the box", boxView, engine.attachedView)
        assertTrue("no resync seek to where the sound is", "seek:642000" in engine.calls)
        assertTagShown(InlineVideoTag)
        assertFalse("pause" in engine.calls)
    }

    @Test
    fun leavingTheAppPausesTheVideo() {
        launch()
        val engine = playBeachDay()
        controller!!.pause().stop()
        assertTrue("pause" in engine.calls)
        assertFalse(engine.pictureEnabled)
        controller!!.restart().resume()
        settle()
        assertTrue(engine.pictureEnabled)
        assertNotNull(engine.attachedView)
    }

    @Test
    fun aRecreateIsNotLeavingTheApp() {
        launch()
        val engine = playBeachDay()
        // A density change on unfold still recreates; the stop in between must not count.
        controller!!.recreate()
        settle()
        assertFalse("the recreate paused the video", "pause" in engine.calls)
        assertFalse("the recreate dropped the picture", "picture:false" in engine.calls)
        assertEquals(VideoStatus.Playing, videoViewModel().uiState.value.status)
    }

    @Test
    fun theVideoNotificationOpensVideos() {
        val activity = launch()
        playBeachDay()
        // Folded, browsing Songs pauses the box but keeps the video loaded.
        compose.onNode(hasContentDescription("Songs").and(IsTab)).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertTagGone(InlineVideoTag)
        assertTrue(videoViewModel().uiState.value.isActive)

        controller!!.newIntent(Intent(activity, MainActivity::class.java).setAction(MainActivity.ActionOpenVideo))
        settle()
        assertTagShown(InlineVideoTag)
        assertTagGone(FullscreenVideoTag)
    }

    private fun launch(): MainActivity {
        val created = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller = created
        compose.mainClock.autoAdvance = false
        settle()
        return created.get()
    }

    private fun playBeachDay(): FakeVideoEngine {
        compose.onNode(hasContentDescription("Videos").and(IsTab)).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        val found = runCatching {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithText("Beach day").fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        assertTrue("\"Beach day\" is not on screen", found)
        compose.onAllNodesWithText("Beach day")[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Time(42_000L))
        settle()
        return engine
    }

    private fun screenOn(on: Boolean) {
        val power = ApplicationProvider.getApplicationContext<Application>().getSystemService(PowerManager::class.java)
        shadowOf(power).turnScreenOn(on)
    }

    private fun videoViewModel(): VideoViewModel =
        ViewModelProvider(controller!!.get())[VideoViewModel::class.java]

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
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

    private class RecordingLink : VideoSessionLink {
        val shown = mutableListOf<VideoSessionSnapshot>()
        var ends = 0
        private var up = false

        override fun show(snapshot: VideoSessionSnapshot, controls: VideoSessionControls, engine: VideoEngine) {
            shown += snapshot
            up = true
        }

        override fun end() {
            if (up) ends++
            up = false
        }
    }
}
