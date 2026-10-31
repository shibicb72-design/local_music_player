package com.shibi.musicplayer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A Songs row's text with real font metrics (native graphics): a long artist gives way so the
 * length always shows in full, and larger fonts grow the row instead of clipping its text.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ListRowsTextUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val song = Track(
        1L, "Alpha", "An artist name far too long for one line of a list row", "Album A",
        225_000L, "content://tracks/1", null,
    )

    @Test
    fun aLongArtistEllipsizesWhileTheLengthStaysWhole() {
        songs()
        assertTrue("artist ellipsized", overflows(hasText(song.artist)))
        val title = bounds(hasText("Alpha"))
        val artist = bounds(hasText(song.artist))
        val length = bounds(hasText("03:45"))
        assertTrue("length after the artist", length.left >= artist.right - 0.5f)
        assertTrue("length inside the text column", length.right <= title.right + 0.5f)
        // " · 03:45" at 14 sp is about 45 dp wide; squeezed, it would shrink.
        assertTrue("length in full: ${length.width} px", length.width > px(35.dp))
        assertEquals(px(60.dp), rowOf("Alpha").height, 0.5f)
    }

    @Test
    fun largerFontsGrowTheRowInsteadOfClippingIt() {
        songs(fontScale = 2f)
        val row = rowOf("Alpha")
        assertTrue("row grew past 60 dp: ${row.height} px", row.height > px(60.dp) + 1f)
        for (text in listOf(bounds(hasText("Alpha")), bounds(hasText(song.artist)))) {
            assertTrue("text inside the row", text.top >= row.top - 0.5f && text.bottom <= row.bottom + 0.5f)
        }
        compose.onAllNodesWithContentDescription("More")[0].assertTouchHeightIsEqualTo(48.dp)
        val pill = compose.onNodeWithText("Play all").fetchSemanticsNode().boundsInRoot
        assertTrue("pill at least 48 dp: ${pill.height} px", pill.height >= px(48.dp) - 0.5f)
    }

    private fun songs(fontScale: Float = 1f) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                MusicPlayerTheme {
                    LibraryContent(
                        tracks = listOf(song),
                        sorted = shippedSongs(listOf(song)),
                        nowPlaying = PlayerUiState(),
                        showMiniPlayer = false,
                        onTrackSelected = { _, _ -> },
                        onOpenPlayer = {},
                        onTogglePlayPause = {},
                        contentBottomPadding = 0.dp,
                    )
                }
            }
        }
    }

    /** The row's clickable bounds: the merged node that holds [title]. */
    private fun rowOf(title: String): Rect = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot

    private fun bounds(matcher: SemanticsMatcher): Rect =
        compose.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun overflows(matcher: SemanticsMatcher): Boolean {
        val results = mutableListOf<TextLayoutResult>()
        val node = compose.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()
        compose.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
        return results.single().hasVisualOverflow
    }

    private fun px(dp: Dp): Float = with(compose.density) { dp.toPx() }
}
