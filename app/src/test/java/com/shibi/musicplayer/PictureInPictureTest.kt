package com.shibi.musicplayer

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
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
import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoViewModel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
 * V10 picture-in-picture in the real activity, with a fake engine: entering hides the chrome,
 * leaving it opens fullscreen again, Home (a leave hint) enters PiP only while playing and only
 * below API 31 (31+ uses auto-enter), and dismissing the window pauses the video.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = CompactPortrait)
class PictureInPictureTest {

    private val compose = createEmptyComposeRule()
    private val engines = mutableListOf<FakeVideoEngine>()
    private var controller: ActivityController<MainActivity>? = null

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(MediaSetup(engines)).around(compose)

    @After
    fun tearDown() {
        screenOn(true)
        controller?.pause()?.stop()?.destroy()
    }

    @Test
    fun pipHidesChromeAndRestoresToFullscreen() {
        val activity = launch()
        playBeachDay()
        assertTagShown(InlineVideoTag)
        assertTrue("the carousel starts on screen", tabShown("Videos"))

        onPipChanged(activity, true)
        settle()
        assertTagShown(PipLayoutTag)
        assertFalse("the carousel is hidden in picture-in-picture", tabShown("Videos"))
        assertTagGone(InlineVideoTag)

        onPipChanged(activity, false)
        settle()
        assertTagGone(PipLayoutTag)
        assertTagShown(FullscreenVideoTag)
    }

    @Test
    @Config(sdk = [30])
    fun pausedVideoDoesNotEnterPipOnLeaveHint() {
        setPipSupported()
        val activity = launch()
        val engine = playBeachDay()
        engine.emit(VideoEngineEvent.Paused)
        settle()

        controller!!.userLeaving()
        settle()

        assertFalse(activity.isInPictureInPictureMode)
    }

    @Test
    @Config(sdk = [30])
    fun playingVideoEntersPipOnLeaveHintBelow31() {
        setPipSupported()
        val activity = launch()
        playBeachDay()

        controller!!.userLeaving()
        settle()

        // ShadowActivity records enterPictureInPictureMode, so the flag is observable here.
        assertTrue(activity.isInPictureInPictureMode)
    }

    @Test
    fun dismissingPipPausesVideo() {
        screenOn(true)
        val activity = launch()
        val engine = playBeachDay()
        onPipChanged(activity, true)
        settle()
        assertTagShown(PipLayoutTag)

        // The window dismissed while the screen is on: the activity stops and the policy pauses.
        controller!!.pause().stop()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("dismissing picture-in-picture paused the video", "pause" in engine.calls)

        engine.emit(VideoEngineEvent.Paused)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(videoViewModel().uiState.value.isPlaying)
    }

    private fun onPipChanged(activity: MainActivity, inPip: Boolean) {
        activity.onPictureInPictureModeChanged(inPip, activity.resources.configuration)
    }

    /** Robolectric reports no PiP system feature by default; the policy checks it on leave hint. */
    private fun setPipSupported() {
        val manager = ApplicationProvider.getApplicationContext<Application>().packageManager
        shadowOf(manager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
    }

    private fun launch(): MainActivity {
        // API 30 reads videos through READ_EXTERNAL_STORAGE, and ContextCompat's not-exported
        // receiver needs the signature permission below; Robolectric grants neither on its own.
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            "${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
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

    private fun tabShown(label: String): Boolean =
        compose.onAllNodes(hasContentDescription(label).and(IsTab)).fetchSemanticsNodes().isNotEmpty()

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
}

private const val PipLayoutTag = "pip_layout"
