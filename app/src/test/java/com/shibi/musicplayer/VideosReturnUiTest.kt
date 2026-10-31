package com.shibi.musicplayer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.library.VideoLibraryUiState
import com.shibi.musicplayer.ui.VideoFastScrollerTag
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Returning from fullscreen composes Videos again before the lifecycle is RESUMED, so
 * [VideosContent]'s resume check (`checked`) is false while the view model still holds the
 * list. The count, rows, month header and fast scroller must stay; an empty or loading
 * state is only for a first visit that has nothing yet.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VideosReturnUiTest {

    @get:Rule
    val compose = createComposeRule()

    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun loadedListStaysVisibleBeforeTheResumeCheck() {
        compose.setContent {
            MusicPlayerTheme {
                CompositionLocalProvider(
                    LocalInspectionMode provides true,
                    LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
                ) {
                    VideosContent(
                        state = VideosScreenshotTest.full.copy(isLoading = true),
                        checked = false,
                        onVideoClick = {},
                        onRequestAccess = {},
                        onOpenSettings = {},
                        contentBottomPadding = 0.dp,
                        fastScrollDragFraction = 0.4f,
                    )
                }
            }
        }
        compose.onNodeWithText("Videos").assertExists()
        // The count, then the sort the list is in.
        compose.onNodeWithText("9 videos · Newest first").assertExists()
        compose.onNodeWithText("Beach day").assertExists()
        compose.onNodeWithText("October 2025").assertExists()
        compose.onNodeWithTag(VideoFastScrollerTag).assertExists()
        compose.onNodeWithText("No videos found on this device.").assertDoesNotExist()
    }

    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun firstVisitWithNothingLoadedStaysBlankUntilTheResumeCheck() {
        compose.setContent {
            MusicPlayerTheme {
                CompositionLocalProvider(
                    LocalInspectionMode provides true,
                    LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
                ) {
                    VideosContent(
                        state = VideoLibraryUiState(access = VideoAccess.Full, isLoading = true),
                        checked = false,
                        onVideoClick = {},
                        onRequestAccess = {},
                        onOpenSettings = {},
                        contentBottomPadding = 0.dp,
                    )
                }
            }
        }
        compose.onNodeWithText("Videos").assertExists()
        compose.onNodeWithText("0 videos").assertDoesNotExist()
        compose.onNodeWithText("No videos found on this device.").assertDoesNotExist()
    }
}
