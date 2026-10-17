package com.shibi.musicplayer

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.lifecycle.ViewModelProvider
import com.shibi.musicplayer.playback.PlaybackService
import com.shibi.musicplayer.ui.InlineVideoTag
import com.shibi.musicplayer.ui.VideoPaneTag
import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoProgressHooks
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

    /** Every engine the activity's [VideoViewModel] created; libVLC never loads here. */
    private val engines = mutableListOf<FakeVideoEngine>()

    /**
     * Robolectric's fake bind hands media3 a null component; no session is needed here.
     * [WithMedia] tests also get music and video access, a fake MediaStore and the fake engine.
     */
    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule { base, description ->
            object : Statement() {
                override fun evaluate() {
                    val app = ApplicationProvider.getApplicationContext<Application>()
                    shadowOf(app).declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
                    if (description.getAnnotation(WithMedia::class.java) != null) {
                        shadowOf(app).grantPermissions(
                            Manifest.permission.READ_MEDIA_AUDIO,
                            Manifest.permission.READ_MEDIA_VIDEO,
                        )
                        Robolectric.buildContentProvider(FakeMediaStore::class.java).create(MediaStore.AUTHORITY)
                        VideoViewModel.testEngineFactory = { FakeVideoEngine().also(engines::add) }
                        VideoViewModel.testProgress = { VideoProgressHooks.None }
                    }
                    try {
                        base.evaluate()
                    } finally {
                        VideoViewModel.testEngineFactory = null
                        VideoViewModel.testProgress = null
                    }
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

    /** Videos is on both rings: opened folded, it survives an unfold and a fold back. */
    @Test
    fun videosSurvivesFoldAndUnfold() {
        start()
        fold(Compact)
        select("Videos")
        assertShown("Videos", "Video access needed")
        fold(Expanded)
        tab("Videos").assertIsSelected()
        assertShown("Videos", "Video access needed")
        fold(Compact)
        tab("Videos").assertIsSelected()
        assertShown("Videos", "Video access needed")
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

    /** Unfolded, a tapped video replaces Now Playing in the right pane; a song tap brings it back. */
    @Test
    @WithMedia
    fun videoPlaysInTheRightPaneUntilASongIsTapped() {
        start()
        assertShown("Now Playing pane", NothingPlaying)
        playVideo("Beach day")
        assertPane(video = true)
        assertGone(NothingPlaying)
        val engine = engines.single()
        assertNotNull("the pane attaches the picture", engine.attachedView)

        // The left pane browses while the video keeps playing.
        select("Songs")
        tab("Songs").assertIsSelected()
        assertPane(video = true)
        assertFalse("browsing paused the video", "pause" in engine.calls)

        tapText("Aria")
        assertPane(video = false)
        assertShown("Now Playing pane", NothingPlaying)
        assertTrue("a song tap releases the video", engine.released)
        assertFalse(videoViewModel().uiState.value.isActive)
    }

    /** The engine lives in the activity-scoped view model, so a fold and an unfold keep playing. */
    @Test
    @WithMedia
    fun foldAndUnfoldMidVideoKeepPlaying() {
        start()
        playVideo("Beach day")
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Time(42_000L))
        select("Settings")
        assertPane(video = true)

        // Folded: the compact box on the Videos route.
        fold(Compact)
        tab("Videos").assertIsSelected()
        assertTagShown(InlineVideoTag)
        assertTagGone(VideoPaneTag)
        assertNotNull("the box re-attached the picture", engine.attachedView)
        assertStillPlaying(engine)

        // Unfolded: the right pane, with the list on the left.
        fold(Expanded)
        tab("Videos").assertIsSelected()
        assertPane(video = true)
        assertTagGone(InlineVideoTag)
        assertNotNull("the pane re-attached the picture", engine.attachedView)
        assertStillPlaying(engine)
        assertEquals("one engine for the whole session", 1, engines.size)
    }

    @Test
    @WithMedia
    fun backClosesTheVideoPaneFirst() {
        start()
        playVideo("Beach day")
        select("Playlists")
        assertShown("Playlists", "No playlists yet")
        pressBack()
        assertPane(video = false)
        assertShown("Now Playing pane", NothingPlaying)
        tab("Playlists").assertIsSelected()
        assertShown("Playlists", "No playlists yet")
        assertTrue(engines.single().released)
    }

    /** No auto-next and no music: the end of the video returns the pane to Now Playing. */
    @Test
    @WithMedia
    fun endOfVideoReturnsToNowPlaying() {
        start()
        playVideo("Beach day")
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Ended)
        settle()
        assertPane(video = false)
        assertShown("Now Playing pane", NothingPlaying)
        assertTrue(engine.released)
    }

    private fun playVideo(title: String) {
        select("Videos")
        tapText(title)
    }

    private fun tapText(text: String) {
        assertShown(text, text)
        compose.onAllNodesWithText(text)[0].performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private fun pressBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun videoViewModel(): VideoViewModel {
        var viewModel: VideoViewModel? = null
        compose.activityRule.scenario.onActivity {
            viewModel = ViewModelProvider(it)[VideoViewModel::class.java]
        }
        return checkNotNull(viewModel)
    }

    private fun assertStillPlaying(engine: FakeVideoEngine) {
        val state = videoViewModel().uiState.value
        assertEquals("Beach day", state.video?.title)
        assertEquals(VideoStatus.Playing, state.status)
        assertEquals(42_000L, state.positionMs)
        assertFalse("the fold paused or released the video", "pause" in engine.calls || engine.released)
        assertEquals(1, engine.calls.count { it.startsWith("load:") })
    }

    private fun assertPane(video: Boolean) {
        if (video) assertTagShown(VideoPaneTag) else assertTagGone(VideoPaneTag)
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

    private fun assertGone(text: String) {
        val found = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        assertFalse("'$text' is still on screen", found)
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
        const val NothingPlaying = "Nothing playing"
        val IsTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

        /** One string per section that only that section's screen draws. */
        val ExpandedMarkers = listOf(
            "Playlists" to "No playlists yet",
            "Devices" to "Choose where your music plays",
            "Favorites" to "No favorites yet",
            "Recent" to "Songs you play show up here.",
            "Search" to "Search songs by title or artist.",
            "Settings" to "Dark theme",
            "Videos" to "Video access needed",
            "Songs" to "Music access needed",
        )
    }
}

private const val Expanded = "w900dp-h600dp-xhdpi"
private const val Compact = "w411dp-h891dp-xxhdpi"

/** Marks a test that runs with music and video access, a fake MediaStore and a fake engine. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
private annotation class WithMedia

/** One song and two videos, answered by URI so the music and video queries each get their columns. */
class FakeMediaStore : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor = when {
        "/audio/" in uri.toString() -> MatrixCursor(
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
            ),
        ).apply { addRow(arrayOf<Any?>(11L, "Aria", "Ensemble", "Arias", 200_000L, 0L)) }
        "/video/" in uri.toString() -> MatrixCursor(
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            ),
        ).apply {
            addRow(arrayOf<Any?>(1L, "Beach day.mp4", "Beach day", 754_000L, 1_412_000_000L, 1920, 1080, "video/mp4", 1_760_000_000L, "Camera"))
            addRow(arrayOf<Any?>(2L, "Recital.mp4", "Recital", 2_640_000L, 3_300_000_000L, 1920, 1080, "video/mp4", 1_760_000_000L, "Movies"))
        }
        else -> MatrixCursor(arrayOf(MediaStore.MediaColumns._ID))
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
