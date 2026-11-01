package com.shibi.musicplayer

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.HistoryType
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.HistoryRow
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.RecentHistoryScreen
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A Songs row's text with real font metrics (native graphics): a long artist gives way so the
 * length always shows in full, and larger fonts grow the row instead of clipping its text; a
 * Recent row's badge and time also stay whole at 2x.
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

    private val entry = HistoryRow(
        1L, HistoryType.Song, 1L, "A title far too long to sit beside the badge and the time",
        "Artist One", null, null, 1_760_000_000_000L,
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

    @Test
    @Config(qualifiers = NARROW)
    fun recentRowsGrowWithLargerFontsAndKeepTheBadgeAndTimeWhole() {
        recent(fontScale = 2f)
        // TrackTitle shortens a title that does not fit, so the row is found by its artist.
        val row = rowOf("Artist One")
        assertTrue("row grew past 60 dp: ${row.height} px", row.height > px(60.dp) + 1f)
        val time = DateFormat.getTimeFormat(ApplicationProvider.getApplicationContext<Context>())
            .format(Date(entry.playedAt))
        // Only TrackTitle writes its "…" into the text, so this is the title, and it gave way.
        val title = bounds(hasText("…", substring = true))
        val badge = bounds(hasText("Song"))
        val clock = bounds(hasText(time))
        for ((name, part) in listOf("title" to title, "badge" to badge, "time" to clock)) {
            assertTrue("$name inside the row", part.top >= row.top - 0.5f && part.bottom <= row.bottom + 0.5f)
        }
        assertTrue("the title gives way to the badge", title.right <= badge.left + 0.5f)
        assertTrue("badge before the time", badge.right <= clock.left + 0.5f)
        assertTrue("badge in full", whole(hasText("Song")))
        assertTrue("time in full", whole(hasText(time)))
        val listEnd = compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode().boundsInRoot.right
        assertEquals("time to the list's end", px(16.dp), listEnd - clock.right, 0.5f)
    }

    private fun recent(fontScale: Float) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                MusicPlayerTheme {
                    RecentHistoryScreen(
                        rows = listOf(entry),
                        onPlay = {},
                        onClearHistory = {},
                        contentBottomPadding = 0.dp,
                        nowMs = entry.playedAt + 60_000L,
                    )
                }
            }
        }
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

    private fun layoutOf(matcher: SemanticsMatcher): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = compose.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()
        compose.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
        return results.single()
    }

    private fun overflows(matcher: SemanticsMatcher): Boolean = layoutOf(matcher).hasVisualOverflow

    /**
     * The text sits on one line in full: not wrapped, ellipsized or cut. A text that is only as
     * wide as its words reports a width overflow even when it fits, so [overflows] cannot say.
     */
    private fun whole(matcher: SemanticsMatcher): Boolean = with(layoutOf(matcher)) {
        lineCount == 1 && !isLineEllipsized(0) && getLineEnd(0) == layoutInput.text.length &&
            getLineRight(0) <= size.width + 0.5f
    }

    private fun px(dp: Dp): Float = with(compose.density) { dp.toPx() }
}
