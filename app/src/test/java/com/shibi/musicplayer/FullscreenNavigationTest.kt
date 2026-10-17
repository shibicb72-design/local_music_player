package com.shibi.musicplayer

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.playback.PlaybackService
import com.shibi.musicplayer.ui.FullscreenVideoTag
import com.shibi.musicplayer.ui.GestureChipTag
import com.shibi.musicplayer.ui.InlineVideoTag
import com.shibi.musicplayer.ui.TrackSheetTag
import com.shibi.musicplayer.ui.VideoPaneTag
import com.shibi.musicplayer.video.RawTrackDescription
import com.shibi.musicplayer.video.TrackSnapshot
import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoSessionLink
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * V6 fullscreen in the real activity, with a fake MediaStore and [FakeVideoEngine]: the
 * button in the folded box and in the unfolded pane, Back, immersive orientation, and the
 * volume, brightness and double-tap gestures. Rotation without a recreate is in
 * [ConfigChangeWithoutRecreateTest].
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = CompactPortrait)
class FullscreenNavigationTest {

    private val compose = createAndroidComposeRule<MainActivity>()
    private val engines = mutableListOf<FakeVideoEngine>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(MediaSetup(engines)).around(compose)

    @Test
    fun foldedBoxOpensFullscreenAndBackReturnsToIt() {
        start()
        playVideo("Beach day")
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Time(42_000L))
        assertTagShown(InlineVideoTag)
        val boxView = engine.attachedView

