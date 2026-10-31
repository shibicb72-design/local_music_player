package com.shibi.musicplayer

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.ui.FullscreenVideoTag
import com.shibi.musicplayer.ui.VideoPaneTag
import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoViewModel
import org.junit.After
import org.junit.Assert.assertEquals
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
 * V11 "Open with Theatrum" in the real activity, with a fake engine: an ACTION_VIEW video
 * intent plays straight into fullscreen, a web link, a non-video file and a bad launch are
 * rejected without touching the engine, and a new intent while running switches the video.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = CompactPortrait)
class OpenWithTest {

    private val compose = createEmptyComposeRule()
    private val engines = mutableListOf<FakeVideoEngine>()
    private var controller: ActivityController<MainActivity>? = null

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(MediaSetup(engines)).around(compose)

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
    }

    @Test
    fun viewIntentPlaysStraightIntoFullscreen() {
        launch(viewIntent("content://com.example.files/video/clip.mp4", "video/mp4"))
        awaitEngineLoad("content://com.example.files/video/clip.mp4")
        settle()

        assertTagShown(FullscreenVideoTag)
        assertEngineLoaded("content://com.example.files/video/clip.mp4")
    }

    @Test
    fun webLinkIsRejected() {
        launch(viewIntent("https://example.com/clip.mp4", "video/mp4"))
        settle()

        assertTagGone(FullscreenVideoTag)
        assertFalse(videoViewModel().uiState.value.isActive)
    }

    @Test
    fun nonVideoIsRejected() {
        launch(viewIntent("content://com.example.files/doc.pdf", "application/pdf"))
        settle()

        assertTagGone(FullscreenVideoTag)
        assertFalse(videoViewModel().uiState.value.isActive)
    }

    @Test
    fun newIntentSwitchesToTheVideo() {
        launch()
        assertTagGone(FullscreenVideoTag)

        controller!!.newIntent(viewIntent("content://com.example.files/video/clip.mp4", "video/mp4"))
        awaitEngineLoad("content://com.example.files/video/clip.mp4")
        settle()

        assertTagShown(FullscreenVideoTag)
        assertEngineLoaded("content://com.example.files/video/clip.mp4")
    }

    /**
     * Unfolded, a video from another app plays in fullscreen over the tab that was open. Its end
     * lands on Videos, where the pane keeps it with "Play again", not on that tab's Now Playing.
     */
    @Test
    @Config(qualifiers = Expanded)
    fun theEndUnfoldedLandsOnVideosWithTheVideoInThePane() {
        launch(viewIntent("content://com.example.files/video/clip.mp4", "video/mp4"))
        awaitEngineLoad("content://com.example.files/video/clip.mp4")
        settle()
        assertTagShown(FullscreenVideoTag)
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Ended)
        settle()

        assertTagGone(FullscreenVideoTag)
        assertTagShown(VideoPaneTag)
        compose.onNode(hasContentDescription("Videos").and(IsTab)).assertIsSelected()
        val playAgain = compose.onAllNodes(hasContentDescription("Play again")).fetchSemanticsNodes()
        assertTrue("the pane shows \"Play again\"", playAgain.isNotEmpty())
        assertFalse("the end released the video", engine.released)
        assertEquals(VideoStatus.Ended, videoViewModel().uiState.value.status)
    }

    private fun viewIntent(uri: String, type: String): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), type)

    private fun launch(intent: Intent? = null): MainActivity {
        // ContextCompat's not-exported receiver needs the signature permission below;
        // Robolectric grants it on its own only sometimes.
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            "${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        )
        val created = if (intent == null) {
            Robolectric.buildActivity(MainActivity::class.java).setup()
        } else {
            Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        }
        controller = created
        compose.mainClock.autoAdvance = false
        settle()
        return created.get()
    }

    private fun videoViewModel(): VideoViewModel =
        ViewModelProvider(controller!!.get())[VideoViewModel::class.java]

    /**
     * The activity reads the external address on a background dispatcher before it reaches the
     * channel; on the paused Robolectric looper the test has to pump both queues and let the
     * reader thread run.
     */
    private fun awaitEngineLoad(uri: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (engines.none { engine -> "load:$uri" in engine.calls } && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(100L)
            compose.waitForIdle()
            Thread.sleep(10L)
        }
    }

    private fun assertEngineLoaded(uri: String) {
        assertTrue("the engine was not asked to load $uri", engines.any { "load:$uri" in it.calls })
    }

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

private const val Expanded = "w900dp-h600dp-xhdpi"
