package com.shibi.musicplayer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.playingState
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.MiniPlayerCarouselGap
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.NowPlayingBar
import com.shibi.musicplayer.ui.SectionRoutes
import com.shibi.musicplayer.ui.SectionScaffold
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Songs and Videos lists at six phone sizes ([DeviceProfiles]), for the width bug seen on a
 * Galaxy S24 Ultra: Songs rows stopped short of the right edge while Videos rows filled it.
 * These are Robolectric renders at the profile's width, height and density, not device or
 * emulator captures. Both lists sit on the blush backdrop, where every row draws its
 * translucent fill, so a row's width shows. The compact chrome is the app's: the mini player
 * and the 9-tile carousel. Only shipped APIs are used, so the same file renders an older build.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DeviceWidthScreenshotTest {

    @Test
    @Config(qualifiers = S24_ULTRA)
    fun s24UltraSongs() = songs("s24ultra")

    @Test
    @Config(qualifiers = S24_ULTRA)
    fun s24UltraVideos() = videos("s24ultra")

    @Test
    @Config(qualifiers = S24_ULTRA_SMALL)
    fun s24UltraSmallSongs() = songs("s24ultra_small")

    @Test
    @Config(qualifiers = S24_ULTRA_SMALL)
    fun s24UltraSmallVideos() = videos("s24ultra_small")

    @Test
    @Config(qualifiers = S25)
    fun s25Songs() = songs("s25")

    @Test
    @Config(qualifiers = S25)
    fun s25Videos() = videos("s25")

    @Test
    @Config(qualifiers = S25_ULTRA)
    fun s25UltraSongs() = songs("s25ultra")

    @Test
    @Config(qualifiers = S25_ULTRA)
    fun s25UltraVideos() = videos("s25ultra")

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSongs() = songs("narrow")

    @Test
    @Config(qualifiers = NARROW)
    fun narrowVideos() = videos("narrow")

    @Test
    @Config(qualifiers = WIDE412)
    fun wide412Songs() = songs("wide412")

    @Test
    @Config(qualifiers = WIDE412)
    fun wide412Videos() = videos("wide412")

    /** The Songs tab as shipped: sorted, so with its fast scroller; one song playing, one liked. */
    private fun songs(profile: String) = capture("devices_${profile}_songs.png", NavSection.Songs) { bottom ->
        val shipped = remember { shippedSongs(tracks) }
        LibraryContent(
            tracks = shipped.items,
            sorted = shipped,
            nowPlaying = playingState,
            showMiniPlayer = false,
            onTrackSelected = { _, _ -> },
            onOpenPlayer = {},
            onTogglePlayPause = {},
            favoriteIds = setOf(5L),
            contentBottomPadding = bottom,
            modifier = Modifier.fillMaxSize(),
        )
    }

    /** Inspection mode only around the list, so the preview handler feeds the thumbnails. */
    @OptIn(ExperimentalCoilApi::class)
    private fun videos(profile: String) = capture("devices_${profile}_videos.png", NavSection.Videos) { bottom ->
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
        ) {
            VideosContent(
                state = VideosScreenshotTest.full,
                onVideoClick = {},
                onRequestAccess = {},
                onOpenSettings = {},
                contentBottomPadding = bottom,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private fun capture(fileName: String, section: NavSection, list: @Composable (bottom: Dp) -> Unit) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Solid("blush"), emptyMap(), forceStatic = true) {
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
                        carousel = {
                            NavCarousel(
                                selected = section,
                                onSelect = {},
                                sections = SectionRoutes.sectionsFor(expanded = false),
                            )
                        },
                    ) { bottom ->
                        list(bottom)
                    }
                }
            }
        }
    }

    private companion object {
        /** Paper Planes (id 2) is the playing song in [playingState]. */
        val tracks = listOf(
            Track(1L, "Afterglow", "Mira Sol", "Coastlines", 214_000L, "content://tracks/1", null),
            Track(2L, "Paper Planes", "Ella Voss", "Night Drive", 198_000L, "content://tracks/2", null),
            Track(3L, "Blue Hour", "The Lanterns", "Harbour Lights", 247_000L, "content://tracks/3", null),
            Track(4L, "City of Glass", "Northbound", "Signals", 189_000L, "content://tracks/4", null),
            Track(5L, "Daybreak", "Kai Rivers", "Tidal", 231_000L, "content://tracks/5", null),
            Track(6L, "Echo Park", "Luma", "Summer Tapes", 205_000L, "content://tracks/6", null),
            Track(7L, "Fireflies", "June Avenue", "Small Hours", 178_000L, "content://tracks/7", null),
            Track(8L, "Golden Line", "Mira Sol", "Coastlines", 262_000L, "content://tracks/8", null),
            Track(9L, "Highway Hum", "The Lanterns", "Harbour Lights", 223_000L, "content://tracks/9", null),
            Track(10L, "Into the Static", "Northbound", "Signals", 301_000L, "content://tracks/10", null),
            Track(11L, "Juniper", "Ella Voss", "Night Drive", 196_000L, "content://tracks/11", null),
            Track(12L, "Kites", "Kai Rivers", "Tidal", 184_000L, "content://tracks/12", null),
        )
    }
}
