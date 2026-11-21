package com.shibi.musicplayer

import android.Manifest
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoFilter
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.library.InMemorySortPrefs
import com.shibi.musicplayer.library.VideoLibraryViewModel
import com.shibi.musicplayer.library.VideoRepository
import com.shibi.musicplayer.ui.AppBackHandler
import com.shibi.musicplayer.ui.VideosScreen
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The TV chip's show list and show page on the real screen: the real view model over a fake
 * MediaStore holding Lost (four episodes over two seasons), Fargo (one episode) and one movie.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TvShowsUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: VideoLibraryViewModel
    private val clicked = mutableListOf<Long>()
    private var outerBacks = 0

    @Before
    fun setUp() {
        val application = compose.activity.application
        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        val provider = Robolectric.buildContentProvider(FakeMediaProvider::class.java)
            .create(MediaStore.AUTHORITY)
            .get()
        provider.rows += row(1L, "Lost.S01E01.mkv", "Lost.S01E01", "Lost")
        provider.rows += row(2L, "Lost.S01E02.mkv", "Lost.S01E02", "Lost")
        provider.rows += row(3L, "Lost.S02E01.mkv", "Lost.S02E01", "Lost")
        provider.rows += row(4L, "Lost.S02E02.mkv", "Lost.S02E02", "Lost")
        provider.rows += row(5L, "Fargo.S01E01.mkv", "Fargo.S01E01", "Fargo")
        provider.rows += row(6L, "Inception.2010.1080p.mkv", "Inception.2010.1080p", "Downloads")
        viewModel = VideoLibraryViewModel(
            application = application,
            repository = VideoRepository(application),
            ioDispatcher = Dispatchers.Unconfined,
            sortPrefs = InMemorySortPrefs(),
        )
    }

    @Test
    fun theTvChipShowsOneRowPerShowWithItsCounts() {
        show()
        tvChip().performClick()
        settle()
        compose.onNodeWithText("Fargo").assertIsDisplayed()
        compose.onNodeWithText("Lost").assertIsDisplayed()
        compose.onNodeWithText("1 season · 1 episode").assertIsDisplayed()
        compose.onNodeWithText("2 seasons · 4 episodes").assertIsDisplayed()
        compose.onNodeWithText("2 shows · 5 episodes").assertIsDisplayed()
        compose.onNodeWithText("Play all").assertDoesNotExist()
        compose.onNodeWithText("Lost.S01E01").assertDoesNotExist()
        tvChip().assertIsSelected()
    }

    @Test
    fun aShowPartWayThroughAnEpisodeShowsItsContinueLine() {
        // 40 min long, stopped at 16 min 40 s: 23 min 20 s left reads 24.
        show(savedProgress = mapOf(uri(2L) to VideoProgress(1_000_000L, Length, updatedAtMs = 10L)))
        tvChip().performClick()
        settle()
        compose.onNodeWithText("Continue S01E02 · 24 min left").assertIsDisplayed()
    }

    @Test
    fun aShowPageSwitchesSeasonsAndContinuesWhereYouStopped() {
        show(savedProgress = mapOf(uri(2L) to VideoProgress(1_000_000L, Length, updatedAtMs = 10L)))
        tvChip().performClick()
        settle()
        compose.onNodeWithText("Lost").performClick()
        settle()
        assertEquals("lost", viewModel.uiState.value.openShowKey)
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        tvChip().assertDoesNotExist()
        // The continue episode's season is chosen first.
        compose.onNodeWithContentDescription("Season 1").assertIsSelected()
        compose.onNodeWithText("E02 · Episode 2").performClick()
        settle()
        assertEquals(listOf(2L), clicked)
        compose.onNodeWithContentDescription("Season 2").performClick()
        settle()
        compose.onNodeWithContentDescription("Season 2").assertIsSelected()
        compose.onNodeWithText("E01 · Episode 1").performClick()
        settle()
        assertEquals(listOf(2L, 3L), clicked)
        compose.onNodeWithText("Continue S01E02").performClick()
        settle()
        assertEquals(listOf(2L, 3L, 2L), clicked)
    }

    @Test
    fun withNoProgressTheButtonPlaysTheFirstEpisode() {
        show()
        openLost()
        compose.onNodeWithText("Play S01E01").performClick()
        settle()
        assertEquals(listOf(1L), clicked)
    }

    @Test
    fun aOneSeasonShowHasNoSeasonChips() {
        show()
        tvChip().performClick()
        settle()
        compose.onNodeWithText("Fargo").performClick()
        settle()
        compose.onNodeWithText("E01 · Episode 1").assertIsDisplayed()
        compose.onNode(hasContentDescription("Seasons")).assertDoesNotExist()
        compose.onNodeWithContentDescription("Season 1").assertDoesNotExist()
    }

    @Test
    fun backClosesTheShowPageThenLeavesTheScreen() {
        show()
        openLost()
        pressBack()
        assertNull(viewModel.uiState.value.openShowKey)
        compose.onNodeWithText("2 seasons · 4 episodes").assertIsDisplayed()
        assertEquals(0, outerBacks)
        pressBack()
        assertEquals(1, outerBacks)
    }

    @Test
    fun changingTheFilterClosesTheShowPage() {
        show()
        openLost()
        compose.runOnUiThread { viewModel.setFilter(VideoFilter.Movies) }
        settle()
        assertNull(viewModel.uiState.value.openShowKey)
        compose.onNodeWithText("Inception").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun selectingOnAnotherFilterKeepsItsOwnBack() {
        show()
        compose.onNodeWithText("Inception.2010.1080p").performTouchInput { longClick() }
        settle()
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("1 selected").assertDoesNotExist()
        assertEquals(0, outerBacks)
        assertNull(viewModel.uiState.value.openShowKey)
        pressBack()
        assertEquals(1, outerBacks)
    }

    private fun openLost() {
        tvChip().performClick()
        settle()
        compose.onNodeWithText("Lost").performClick()
        settle()
    }

    private fun tvChip() = compose.onNodeWithContentDescription("TV, 5 videos")

    private fun show(savedProgress: Map<String, VideoProgress> = emptyMap()) {
        compose.setContent {
            MusicPlayerTheme {
                // Composed first, so the screen's own handlers are newer and win while enabled.
                AppBackHandler { outerBacks++ }
                Box(Modifier.fillMaxSize()) {
                    VideosScreen(
                        onVideoClick = { video: Video -> clicked += video.id },
                        contentBottomPadding = 0.dp,
                        viewModel = viewModel,
                        savedProgress = savedProgress,
                    )
                }
            }
        }
        settle()
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun uri(id: Long) = "content://media/external/video/media/$id"

    private fun row(id: Long, displayName: String, title: String, folder: String): Array<Any?> =
        arrayOf(id, displayName, title, Length, 1_000_000L, 1920, 1080, "video/mp4", 1_600_000_000L + id, folder)

    private companion object {
        const val Length = 2_400_000L
    }
}
