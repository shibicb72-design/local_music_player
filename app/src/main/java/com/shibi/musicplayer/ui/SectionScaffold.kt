package com.shibi.musicplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp

/**
 * Hosts the floating mini player and the carousel once, over the section
 * content. Content bottom padding is the measured chrome height plus
 * [contentBottomPadding], so the last row can scroll clear of both.
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
    SubcomposeLayout(modifier) { constraints ->
        val chromePlaceable = if (showMiniPlayer || showCarousel) {
            subcompose("chrome") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (showMiniPlayer) miniPlayer()
                    if (showCarousel) carousel()
                }
            }.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        } else {
            null
        }
        val chromeHeight = chromePlaceable?.height ?: 0
        val bottomPadding = contentBottomPadding + chromeHeight.toDp()
        val contentPlaceable = subcompose("content") {
            Column(modifier = Modifier.fillMaxSize()) {
                content(bottomPadding)
            }
        }.first().measure(constraints)
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            contentPlaceable.place(0, 0)
            chromePlaceable?.place(0, height - chromeHeight)
        }
    }
}
