package com.shibi.musicplayer

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.ui.InlineVideoPlayer
import com.shibi.musicplayer.ui.VideoSurface
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SeekPreviewPlacementTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theBubbleSitsAboveTheSeekBarInsideThePlayer() {
        val video = VideosScreenshotTest.videos.first { it.id == 2L }
        val state = VideoUiState(
            video = video,
            status = VideoStatus.Paused,
            positionMs = 192_000L,
            durationMs = video.durationMs ?: 0L,
            aspect = InlineVideoBox.displayAspect(video.width ?: 0, video.height ?: 0),
        )
        val engine = FakeVideoEngine()
        compose.setContent {
            MusicPlayerTheme {
                InlineVideoPlayer(
                    state = state,
                    maxHeight = 420.dp,
                    onTogglePlayPause = {},
                    onSeek = {},
                    onClose = {},
                    surface = { VideoSurface(engine) },
                    initialScrubMs = 1_240_000L,
                    modifier = Modifier,
                )
            }
        }
        compose.waitForIdle()
        val seek = ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.seek)
        val bar = compose.onNodeWithContentDescription(seek).getBoundsInRoot()
        val pill = compose.onNodeWithText("20:40").getBoundsInRoot()
        assertTrue("pill $pill must end above the bar $bar", pill.bottom <= bar.top)
        assertTrue("pill $pill must start inside the player", pill.top >= 0.dp && pill.left >= 0.dp)
    }
}
