package com.shibi.musicplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.AudioOutput
import com.shibi.musicplayer.domain.OutputKind
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.ChromeSlot
import com.shibi.musicplayer.ui.DevicesScreen
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.ListViewport
import com.shibi.musicplayer.ui.MiniPlayerCarouselGap
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavCarouselContent
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.NowPlayingBar
import com.shibi.musicplayer.ui.PlayerScreen
import com.shibi.musicplayer.ui.SectionScaffold
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Carousel v2 review set: folded and two-pane, on the dark default and on a
 * pastel. The same file names were recorded before and after the redesign.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CarouselV2ScreenshotTest {

    @Test
    fun foldedDark() = captureFolded(AppBackground.Default, NavSection.Songs, "cv2_folded_dark.png")

    @Test
    fun foldedBlush() = captureFolded(AppBackground.Solid("blush"), NavSection.Songs, "cv2_folded_blush.png")

    @Test
    fun foldedDarkDevices() =
        captureFolded(AppBackground.Default, NavSection.Devices, "cv2_folded_dark_devices.png")

    @Test
    fun foldedBlushFavorites() =
        captureFolded(AppBackground.Solid("blush"), NavSection.Favorites, "cv2_folded_blush_favorites.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun twoPaneDark() = captureTwoPane(AppBackground.Default, "cv2_two_pane_dark.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun twoPaneSky() = captureTwoPane(AppBackground.Solid("sky"), "cv2_two_pane_sky.png")

    @Test
    fun filmstripDark() = captureFilmstrip(AppBackground.Default, "cv2_filmstrip_dark.png")

    @Test
    fun filmstripBlush() = captureFilmstrip(AppBackground.Solid("blush"), "cv2_filmstrip_blush.png")

    @Test
    fun devicesEmptyDark() = captureDevices(AppBackground.Default, "cv2_devices_empty_dark.png")

    @Test
    fun devicesEmptyBlush() = captureDevices(AppBackground.Solid("blush"), "cv2_devices_empty_blush.png")

    /** Five poses of one spin from Songs to Playlists: lifted lens mid-spin, settled at the end. */
    private fun captureFilmstrip(background: AppBackground, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        listOf(
                            0f to 0f,
                            0.2f to 1f,
                            0.45f to 1f,
                            0.7f to 1f,
                            1f to 0f,
                        ).forEach { (turn, lift) ->
                            NavCarouselContent(
                                rotation = { turn },
                                energy = { lift },
                                selectedIndex = Math.round(turn),
                                blurBack = true,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun captureDevices(background: AppBackground, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    SectionScaffold(
                        modifier = Modifier.fillMaxSize(),
                        showMiniPlayer = true,
                        showCarousel = true,
                        contentBottomPadding = SongListBottomPadding,
                        miniPlayer = { MiniBar() },
                        carousel = { NavCarousel(selected = NavSection.Devices, onSelect = {}) },
                    ) { bottom ->
                        DevicesScreen(
                            outputs = listOf(AudioOutput(1, OutputKind.PHONE_SPEAKER, "This phone")),
                            selectedId = null,
                            onSelect = {},
                            contentBottomPadding = bottom,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    private fun captureFolded(background: AppBackground, selected: NavSection, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    SectionScaffold(
                        modifier = Modifier.fillMaxSize(),
                        showMiniPlayer = true,
                        showCarousel = true,
                        contentBottomPadding = SongListBottomPadding,
                        miniPlayer = { MiniBar() },
                        carousel = { NavCarousel(selected = selected, onSelect = {}) },
                    ) { bottom ->
                        Songs(bottom)
                    }
                }
            }
        }
    }

    private fun captureTwoPane(background: AppBackground, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                            ListViewport(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                                Songs(SongListBottomPadding)
                            }
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
                                includeNavigationBars = false,
                                modifier = Modifier
                                    .weight(0.58f)
                                    .fillMaxHeight()
                                    .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = SongListBottomPadding)
                                    .clip(RoundedCornerShape(28.dp)),
                            )
                        }
                        ChromeSlot {
                            MiniBar()
                            NavCarousel(selected = NavSection.Playlists, onSelect = {})
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MiniBar() {
        NowPlayingBar(
            state = playingState,
            onOpen = {},
            onTogglePlayPause = {},
            route = RoutePresentation.phone("This phone"),
            padForNavigationBars = false,
            bottomPadding = MiniPlayerCarouselGap,
        )
    }

    @Composable
    private fun Songs(bottom: Dp) {
        LibraryContent(
            tracks = tracks,
            nowPlaying = playingState,
            showMiniPlayer = false,
            onTrackSelected = { _, _ -> },
            onOpenPlayer = {},
            onTogglePlayPause = {},
            favoriteIds = setOf(3L),
            contentBottomPadding = bottom,
            listState = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
        )
    }

    internal companion object {
        val tracks = listOf(
            Track(1L, "Midnight Drive", "Neon Cascade", "Night Highway", 215_000L, "content://tracks/1", null),
            Track(2L, "Paper Planes", "Ella Voss", "Paper Planes", 198_000L, "content://tracks/2", null),
            Track(3L, "Golden Hour", "Marina Cole", "Golden Hour", 224_000L, "content://tracks/3", null),
            Track(4L, "Saltwater", "Northbound", "Tidal", 187_000L, "content://tracks/4", null),
            Track(5L, "Ember", "Rilo Ash", "Ember", 245_000L, "content://tracks/5", null),
            Track(6L, "Static Bloom", "Hollow Coast", "Static Bloom", 202_000L, "content://tracks/6", null),
            Track(7L, "Slow Tide", "June & The Current", "Slow Tide", 176_000L, "content://tracks/7", null),
            Track(9L, "Glass Harbor", "Mono Lake", "Glass Harbor", 233_000L, "content://tracks/9", null),
            Track(10L, "Northern Lights", "Avery Lune", "Aurora", 251_000L, "content://tracks/10", null),
            Track(11L, "Velvet Rain", "The Quiet Hours", "Velvet", 190_000L, "content://tracks/11", null),
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
    }
}
