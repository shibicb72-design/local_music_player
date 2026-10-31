package com.shibi.musicplayer

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.playback.PlayerViewModel
import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The music mini player after a video ends, in the real activity: it comes back paused, never
 * starts the music on its own, and plays only when its play button is tapped. The fake player
 * stands in for the media session Robolectric cannot bind.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = CompactPortrait)
class MusicBannerAfterVideoTest {

    private val compose = createAndroidComposeRule<MainActivity>()
    private val engines = mutableListOf<FakeVideoEngine>()
    private val music = FakeMusicPlayer()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(MediaSetup(engines)).around(MusicPlayerSeam(music)).around(compose)

    @Test
    fun theBannerComesBackPausedWhenTheVideoEndsAndPlaysOnlyWhenTapped() {
        compose.mainClock.autoAdvance = false
        settle()
        // A song plays (a real tap on its row).
        assertTextShown("Aria")
        compose.onAllNodesWithText("Aria")[0].performClick()
        settle()
        assertTrue("the song plays", playerViewModel().uiState.value.isPlaying)

        // A video starts: it pauses the music and the bar goes, one play button at a time.
        compose.onNode(hasContentDescription("Videos").and(IsTab)).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        assertTextShown("Beach day")
        compose.onAllNodesWithText("Beach day")[0].performClick()
        settle()
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        settle()
        assertFalse("the video paused the music", playerViewModel().uiState.value.isPlaying)
        assertFalse("no mini player while the video plays", bannerShown())
        val requests = music.playRequests

        // The end: the bar is back, showing the song, paused (its button says Play).
        engine.emit(VideoEngineEvent.Ended)
        settle()
        assertEquals(VideoStatus.Ended, videoViewModel().uiState.value.status)
        assertTrue("the mini player is back", bannerShown())
        bannerPlayButton().assertExists()
        assertFalse(playerViewModel().uiState.value.isPlaying)
        assertEquals("nothing asked the music to play", requests, music.playRequests)

        // Time passes: still paused.
        settle(10_000)
        assertFalse(playerViewModel().uiState.value.isPlaying)
        assertEquals(requests, music.playRequests)
        assertTrue("the ended video stays loaded", videoViewModel().uiState.value.isActive)

        // Only the tap plays it, and music starting closes the ended video.
        bannerPlayButton().performClick()
        settle()
        assertEquals(requests + 1, music.playRequests)
        assertTrue(playerViewModel().uiState.value.isPlaying)
        assertFalse("the video closed for the music", videoViewModel().uiState.value.isActive)
    }

    /** The mini player's own play button: under the bar that names the song. */
    private fun bannerPlayButton(): SemanticsNodeInteraction =
        compose.onNode(hasContentDescription("Play").and(hasAnyAncestor(hasText("Aria"))))

    private fun bannerShown(): Boolean =
        compose.onAllNodesWithText("Aria").fetchSemanticsNodes().isNotEmpty()

    private fun playerViewModel(): PlayerViewModel {
        var viewModel: PlayerViewModel? = null
        compose.activityRule.scenario.onActivity { viewModel = ViewModelProvider(it)[PlayerViewModel::class.java] }
        return checkNotNull(viewModel)
    }

    private fun videoViewModel(): VideoViewModel {
        var viewModel: VideoViewModel? = null
        compose.activityRule.scenario.onActivity { viewModel = ViewModelProvider(it)[VideoViewModel::class.java] }
        return checkNotNull(viewModel)
    }

    private fun settle(ms: Long = 2_000) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun assertTextShown(text: String) {
        val found = runCatching {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        assertTrue("\"$text\" is not on screen", found)
    }
}

/** Hands [player] to the activity's [PlayerViewModel] instead of the session Robolectric cannot bind. */
internal class MusicPlayerSeam(private val player: Player) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            PlayerViewModel.testPlayerFactory = { player }
            try {
                base.evaluate()
            } finally {
                PlayerViewModel.testPlayerFactory = null
            }
        }
    }
}