        tapFullscreen()
        assertTagShown(FullscreenVideoTag)
        assertFalse("the carousel is hidden", tabShown("Videos"))
        assertTrue("fullscreen attached its own view", engine.attachedView !== boxView)
        assertNotNull(engine.attachedView)
        // Beach day is 1920x1080: wide, so it turns sideways.
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, requestedOrientation())

        pressBack()
        assertTagGone(FullscreenVideoTag)
        assertTagShown(InlineVideoTag)
        assertTrue("the carousel is back", tabShown("Videos"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientation())
        assertNotNull("the box re-attached the picture", engine.attachedView)
        val state = videoViewModel().uiState.value
        assertEquals(VideoStatus.Playing, state.status)
        assertEquals(42_000L, state.positionMs)
        assertFalse("Back paused or released the video", "pause" in engine.calls || engine.released)
    }

    @Test
    @Config(qualifiers = Expanded)
    fun unfoldedPaneOpensFullscreenAndBackKeepsTheVideo() {
        start()
        playVideo("Beach day")
        assertTagShown(VideoPaneTag)
        tapFullscreen()
        assertTagShown(FullscreenVideoTag)
        assertFalse("the carousel is hidden", tabShown("Videos"))

        // The first Back leaves fullscreen; it does not close the two-pane video.
        pressBack()
        assertTagGone(FullscreenVideoTag)
        assertTagShown(VideoPaneTag)
        assertTrue(videoViewModel().uiState.value.isActive)
        assertFalse(engines.single().released)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientation())
    }

    @Test
    fun theExitButtonLeavesFullscreen() {
        start()
        playVideo("Beach day")
        tapFullscreen()
        assertTagShown(FullscreenVideoTag)
        // Buffering keeps the controls up, so the exit button is there.
        tapDescription("Exit fullscreen")
        assertTagGone(FullscreenVideoTag)
        assertTagShown(InlineVideoTag)
    }

    /** Music started from the notification closes the video; fullscreen goes with it. */
    @Test
    fun closingTheVideoLeavesFullscreen() {
        start()
        playVideo("Beach day")
        tapFullscreen()
        videoViewModel().close()
        settle()
        assertTagGone(FullscreenVideoTag)
        assertTrue(tabShown("Videos"))
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requestedOrientation())
    }

    @Test
    fun rightHalfSwipeSetsTheMusicVolumeAndShowsOnlyTheChip() {
        val audio = ApplicationProvider.getApplicationContext<Application>()
            .getSystemService(AudioManager::class.java)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        openPlayingFullscreen()

        fullscreen().performTouchInput { verticalSwipe(x = width * 0.8f, from = height * 0.9f, to = height * 0.1f) }
        settle(500)
        assertEquals(max, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertTextShown("$max / $max")

        fullscreen().performTouchInput { verticalSwipe(x = width * 0.8f, from = height * 0.1f, to = height * 0.9f) }
        settle(500)
        assertTrue(audio.getStreamVolume(AudioManager.STREAM_MUSIC) < max)
        // The chip fades 0.8 s after the last change.
        settle(2_000)
        assertTagGone(GestureChipTag)
    }

    @Test
    fun leftHalfSwipeSetsTheWindowBrightnessAndLeavingResetsIt() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // About half; the swipe starts from the system level while the window follows it.
        Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        start()
        setBrightnessSwipe(true)
        openPlayingFullscreen()
        fullscreen().performTouchInput { verticalSwipe(x = width * 0.2f, from = height * 0.2f, to = height * 0.6f) }
        settle(500)
        val dimmed = windowBrightness()
        assertTrue("window brightness $dimmed", dimmed > 0f && dimmed < 0.5f)
        assertTextShown("%")

        pressBack()
        assertEquals(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, windowBrightness())
    }

    @Test
    fun brightnessSwipeOffLeavesTheWindowAlone() {
        start()
        // The DataStore outlives the test in this JVM, so the default is put back.
        setBrightnessSwipe(false)
        try {
            playVideo("Beach day")
            tapFullscreen()
            engines.single().emit(VideoEngineEvent.Playing)
            settle()
            fullscreen().performTouchInput { verticalSwipe(x = width * 0.2f, from = height * 0.2f, to = height * 0.6f) }
            settle(500)
            assertEquals(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, windowBrightness())
        } finally {
            setBrightnessSwipe(true)
        }
    }

    @Test
    fun doubleTapOnTheThirdsSeeksTenSeconds() {
        openPlayingFullscreen()
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Length(754_000L))
        engine.emit(VideoEngineEvent.Time(60_000L))
        settle()
        fullscreen().performTouchInput { doubleClick(centerRight.copy(x = width * 0.9f)) }
        settle(500)
        assertEquals("seek:70000", engine.calls.last())
        fullscreen().performTouchInput { doubleClick(centerLeft.copy(x = width * 0.1f)) }
        settle(500)
        assertEquals("seek:60000", engine.calls.last())
        val seeks = engine.calls.count { it.startsWith("seek:") }
        fullscreen().performTouchInput { doubleClick(center) }
        settle(500)
        assertEquals("the middle third does not seek", seeks, engine.calls.count { it.startsWith("seek:") })
    }

    /** V7: the placeholder V6 hid is now the tracks button; the sheet opens over fullscreen. */
    @Test
    fun theTracksButtonOpensTheSheetOverFullscreen() {
        start()
        playVideo("Beach day")
        val engine = engines.single()
        engine.trackState.value = TrackSnapshot(
            subtitles = listOf(RawTrackDescription(-1, "Disable"), RawTrackDescription(3, "Track 1 - [English]")),
        )
        tapFullscreen()
        assertTagShown(FullscreenVideoTag)
        tapDescription("Audio and subtitles")
        assertTagShown(TrackSheetTag)
        assertTextShown("Load subtitle file…")

        compose.onNodeWithText("Track 1 - [English]").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertEquals("subtitle:3", engine.calls.last())
        // The sheet sits over fullscreen; fullscreen is still there under it.
        assertTagShown(FullscreenVideoTag)
    }

    @Test
    fun theFoldedBoxOpensTheSheetToo() {
        start()
        playVideo("Beach day")
        assertTagShown(InlineVideoTag)
        tapDescription("Audio and subtitles")
        assertTagShown(TrackSheetTag)
        // Closing the video takes the sheet with it.
        videoViewModel().close()
        settle()
        assertTagGone(TrackSheetTag)
    }

    private fun openPlayingFullscreen() {
        start()
        playVideo("Beach day")
        tapFullscreen()
        engines.single().emit(VideoEngineEvent.Playing)
        settle()
        assertTagShown(FullscreenVideoTag)
    }

    /** The pref is written on DataStore's own thread; wait until the view model sees it. */
    private fun setBrightnessSwipe(on: Boolean) {
        val viewModel = videoViewModel()
        viewModel.setBrightnessSwipe(on)
        compose.waitUntil(timeoutMillis = 5_000) {
            // DataStore answers on its own thread and then posts to the paused main looper.
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeByFrame()
            viewModel.brightnessSwipe.value == on
        }
        settle()
    }

    private fun fullscreen() = compose.onNode(hasTestTag(FullscreenVideoTag))

    private fun TouchInjectionScope.verticalSwipe(x: Float, from: Float, to: Float) =
        swipe(start = Offset(x, from), end = Offset(x, to), durationMillis = 300)

    private fun windowBrightness(): Float {
        var level = 0f
        compose.activityRule.scenario.onActivity { level = it.window.attributes.screenBrightness }
        return level
    }

    private fun requestedOrientation(): Int {
        var orientation = Int.MIN_VALUE
        compose.activityRule.scenario.onActivity { orientation = it.requestedOrientation }
        return orientation
    }

    private fun videoViewModel(): VideoViewModel {
        var viewModel: VideoViewModel? = null
        compose.activityRule.scenario.onActivity {
            viewModel = ViewModelProvider(it)[VideoViewModel::class.java]
        }
        return checkNotNull(viewModel)
    }

    private fun playVideo(title: String) {
        compose.onNode(hasContentDescription("Videos").and(IsTab)).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertTextShown(title)
        compose.onAllNodesWithText(title)[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private fun tapFullscreen() = tapDescription("Fullscreen")

    private fun tapDescription(description: String) {
        compose.onNode(hasContentDescription(description)).performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private fun tabShown(label: String): Boolean =
        compose.onAllNodes(hasContentDescription(label).and(IsTab)).fetchSemanticsNodes().isNotEmpty()

    private fun pressBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun start() {
        compose.mainClock.autoAdvance = false
        settle()
    }

    private fun settle(ms: Long = 2_000) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun assertTextShown(text: String) {
        val found = runCatching {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        assertTrue("\"$text\" is not on screen", found)
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

/**
 * Music and video access, the fake MediaStore, the fake engine through the view model's
 * seam, and an unbindable [PlaybackService] (Robolectric's bind hands media3 a null).
 */
internal class MediaSetup(private val engines: MutableList<FakeVideoEngine>) : TestRule {
    override fun apply(base: Statement, description: org.junit.runner.Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val app = ApplicationProvider.getApplicationContext<Application>()
                shadowOf(app).declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
                shadowOf(app).grantPermissions(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO,
                )
                Robolectric.buildContentProvider(FakeMediaStore::class.java).create(MediaStore.AUTHORITY)
                VideoViewModel.testEngineFactory = { FakeVideoEngine().also(engines::add) }
                // Robolectric cannot bind the session service; tests that care record the link.
                VideoViewModel.testSessionLink = { VideoSessionLink.None }
                try {
                    base.evaluate()
                } finally {
                    VideoViewModel.testEngineFactory = null
                    VideoViewModel.testSessionLink = null
                }
            }
        }
}

internal val IsTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

internal const val CompactPortrait = "w411dp-h891dp-xxhdpi"
private const val Expanded = "w900dp-h600dp-xhdpi"
