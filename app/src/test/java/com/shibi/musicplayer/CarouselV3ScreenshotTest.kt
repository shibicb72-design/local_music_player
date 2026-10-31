package com.shibi.musicplayer

import androidx.compose.foundation.layout.Box
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
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.playingState
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.songs
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.BackgroundTab
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.shownWith
import com.shibi.musicplayer.ui.ChromeSlot
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.ListViewport
import com.shibi.musicplayer.ui.MiniPlayerCarouselGap
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.NowPlayingBar
import com.shibi.musicplayer.ui.PlayerScreen
import com.shibi.musicplayer.ui.SectionRoutes
import com.shibi.musicplayer.ui.SectionScaffold
import com.shibi.musicplayer.ui.SettingsScreen
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.background.BackgroundPickerContent
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Carousel v3 review set: the carousel stays under compact Now Playing, the
 * expanded ring drops Now Playing, and Dark theme covers then restores a pastel.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CarouselV3ScreenshotTest {

    @Test
    fun compactNowPlayingDark() = captureCompactNowPlaying(AppBackground.Default, "cv3_compact_now_playing_dark.png")

    @Test
    fun compactNowPlayingBlush() =
        captureCompactNowPlaying(AppBackground.Solid("blush"), "cv3_compact_now_playing_blush.png")

    /** Cover-screen height: the player still ends above the carousel. */
    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun compactNowPlayingShortDark() =
        captureCompactNowPlaying(AppBackground.Default, "cv3_compact_now_playing_short_dark.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun expandedDark() = captureExpanded(AppBackground.Default, "cv3_expanded_dark.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun expandedSky() = captureExpanded(AppBackground.Solid("sky"), "cv3_expanded_sky.png")

    @Test
    fun darkOnOverPastel() = captureSettings(darkTheme = true, "cv3_dark_on.png")

    @Test
    fun darkOffRestoresPastel() = captureSettings(darkTheme = false, "cv3_dark_off_restored.png")

    @Test
    fun darkOnBackgroundsHint() {
        val chosen = AppBackground.Solid("blush")
        captureRoboImage("build/outputs/roborazzi/cv3_dark_on_backgrounds.png") {
            MusicPlayerTheme(darkTheme = true) {
                AppBackgroundHost(chosen.shownWith(darkTheme = true), emptyMap(), forceStatic = true) {
                    BackgroundPickerContent(
                        tab = BackgroundTab.SOLID,
                        onTabChange = {},
                        selected = chosen,
                        customColors = emptyMap(),
                        onSelect = {},
                        onBack = {},
                        forceStatic = true,
                        darkThemeOn = true,
                    )
                }
            }
        }
    }

    private fun captureCompactNowPlaying(background: AppBackground, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    SectionScaffold(
                        modifier = Modifier.fillMaxSize(),
                        showMiniPlayer = false,
                        showCarousel = true,
                        contentBottomPadding = SongListBottomPadding,
                        carousel = {
                            NavCarousel(
                                selected = NavSection.NowPlaying,
                                onSelect = {},
                                sections = SectionRoutes.sectionsFor(expanded = false),
                            )
                        },
                    ) { bottom ->
                        PlayerScreen(
                            state = playingState,
                            onBack = {},
                            onTogglePlayPause = {},
                            onNext = {},
                            onPrevious = {},
                            onSeek = {},
                            selectedOutputId = null,
                            onSelectOutput = {},
                            includeNavigationBars = false,
                            contentBottomPadding = bottom,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    private fun captureExpanded(background: AppBackground, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                            ListViewport(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                                Songs(SongListBottomPadding)
                            }
                            Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
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
                                        .fillMaxHeight()
                                        .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = SongListBottomPadding)
                                        .clip(RoundedCornerShape(28.dp)),
                                )
                            }
                        }
                        ChromeSlot {
                            MiniBar()
                            NavCarousel(
                                selected = NavSection.Songs,
                                onSelect = {},
                                sections = SectionRoutes.sectionsFor(expanded = true),
                            )
                        }
                    }
                }
            }
        }
    }

    /** The user chose blush; Dark theme on paints the dark default, off brings blush back. */
    private fun captureSettings(darkTheme: Boolean, fileName: String) {
        val chosen = AppBackground.Solid("blush")
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme(darkTheme = darkTheme) {
                AppBackgroundHost(chosen.shownWith(darkTheme), emptyMap(), forceStatic = true) {
                    SectionScaffold(
                        modifier = Modifier.fillMaxSize(),
                        showMiniPlayer = true,
                        showCarousel = true,
                        contentBottomPadding = SongListBottomPadding,
                        miniPlayer = { MiniBar() },
                        carousel = { NavCarousel(selected = NavSection.Settings, onSelect = {}) },
                    ) { bottom ->
                        SettingsScreen(
                            darkTheme = darkTheme,
                            onDarkThemeChange = {},
                            backgroundName = "Blush",
                            onOpenBackgrounds = {},
                            contentBottomPadding = bottom,
                            modifier = Modifier.fillMaxSize(),
                        )
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
            tracks = songs.items,
            sorted = songs,
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
}
