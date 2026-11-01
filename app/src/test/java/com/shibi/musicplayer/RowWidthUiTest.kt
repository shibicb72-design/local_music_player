package com.shibi.musicplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The width bug from a Galaxy S24 Ultra: Songs rows stopped short of the right edge (they left
 * room for the fast-scroll thumb) while Videos rows filled the width. At each phone size
 * ([DeviceProfiles]) a Songs row and a Videos row, both lists as shipped (sorted, so with their
 * fast scrollers), must have the same bounds, with the same margin on the left and the right.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RowWidthUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = S24_ULTRA)
    fun s24UltraSongsRowsMatchVideosRows() = assertRowsMatch()

    @Test
    @Config(qualifiers = S24_ULTRA_SMALL)
    fun s24UltraSmallSongsRowsMatchVideosRows() = assertRowsMatch()

    @Test
    @Config(qualifiers = S25)
    fun s25SongsRowsMatchVideosRows() = assertRowsMatch()

    @Test
    @Config(qualifiers = S25_ULTRA)
    fun s25UltraSongsRowsMatchVideosRows() = assertRowsMatch()

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSongsRowsMatchVideosRows() = assertRowsMatch()

    @Test
    @Config(qualifiers = WIDE412)
    fun wide412SongsRowsMatchVideosRows() = assertRowsMatch()

    /** Songs above Videos, each list full width; their first rows are compared. */
    @OptIn(ExperimentalCoilApi::class)
    private fun assertRowsMatch() {
        val shipped = shippedSongs(tracks)
        compose.setContent {
            MusicPlayerTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LibraryContent(
                            tracks = shipped.items,
                            sorted = shipped,
                            nowPlaying = PlayerUiState(),
                            showMiniPlayer = false,
                            onTrackSelected = { _, _ -> },
                            onOpenPlayer = {},
                            onTogglePlayPause = {},
                            contentBottomPadding = 0.dp,
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        CompositionLocalProvider(
                            LocalInspectionMode provides true,
                            LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
                        ) {
                            VideosContent(
                                state = VideosScreenshotTest.full,
                                onVideoClick = {},
                                onRequestAccess = {},
                                onOpenSettings = {},
                                contentBottomPadding = 0.dp,
                            )
                        }
                    }
                }
            }
        }
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val song = rowOf("Alpha")
        val video = rowOf("Beach day")
        assertEquals("Songs and Videos rows start together", video.left, song.left, 0.5f)
        assertEquals("Songs and Videos rows end together", video.right, song.right, 0.5f)
        // Each side rounds 4 dp to whole pixels on its own, so allow a pixel between them.
        assertEquals("even margins (left ${song.left} px)", song.left, width - song.right, 1f)
        assertEquals("the rows' 4 dp fill inset", with(compose.density) { 4.dp.toPx() }, song.left, 1f)
    }

    /** The row's clickable bounds: the merged node that holds [title]. */
    private fun rowOf(title: String): Rect = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot

    private companion object {
        val tracks = listOf(
            Track(1L, "Alpha", "Artist One", "Album A", 100_000L, "content://tracks/1", null),
            Track(2L, "Bravo", "Artist Two", "Album B", 110_000L, "content://tracks/2", null),
            Track(3L, "Charlie", "Artist Three", "Album C", 120_000L, "content://tracks/3", null),
        )
    }
}
