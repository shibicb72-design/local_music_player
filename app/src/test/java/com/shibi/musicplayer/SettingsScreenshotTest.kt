package com.shibi.musicplayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.playingState
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.shownWith
import com.shibi.musicplayer.ui.ChromeSlot
import com.shibi.musicplayer.ui.ListViewport
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.PlayerScreen
import com.shibi.musicplayer.ui.SectionRoutes
import com.shibi.musicplayer.ui.SettingsScreen
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.TwoPaneCarouselPadding
import com.shibi.musicplayer.ui.TwoPanePlayerBottomPadding
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings unfolded: the list pane beside the player, the carousel under the list as in the
 * app, 800dp tall so the cards down to Send feedback show. Folded Settings is already drawn
 * by CarouselV3ScreenshotTest (`cv3_dark_on`, `cv3_dark_off_restored`).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w900dp-h800dp-xhdpi")
class SettingsScreenshotTest {

    @Test
    fun twoPane() {
        captureRoboImage("build/outputs/roborazzi/settings_two_pane.png") {
            MusicPlayerTheme(darkTheme = true) {
                AppBackgroundHost(AppBackground.Solid("blush").shownWith(true), emptyMap(), forceStatic = true) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                            ListViewport(modifier = Modifier.weight(1f).fillMaxSize()) {
                                SettingsScreen(
                                    darkTheme = true,
                                    onDarkThemeChange = {},
                                    backgroundName = "Blush",
                                    onOpenBackgrounds = {},
                                    contentBottomPadding = SongListBottomPadding,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            ChromeSlot {
                                NavCarousel(
                                    selected = NavSection.Settings,
                                    onSelect = {},
                                    sections = SectionRoutes.sectionsFor(expanded = true),
                                    modifier = Modifier.padding(
                                        top = TwoPaneCarouselPadding,
                                        bottom = TwoPaneCarouselPadding,
                                    ),
                                )
                            }
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
                                .navigationBarsPadding()
                                .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = TwoPanePlayerBottomPadding)
                                .clip(RoundedCornerShape(28.dp)),
                        )
                    }
                }
            }
        }
    }
}
