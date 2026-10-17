package com.shibi.musicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.domain.VideoFormat
import com.shibi.musicplayer.domain.VideoPaneLayout
import com.shibi.musicplayer.ui.background.ContrastSurface
import com.shibi.musicplayer.video.VideoUiState

/** Test tag of the two-pane video pane. */
internal const val VideoPaneTag = "video_pane"

/**
 * The two-pane right pane while a video is active, in place of Now Playing. Black in every
 * theme and on every pastel: the picture fits the pane width with the same tap-to-show
 * controls as the compact box, and the title and file details sit under it when there is
 * room. The caller clips it to the Now Playing card's shape.
 */
@Composable
internal fun VideoPane(
    state: VideoUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    surface: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onFullscreen: () -> Unit = {},
    initialControlsVisible: Boolean = false,
) {
    ContrastSurface(
        color = Color.Black,
        modifier = modifier
            .fillMaxSize()
            .testTag(VideoPaneTag),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = VideoPaneLayout.of(maxWidth.value, maxHeight.value, state.aspect)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                VideoPlayerBox(
                    state = state,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeek = onSeek,
                    onClose = onClose,
                    surface = surface,
                    onFullscreen = onFullscreen,
                    initialControlsVisible = initialControlsVisible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(layout.videoHeight.dp),
                )
                if (layout.showDetails) {
                    VideoDetails(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(VideoPaneLayout.DetailsHeight.dp)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

/** Title, then `1:02:03 · 4.8 GB · 4K · Movies`; white on the black pane, never from the backdrop. */
@Composable
private fun VideoDetails(state: VideoUiState, modifier: Modifier = Modifier) {
    val video = state.video ?: return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TrackTitle(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = OnVideo,
            modifier = Modifier.fillMaxWidth(),
        )
        val details = videoDetails(state.durationMs, videoSubtitle(video))
        if (details.isNotEmpty()) {
            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium,
                color = OnVideoMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The length (when known) in front of the row subtitle; nothing missing is dashed. */
internal fun videoDetails(durationMs: Long, subtitle: String): String = listOfNotNull(
    VideoFormat.duration(durationMs).takeIf { it != VideoFormat.MISSING },
    subtitle.takeIf { it.isNotEmpty() },
).joinToString(" · ")
