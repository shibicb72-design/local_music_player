package com.shibi.musicplayer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.playingState
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.OpenSourceNotices
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.shownWith
import com.shibi.musicplayer.ui.LicensesScreen
import com.shibi.musicplayer.ui.MiniPlayerCarouselGap
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.NowPlayingBar
import com.shibi.musicplayer.ui.SectionScaffold
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > About > Open-source licences. The user chose blush: Dark theme on
 * paints the dark default, off shows blush; the cards keep their own contrast.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LicensesScreenshotTest {

    @Test
    fun licensesDark() = captureLicenses(darkTheme = true, "licenses_dark.png")

    @Test
    fun licensesPastel() = captureLicenses(darkTheme = false, "licenses_pastel.png")

    @Test
    fun licenceTextsShipAsRawResources() {
        val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val lgpl = resources.openRawResource(R.raw.lgpl_2_1).bufferedReader().use { it.readText() }
        val ofl = resources.openRawResource(R.raw.ofl_1_1).bufferedReader().use { it.readText() }
        assertEquals(
            "GNU LESSER GENERAL PUBLIC LICENSE Version 2.1, February 1999",
            OpenSourceNotices.paragraphs(lgpl).first(),
        )
        assertTrue(ofl.contains("SIL OPEN FONT LICENSE Version 1.1"))
        assertTrue(ofl.contains("Plus Jakarta Sans") && ofl.contains("Noto"))
    }

    /** The version comes from the catalog via resValue, so the notice cannot drift from the dependency. */
    @Test
    fun libVlcVersionIsGeneratedFromTheCatalog() {
        val version = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.libvlc_version)
        assertTrue(version, Regex("""\d+\.\d+\.\d+""").matches(version))
    }

    private fun captureLicenses(darkTheme: Boolean, fileName: String) {
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
                        LicensesScreen(
                            onBack = {},
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
}
