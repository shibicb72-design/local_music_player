package com.shibi.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome

/** Fade at the bottom of the list viewport so rows dissolve before the chrome. */
internal val ListEdgeFade = 32.dp

/**
 * Hosts the mini player and the carousel in a non-overlapping slot under the
 * section content. The list viewport ends above that slot; a short fade at its
 * bottom edge dissolves rows into the page background.
 */
@Composable
internal fun SectionScaffold(
    showMiniPlayer: Boolean,
    showCarousel: Boolean,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable () -> Unit = {},
    carousel: @Composable () -> Unit = {},
    content: @Composable (bottomPadding: Dp) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        ListViewport(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content(contentBottomPadding)
        }
        if (showMiniPlayer || showCarousel) {
            ChromeSlot {
                if (showMiniPlayer) miniPlayer()
                if (showCarousel) carousel()
            }
        }
    }
}

@Composable
internal fun ListViewport(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val fill = LocalBackgroundChrome.current.pageFill
    Box(modifier) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(ListEdgeFade)
                .background(
                    Brush.verticalGradient(
                        0f to fill.copy(alpha = 0f),
                        1f to fill,
                    ),
                ),
        )
    }
}

@Composable
internal fun ChromeSlot(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LocalBackgroundChrome.current.pageFill),
        content = content,
    )
}
