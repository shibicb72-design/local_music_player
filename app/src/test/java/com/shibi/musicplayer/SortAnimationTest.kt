package com.shibi.musicplayer

import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.LibrarySorter
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.UnknownNames
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.library.VideoLibraryUiState
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.SortButton
import com.shibi.musicplayer.ui.SortButtonTag
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.sortOptionTag
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import java.text.Collator
import java.time.ZoneId
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real menu taps with a held clock distinguish animation from the final resting layout. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SortAnimationTest {

    @get:Rule
    val compose = createComposeRule()

    private val resolver = RuntimeEnvironment.getApplication().contentResolver

    @After
    fun restoreMotion() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test
    fun menuOpeningScalesBeforeSettling() = checkMenuOpening(reduced = false)

    @Test
    fun menuOpeningSnapsWithReduceMotion() = checkMenuOpening(reduced = true)

    @Test
    fun menuClosingKeepsPopupUntilAnimationEnds() = checkMenuClosing(reduced = false)

    @Test
    fun menuClosingSnapsWithReduceMotion() = checkMenuClosing(reduced = true)

    @Test
    fun videoRowsAnimateAfterSortPick() = checkReorder(music = false, reduced = false)

    @Test
    fun videoRowsSnapAfterSortPickWithReduceMotion() = checkReorder(music = false, reduced = true)

    @Test
    fun songRowsAnimateAfterSortPick() = checkReorder(music = true, reduced = false)

    @Test
    fun songRowsSnapAfterSortPickWithReduceMotion() = checkReorder(music = true, reduced = true)

    private fun checkMenuOpening(reduced: Boolean) {
        setMenu(reduced)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(SortButtonTag).performClick()
        compose.mainClock.advanceTimeBy(64)
        val row = compose.onNodeWithTag(sortOptionTag(Ascending))
        val earlyWidth = row.fetchSemanticsNode().boundsInRoot.width
        compose.mainClock.advanceTimeBy(2_000)
        val settledWidth = row.fetchSemanticsNode().boundsInRoot.width
        assertTrue("the settled menu has a visible size", settledWidth > 0f)
        if (reduced) {
            assertEquals("menu is already full size", settledWidth, earlyWidth, 0.5f)
        } else {
            assertTrue("menu is still scaling: $earlyWidth -> $settledWidth", earlyWidth < settledWidth - 1f)
        }
    }

    private fun checkMenuClosing(reduced: Boolean) {
        setMenu(reduced)
        compose.onNodeWithTag(SortButtonTag).performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        // Picking the current option dismisses without changing the sort.
        val row = compose.onNodeWithTag(sortOptionTag(Ascending))
        row.performClick()
        compose.mainClock.advanceTimeBy(64)
        if (reduced) {
            row.assertDoesNotExist()
        } else {
            row.assertIsDisplayed()
        }
        compose.mainClock.advanceTimeBy(2_000)
        row.assertDoesNotExist()
    }

    private fun setMenu(reduced: Boolean) {
        setMotion(reduced)
        compose.setContent {
            MusicPlayerTheme {
                SortButton(
                    sort = Ascending,
                    options = LibrarySorts.VideoOptions,
                    onSortChange = { error("the current option must only close the menu") },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun checkReorder(music: Boolean, reduced: Boolean) {
        setMotion(reduced)
        var spec by mutableStateOf(Ascending)
        compose.setContent {
            MusicPlayerTheme {
                if (music) {
                    val sorted = LibrarySorter.tracks(
                        Songs,
                        spec,
                        Collator.getInstance(),
                        ZoneId.systemDefault(),
                        UnknownNames("Unknown title", "Unknown artist", "Unknown album"),
                    )
                    LibraryContent(
                        tracks = sorted.items,
                        sorted = sorted,
                        nowPlaying = PlayerUiState(),
                        showMiniPlayer = false,
                        onTrackSelected = { _, _ -> },
                        onOpenPlayer = {},
                        onTogglePlayPause = {},
                        onSortChange = { spec = it },
                        contentBottomPadding = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val sorted = LibrarySorter.videos(
                        Videos,
                        spec,
                        Collator.getInstance(),
                        ZoneId.systemDefault(),
                        "Unknown title",
                    )
                    VideosContent(
                        state = VideoLibraryUiState(
                            access = VideoAccess.Full,
                            videos = sorted.items,
                            sort = spec,
                            runs = sorted.runs,
                        ),
                        onVideoClick = {},
                        onRequestAccess = {},
                        onOpenSettings = {},
                        onSortChange = { spec = it },
                        contentBottomPadding = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()
        val initialTop = rowTop()
        compose.onNodeWithTag(SortButtonTag).performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(sortOptionTag(Descending)).performClick()
        compose.mainClock.advanceTimeBy(64)
        val earlyTop = rowTop()
        compose.mainClock.advanceTimeBy(2_000)
        val settledTop = rowTop()
        assertTrue("the pick moved Azure toward the top", initialTop > settledTop + 1f)
        if (reduced) {
            assertEquals("row is already at its sorted position", settledTop, earlyTop, 0.5f)
        } else {
            assertTrue("row is still moving: $earlyTop -> $settledTop", abs(earlyTop - settledTop) > 1f)
        }
    }

    private fun rowTop(): Float = compose.onNodeWithText("Azure").fetchSemanticsNode().boundsInRoot.top

    private fun setMotion(reduced: Boolean) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (reduced) 0f else 1f)
    }

    private companion object {
        val Ascending = SortSpec(SortField.Name, ascending = true)
        val Descending = SortSpec(SortField.Name, ascending = false)
        val Names = listOf("Alpha", "Alpine", "Azure")
        val Songs = Names.mapIndexed { index, title ->
            Track(index + 1L, title, "Artist", "Album", 180_000L, "content://songs/${index + 1}", null)
        }
        val Videos = Names.mapIndexed { index, title ->
            Video(
                id = index + 1L,
                title = title,
                displayName = "$title.mp4",
                durationMs = 180_000L,
                sizeBytes = 1_000_000L,
                width = 1920,
                height = 1080,
                mimeType = "video/mp4",
                dateAddedSeconds = 1_700_000_000L,
                folder = "Camera",
                contentUri = "content://videos/${index + 1}",
            )
        }
    }
}
