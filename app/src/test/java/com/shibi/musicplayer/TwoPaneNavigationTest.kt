package com.shibi.musicplayer

import android.app.Application
import android.content.ComponentName
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.playback.PlaybackService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real activity, driven through the carousel. A fold or unfold recreates the
 * activity at the new width, as it does on the Fold 7, so the NavController
 * restores its back stack and saved section stacks exactly like the device.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Expanded)
class TwoPaneNavigationTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    /** Robolectric's fake bind hands media3 a null component; no session is needed here. */
    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule { base, _ ->
            object : Statement() {
                override fun evaluate() {
                    val app = ApplicationProvider.getApplicationContext<Application>()
                    shadowOf(app).declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
                    base.evaluate()
                }
            }
        }
        .around(compose)

    @Test
    fun coldStartExpandedLoadsEverySection() {
        start()
        for ((label, marker) in ExpandedMarkers) {
            select(label)
            assertShown(label, marker)
            if (label == "Playlists" || label == "Devices") {
                compose.onRoot().captureRoboImage(
                    "build/outputs/roborazzi/cv4_expanded_${label.lowercase()}_dark.png",
                )
            }
        }
    }

    /** Shibi's path: Now Playing opened over Playlists folded, then Playlists picked unfolded. */
    @Test
    fun nowPlayingOverPlaylistsDoesNotBlankTwoPane() {
        start()
        fold(Compact)
        select("Playlists")
        select("Now playing")
        select("Devices")
        fold(Expanded)
        select("Playlists")
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/cv4_unfold_playlists_after_now_playing.png")
        walkExpanded()
    }

    @Test
    fun nowPlayingOverDevicesDoesNotBlankTwoPane() {
        start()
        fold(Compact)
        select("Devices")
        select("Now playing")
        select("Playlists")
        fold(Expanded)
        walkExpanded()
    }

    @Test
    fun sectionsSwitchAfterFoldUnfoldCycle() {
        start()
        select("Recent")
        fold(Compact)
        select("Favorites")
        fold(Expanded)
        walkExpanded()
        fold(Compact)
        fold(Expanded)
        walkExpanded()
    }

    /** Unfolding on Now Playing lands on the section under it, with the left pane filled. */
    @Test
    fun unfoldOnNowPlayingFallsBackToSection() {
        start()
        fold(Compact)
        select("Playlists")
        select("Now playing")
        fold(Expanded)
        tab("Playlists").assertIsSelected()
        assertShown("Playlists", "No playlists yet")
        assertNoNowPlayingTab()
        walkExpanded()
    }

    @Test
    fun unfoldOnNowPlayingFromSongsFallsBackToSongs() {
        start()
        fold(Compact)
        select("Now playing")
        fold(Expanded)
        tab("Songs").assertIsSelected()
        assertShown("Songs", "Music access needed")
        walkExpanded()
    }

    /** Folded, picking a section again shows that section, never a restored Now Playing. */
    @Test
    fun compactRestoresSectionWithoutNowPlaying() {
        start()
        fold(Compact)
        select("Playlists")
        select("Now playing")
        select("Devices")
        select("Playlists")
        tab("Playlists").assertIsSelected()
        assertShown("Playlists", "No playlists yet")
    }

    private fun start() {
        compose.mainClock.autoAdvance = false
        settle()
    }

    private fun fold(qualifiers: String) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        compose.activityRule.scenario.recreate()
        settle()
    }

    private fun walkExpanded() {
        for ((label, marker) in ExpandedMarkers) {
            select(label)
            tab(label).assertIsSelected()
            assertShown(label, marker)
        }
    }

    private fun tab(label: String): SemanticsNodeInteraction =
        compose.onNode(hasContentDescription(label).and(IsTab))

    private fun select(label: String) {
        tab(label).performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    /** The ring spring and the nav crossfade both finish well inside two seconds. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    /**
     * Room answers on its own thread, so give the screen a moment to fill in. The
     * clock is paused, so each poll steps one frame to let that answer recompose.
     */
    private fun assertShown(label: String, marker: String) {
        val found = runCatching {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithText(marker, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        assertTrue("$label: left pane does not show \"$marker\"", found)
    }

    private fun assertNoNowPlayingTab() {
        val found = compose.onAllNodes(hasContentDescription("Now playing").and(IsTab))
            .fetchSemanticsNodes().isNotEmpty()
        assertFalse("two-pane ring still offers Now playing", found)
    }

    private companion object {
        val IsTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

        /** One string per section that only that section's screen draws. */
        val ExpandedMarkers = listOf(
            "Playlists" to "No playlists yet",
            "Devices" to "Choose where your music plays",
            "Favorites" to "No favorites yet",
            "Recent" to "Songs you play show up here.",
            "Search" to "Search songs by title or artist.",
            "Settings" to "Dark theme",
            "Songs" to "Music access needed",
        )
    }
}

private const val Expanded = "w900dp-h600dp-xhdpi"
private const val Compact = "w411dp-h891dp-xxhdpi"
