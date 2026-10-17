package com.shibi.musicplayer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.playingState
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.AppFacts
import com.shibi.musicplayer.domain.DeviceFacts
import com.shibi.musicplayer.domain.ErrorLogEntry
import com.shibi.musicplayer.domain.ErrorReport
import com.shibi.musicplayer.domain.MediaFacts
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.shownWith
import com.shibi.musicplayer.ui.ErrorLogContent
import com.shibi.musicplayer.ui.MiniPlayerCarouselGap
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.NowPlayingBar
import com.shibi.musicplayer.ui.SectionScaffold
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import com.shibi.musicplayer.video.VideoPlaybackException
import java.io.FileNotFoundException
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > Debugging / Error log. Each entry is a card with its own contrast; the only
 * button is "Copy all". The user chose blush: Dark theme on paints the dark default.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ErrorLogScreenshotTest {

    @Test
    fun dark() = capture(darkTheme = true, entries = entries, fileName = "error_log_dark.png")

    @Test
    fun pastel() = capture(darkTheme = false, entries = entries, fileName = "error_log_pastel.png", copied = true)

    @Test
    fun empty() = capture(darkTheme = false, entries = emptyList(), fileName = "error_log_empty.png")

    private fun capture(
        darkTheme: Boolean,
        entries: List<ErrorLogEntry>,
        fileName: String,
        copied: Boolean = false,
    ) {
        val chosen = AppBackground.Solid("blush")
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme(darkTheme = darkTheme) {
                AppBackgroundHost(chosen.shownWith(darkTheme), emptyMap(), forceStatic = true) {
                    SectionScaffold(
                        modifier = Modifier.fillMaxSize(),
                        showMiniPlayer = true,
                        showCarousel = true,
                        contentBottomPadding = SongListBottomPadding,
                        miniPlayer = {
                            NowPlayingBar(
                                state = playingState,
                                onOpen = {},
                                onTogglePlayPause = {},
                                route = RoutePresentation.phone("This phone"),
                                padForNavigationBars = false,
                                bottomPadding = MiniPlayerCarouselGap,
                            )
                        },
                        carousel = { NavCarousel(selected = NavSection.Settings, onSelect = {}) },
                    ) { bottom ->
                        ErrorLogContent(
                            entries = entries,
                            copied = copied,
                            onCopyAll = {},
                            onBack = {},
                            contentBottomPadding = bottom,
                            zone = Zone,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val Zone: ZoneId = ZoneId.of("America/New_York")
        val device = DeviceFacts("samsung", "SM-F966B", "16", 36, listOf("arm64-v8a"), 4096L, "unfolded")
        val app = AppFacts("Theatrum", "1.0", 1L, "3.7.7")

        fun entry(
            time: Long,
            step: String,
            error: Throwable?,
            shown: String?,
            media: MediaFacts?,
        ) = ErrorLogEntry.of(
            ErrorReport(time, step, shown, ErrorReport.causesOf(error), media, device, app),
            Zone,
        )

        val entries = listOf(
            entry(
                time = 1_792_261_925_000L,
                step = "Load subtitle file for Concert encore.mkv (fullscreen)",
                error = Exception("libVLC added no track for the file within 4000 ms"),
                shown = "Couldn't load that subtitle file.",
                media = MediaFacts(fileName = "Concert encore.en.idx", mimeType = "application/octet-stream"),
            ),
            entry(
                time = 1_792_261_402_000L,
                step = "Play video (video pane): libVLC error",
                error = VideoPlaybackException("libVLC reported EncounteredError while playing"),
                shown = "This video can't be played.",
                media = MediaFacts(fileName = "Old camcorder.wmv", mimeType = "video/x-ms-wmv"),
            ),
            entry(
                time = 1_792_258_800_000L,
                step = "Open video (inline box)",
                error = FileNotFoundException(
                    "open failed: ENOENT (No such file or directory) /storage/emulated/0/Movies/Beach day.mp4",
                ),
                shown = "Couldn't open this video. It may have been moved or deleted.",
                media = MediaFacts(fileName = "Beach day.mp4", mimeType = "video/mp4"),
            ),
            entry(
                time = 1_792_250_000_000L,
                step = "Scan videos (MediaStore query, access Full)",
                error = SecurityException("Permission Denial: reading com.android.providers.media.MediaProvider"),
                shown = "Could not read videos from this device.",
                media = null,
            ),
        )
    }
}
