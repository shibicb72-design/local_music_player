package com.shibi.musicplayer

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.domain.AudioOutput
import com.shibi.musicplayer.domain.OutputKind
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.OutputPickerContent
import com.shibi.musicplayer.ui.PlayerScreen
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @Test
    fun list() {
        captureRoboImage("build/outputs/roborazzi/list.png") {
            MusicPlayerTheme {
                LibraryContent(
                    tracks = tracks,
                    nowPlaying = playingState,
                    showMiniPlayer = true,
                    onTrackSelected = { _, _ -> },
                    onOpenPlayer = {},
                    onTogglePlayPause = {},
                )
            }
        }
    }

    @Test
    fun nowPlayingNoArt() {
        captureRoboImage("build/outputs/roborazzi/now_playing_noart.png") {
            MusicPlayerTheme {
                PlayerScreen(
                    state = tamilState,
                    onBack = {},
                    onTogglePlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onSeek = {},
                    selectedOutputId = null,
                    onSelectOutput = {},
                )
            }
        }
    }

    @Test
    fun nowPlayingEnglish() {
        captureRoboImage("build/outputs/roborazzi/now_playing_english.png") {
            MusicPlayerTheme {
                PlayerScreen(
                    state = englishState,
                    onBack = {},
                    onTogglePlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onSeek = {},
                    selectedOutputId = null,
                    onSelectOutput = {},
                )
            }
        }
    }

    @Test
    fun miniPlayer() {
        captureRoboImage("build/outputs/roborazzi/mini_player.png") {
            MusicPlayerTheme {
                LibraryContent(
                    tracks = emptyList(),
                    nowPlaying = playingState,
                    showMiniPlayer = true,
                    onTrackSelected = { _, _ -> },
                    onOpenPlayer = {},
                    onTogglePlayPause = {},
                )
            }
        }
    }

    @Test
    fun deviceSheet() {
        captureRoboImage("build/outputs/roborazzi/device_sheet.png") {
            MusicPlayerTheme {
                OutputPickerContent(
                    outputs = outputs,
                    selectedId = MAJOR_V_ID,
                    onSelect = {},
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun twoPane() {
        captureRoboImage("build/outputs/roborazzi/two_pane.png") {
            MusicPlayerTheme {
                Row(modifier = Modifier.fillMaxSize()) {
                    LibraryContent(
                        tracks = tracks,
                        nowPlaying = playingState,
                        showMiniPlayer = false,
                        onTrackSelected = { _, _ -> },
                        onOpenPlayer = {},
                        onTogglePlayPause = {},
                        modifier = Modifier.weight(0.45f),
                    )
                    PlayerScreen(
                        state = playingState,
                        onBack = {},
                        onTogglePlayPause = {},
                        onNext = {},
                        onPrevious = {},
                        onSeek = {},
                        selectedOutputId = null,
                        onSelectOutput = {},
                        showBack = false,
                        modifier = Modifier.weight(0.55f),
                    )
                }
            }
        }
    }

    private companion object {
        const val MAJOR_V_ID = 2

        val tamilTitle =
            "\u0B8E\u0BA9\u0BCD\u0BA9\u0BC8 \u0B95\u0BCA\u0B9E\u0BCD\u0B9A\u0BAE\u0BCD.mp3"

        val tracks = listOf(
            Track(1L, "Midnight Drive", "Neon Cascade", "Night Highway", 215_000L, "content://tracks/1", null),
            Track(2L, "Paper Planes", "Ella Voss", "Paper Planes", 198_000L, "content://tracks/2", null),
            Track(3L, "Golden Hour", "Marina Cole", "Golden Hour", 224_000L, "content://tracks/3", null),
            Track(4L, "Saltwater", "Northbound", "Tidal", 187_000L, "content://tracks/4", null),
            Track(5L, "Ember", "Rilo Ash", "Ember", 245_000L, "content://tracks/5", null),
            Track(6L, "Static Bloom", "Hollow Coast", "Static Bloom", 202_000L, "content://tracks/6", null),
            Track(7L, "Slow Tide", "June & The Current", "Slow Tide", 176_000L, "content://tracks/7", null),
            Track(8L, tamilTitle, "<unknown>", "Unknown album", 215_000L, "content://tracks/8", null),
        )

        val playingState = PlayerUiState(
            currentMediaId = "2",
            title = "Paper Planes",
            artist = "Ella Voss",
            isPlaying = true,
            positionMs = 62_000L,
            durationMs = 198_000L,
            hasMedia = true,
            hasNext = true,
            hasPrevious = true,
        )

        val tamilState = PlayerUiState(
            currentMediaId = "8",
            title = tamilTitle,
            artist = "<unknown>",
            isPlaying = true,
            positionMs = 62_000L,
            durationMs = 215_000L,
            hasMedia = true,
            hasNext = true,
            hasPrevious = true,
        )

        val englishState = PlayerUiState(
            currentMediaId = "1",
            title = "Midnight Drive",
            artist = "Neon Cascade",
            isPlaying = false,
            positionMs = 42_000L,
            durationMs = 215_000L,
            hasMedia = true,
            hasNext = true,
            hasPrevious = true,
        )

        val outputs = listOf(
            AudioOutput(1, OutputKind.PHONE_SPEAKER, "This phone"),
            AudioOutput(MAJOR_V_ID, OutputKind.BLUETOOTH, "MAJOR V"),
            AudioOutput(3, OutputKind.USB, "USB-C headphones"),
        )
    }
}
