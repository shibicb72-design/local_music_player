package com.shibi.musicplayer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The two-pane right side: the video pane on the Videos tab, Now Playing elsewhere, crossfading; instant with Remove animations on. */
@Composable
internal fun RightPane(
    showVideo: Boolean,
    video: @Composable () -> Unit,
    nowPlaying: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduce = rememberReduceMotion()
    AnimatedContent(
        targetState = showVideo,
        modifier = modifier,
        transitionSpec = {
            if (reduce) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(tween(220)) togetherWith fadeOut(tween(160))
            }
        },
        label = "rightPane",
    ) { show ->
        if (show) video() else nowPlaying()
    }
}
