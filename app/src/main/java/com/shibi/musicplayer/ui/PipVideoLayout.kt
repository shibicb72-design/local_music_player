package com.shibi.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

/**
 * The whole layout while in picture-in-picture: the window shows only the video.
 * Android draws its own PiP controls (the RemoteActions), so no controls, text, chips or carousel.
 */
@Composable
fun PipVideoLayout(surface: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("pip_layout"),
        contentAlignment = Alignment.Center,
    ) {
        surface()
    }
}
