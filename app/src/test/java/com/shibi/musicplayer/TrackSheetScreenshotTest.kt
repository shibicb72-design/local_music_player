package com.shibi.musicplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.InlineVideoBox
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.shownWith
import com.shibi.musicplayer.ui.InlineVideoPlayer
import com.shibi.musicplayer.ui.OutputScrim
import com.shibi.musicplayer.ui.TrackSheetActions
import com.shibi.musicplayer.ui.TrackSheetContent
import com.shibi.musicplayer.ui.VideoSurface
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.background.ContrastSurface
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import com.shibi.musicplayer.video.EngineOptions
import com.shibi.musicplayer.video.MediaTrackType
import com.shibi.musicplayer.video.RawMediaTrack
import com.shibi.musicplayer.video.RawTrackDescription
import com.shibi.musicplayer.video.SubtitleTextSize
import com.shibi.musicplayer.video.TrackMessage
import com.shibi.musicplayer.video.TrackSheetState
import com.shibi.musicplayer.video.TrackSnapshot
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * V7: the tracks and subtitles sheet over the folded inline box, behind the 55% scrim. The
 * sheet takes its colour from the backdrop's sheet tone and every text colour from that
 * container (`ContrastSurface`), so dark and pastel read alike. Delays are steppers; there
 * is no slider of any kind.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TrackSheetScreenshotTest {

    @Test
    fun dark() = capture(
        darkTheme = true,
        sheet = TrackSheetState.of(movie, EngineOptions()),
        fileName = "track_sheet_dark.png",
    )

    /** Blush with Dark theme off; the short message after a failed audio file. */
    @Test
    fun pastel() = capture(
        darkTheme = false,
        sheet = TrackSheetState.of(
            movie.copy(selectedSubtitleId = 4, audioDelayMs = -50L),
            EngineOptions(hardwareDecoding = false),
            message = TrackMessage.AudioFailed,
        ),
        fileName = "track_sheet_pastel.png",
    )

    /** A loaded `.srt`, selected, shown by its file name, with a +0.3 s delay and large text. */
    @Test
    fun withExternalSubtitle() = capture(
        darkTheme = false,
        background = AppBackground.Solid("sky"),
        sheet = TrackSheetState.of(
            movie.copy(
                subtitles = movie.subtitles + RawTrackDescription(9, "Track 3"),
                selectedSubtitleId = 9,
                subtitleDelayMs = 300L,
                externalNames = mapOf(9 to "Concert encore.en.srt"),
            ),
            EngineOptions(subtitleTextSize = SubtitleTextSize.Large),
        ),
        fileName = "track_sheet_with_external_sub.png",
    )

    private fun capture(
        darkTheme: Boolean,
        sheet: TrackSheetState,
        fileName: String,
        background: AppBackground = AppBackground.Solid("blush"),
    ) {
        val engine = FakeVideoEngine(frameAspect = InlineVideoBox.DefaultAspect)
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme(darkTheme = darkTheme) {
                AppBackgroundHost(background.shownWith(darkTheme), emptyMap(), forceStatic = true) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val tallest = maxHeight - 48.dp
                        InlineVideoPlayer(
                            state = playing,
                            maxHeight = maxHeight,
                            onTogglePlayPause = {},
                            onSeek = {},
                            onClose = {},
                            surface = { VideoSurface(engine) },
                            onOpenTracks = {},
                            holdControls = true,
                            initialControlsVisible = true,
                            modifier = Modifier.statusBarsPadding(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(OutputScrim),
                        )
                        val sheetColor = LocalBackgroundChrome.current.sheet
                        ContrastSurface(
                            color = sheetColor,
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .heightIn(max = tallest),
                        ) {
                            TrackSheetContent(
                                state = sheet,
                                actions = TrackSheetActions(),
                                onPickAudio = {},
                                onPickSubtitle = {},
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val concert: Video = VideosScreenshotTest.videos.first { it.id == 2L }

        val playing = VideoUiState(
            video = concert,
            status = VideoStatus.Playing,
            positionMs = 192_000L,
            durationMs = concert.durationMs ?: 0L,
            aspect = InlineVideoBox.displayAspect(concert.width ?: 0, concert.height ?: 0),
        )

        val movie = TrackSnapshot(
            audio = listOf(
                RawTrackDescription(-1, "Disable"),
                RawTrackDescription(1, "Track 1 - [English]"),
                RawTrackDescription(2, "Track 2 - [Japanese]"),
                RawTrackDescription(3, "Director's commentary - [English]"),
            ),
            subtitles = listOf(
                RawTrackDescription(-1, "Disable"),
                RawTrackDescription(4, "Track 1 - [English]"),
                RawTrackDescription(5, null),
            ),
            mediaTracks = listOf(
                RawMediaTrack(1, language = "eng", codec = "a52 ", type = MediaTrackType.Audio),
                RawMediaTrack(2, language = "jpn", codec = "mp4a", type = MediaTrackType.Audio),
                RawMediaTrack(3, language = "eng", codec = "mp4a", type = MediaTrackType.Audio),
                RawMediaTrack(4, language = "eng", codec = "ssa ", type = MediaTrackType.Text),
                RawMediaTrack(5, language = "fre", codec = "subt", type = MediaTrackType.Text),
            ),
            selectedAudioId = 1,
        )
    }
}
