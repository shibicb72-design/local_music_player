package com.shibi.musicplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.playingState
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.MusicCatalog
import com.shibi.musicplayer.domain.MusicView
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.UnknownNames
import com.shibi.musicplayer.library.OpenArtist
import com.shibi.musicplayer.playback.PlayerUiState
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
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import java.text.Collator
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Artists list and an artist page (Daft Punk: own albums, an Appears on card and the songs),
 * compact and two-pane; the chip row at 2x font; the artist page on the light cream background.
 * A fake catalog; one of Daft Punk's songs is playing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ArtistsScreenshotTest {

    @Test
    fun artistListCompact() = compact(null, "artists_compact.png")

    @Test
    fun artistPageCompact() = compact(OpenArtist("daft punk"), "artist_page_compact.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun artistListTwoPane() = twoPane(null, "artists_two_pane.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun artistPageTwoPane() = twoPane(OpenArtist("daft punk"), "artist_page_two_pane.png")

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
    fun artistListCompactBigFont() = compact(null, "artists_compact_font2x.png")

    @Test
    fun artistPageOnCream() = compact(OpenArtist("daft punk"), "artist_page_cream.png", AppBackground.Solid("cream"))

    private fun compact(open: OpenArtist?, fileName: String, background: AppBackground = AppBackground.Default) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    SectionScaffold(
                        modifier = Modifier.fillMaxSize(),
                        showMiniPlayer = true,
                        showCarousel = true,
                        contentBottomPadding = SongListBottomPadding,
                        miniPlayer = { MiniBar() },
                        carousel = {
                            NavCarousel(
                                selected = NavSection.Songs,
                                onSelect = {},
                                sections = SectionRoutes.sectionsFor(expanded = false),
                            )
                        },
                    ) { bottom ->
                        Artists(open, bottom)
                    }
                }
            }
        }
    }

    private fun twoPane(open: OpenArtist?, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Default, emptyMap(), forceStatic = true) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                            ListViewport(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                                Artists(open, SongListBottomPadding)
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

    @Composable
    private fun Artists(open: OpenArtist?, bottom: Dp) {
        LibraryContent(
            tracks = tracks,
            nowPlaying = PlayerUiState(currentMediaId = "11"),
            showMiniPlayer = false,
            onTrackSelected = { _, _ -> },
            onOpenPlayer = {},
            onTogglePlayPause = {},
            favoriteIds = setOf(12L),
            contentBottomPadding = bottom,
            modifier = Modifier.fillMaxSize(),
            view = MusicView.Artists,
            catalog = catalog,
            openArtist = open,
        )
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

    private companion object {
        var nextId = 1L

        fun track(title: String, artist: String, album: String, albumArtist: String?, number: Int?, year: Int?, folder: String? = null) = Track(
            id = nextId++, title = title, artist = artist, album = album, durationMs = 200_000L,
            contentUri = "content://$title", albumArtUri = null, albumArtist = albumArtist,
            trackNumber = number, year = year, folder = folder,
        )

        val tracks: List<Track> = listOf(
            track("Revolver One", "The Beatles", "Revolver", "The Beatles", 1, 1966),
            track("Revolver Two", "The Beatles", "Revolver", "The Beatles", 2, 1966),
            track("Abbey One", "The Beatles", "Abbey Road", "The Beatles", 1, 1969),
            track("Formation", "Beyoncé", "Lemonade", "Beyoncé", 1, 2016),
            track("Hold Up", "Beyoncé", "Lemonade", "Beyoncé", 2, 2016),
            track("Album One Song 1", "Daft Punk", "Album One", "Daft Punk", 1, 2001),
            track("Album One Song 2", "Daft Punk", "Album One", "Daft Punk", 2, 2001),
            track("Album One Song 3", "Daft Punk", "Album One", "Daft Punk", 3, 2001),
            track("Album Two Song 1", "Daft Punk", "Album Two", "Daft Punk", 1, 2013),
            track("Album Two Song 2", "Daft Punk", "Album Two", "Daft Punk", 2, 2013),
            track("Summer Song", "Daft Punk", "Summer Hits", "Various Artists", 1, 2010),
            track("Summer Other", "Zedd", "Summer Hits", "Various Artists", 2, 2010),
            track("Clarity", "Zedd", "Clarity", "Zedd", 1, 2012),
            track("Somebody", "Queen", "Greatest Hits", "Queen", 1, 1981),
            track("Loose One", "Unknown artist", "TestSongs", null, null, null, folder = "Music/TestSongs"),
            track("Loose Two", "Unknown artist", "TestSongs", null, null, null, folder = "Music/TestSongs"),
        )

        val catalog: MusicCatalog = MusicCatalog.build(
            tracks,
            UnknownNames(title = "Unknown title", artist = "Unknown artist", album = "Unknown album"),
            Collator.getInstance(Locale.US),
        )
    }
}
