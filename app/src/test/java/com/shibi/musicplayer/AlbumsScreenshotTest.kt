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
import com.shibi.musicplayer.domain.AlbumSort
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.MusicCatalog
import com.shibi.musicplayer.domain.MusicView
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.UnknownNames
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
 * The Albums grid and an album page (ABBA's two-disc Greatest Hits, with a guest on one song),
 * compact and two-pane; the grid at 2x font; the album page on the light cream background.
 * A fake catalog; one of ABBA's songs is playing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AlbumsScreenshotTest {

    @Test
    fun albumGridCompact() = compact(null, "albums_compact.png")

    @Test
    fun albumPageCompact() = compact(abbaHits, "album_page_compact.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun albumGridTwoPane() = twoPane(null, "albums_two_pane.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun albumPageTwoPane() = twoPane(abbaHits, "album_page_two_pane.png")

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
    fun albumGridCompactBigFont() = compact(null, "albums_compact_font2x.png")

    @Test
    fun albumPageOnCream() = compact(abbaHits, "album_page_cream.png", AppBackground.Solid("cream"))

    /** Track numbers of one, two and three digits at 2x font; the three-digit one is playing and must not clip. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
    fun albumPageCompactBigFontNumbers() =
        compact(numbersAlbum, "album_page_compact_font2x_numbers.png", library = numbersLibrary, playingId = "903")

    private fun compact(
        open: String?,
        fileName: String,
        background: AppBackground = AppBackground.Default,
        library: List<Track> = tracks,
        playingId: String = "3",
    ) {
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
                        Albums(open, bottom, library, playingId)
                    }
                }
            }
        }
    }

    private fun twoPane(open: String?, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(AppBackground.Default, emptyMap(), forceStatic = true) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                            ListViewport(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                                Albums(open, SongListBottomPadding)
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
    private fun Albums(open: String?, bottom: Dp, library: List<Track> = tracks, playingId: String = "3") {
        LibraryContent(
            tracks = library,
            nowPlaying = PlayerUiState(currentMediaId = playingId),
            showMiniPlayer = false,
            onTrackSelected = { _, _ -> },
            onOpenPlayer = {},
            onTogglePlayPause = {},
            favoriteIds = setOf(4L),
            contentBottomPadding = bottom,
            modifier = Modifier.fillMaxSize(),
            view = MusicView.Albums,
            catalog = if (library === tracks) catalog else numbersCatalog,
            albumSort = AlbumSort.Title,
            openAlbum = open,
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

        fun track(title: String, artist: String, album: String, albumArtist: String?, number: Int?, disc: Int?, year: Int?) = Track(
            id = nextId++, title = title, artist = artist, album = album, durationMs = 200_000L,
            contentUri = "content://$title", albumArtUri = null, albumArtist = albumArtist,
            trackNumber = number, discNumber = disc, year = year,
        )

        val tracks: List<Track> = listOf(
            track("Mamma Mia", "ABBA", "Greatest Hits", "ABBA", 1, 1, 1976),
            track("Waterloo", "ABBA", "Greatest Hits", "ABBA", 2, 1, 1976),
            track("Dancing Queen", "ABBA", "Greatest Hits", "ABBA", 1, 2, 1976),
            track("Fernando", "ABBA", "Greatest Hits", "ABBA", 2, 2, 1976),
            track("Chiquitita (Live)", "ABBA feat. Guest Choir", "Greatest Hits", "ABBA", 3, 2, 1976),
            track("Revolver One", "The Beatles", "Revolver", "The Beatles", 1, null, 1966),
            track("Abbey One", "The Beatles", "Abbey Road", "The Beatles", 1, null, 1969),
            track("Formation", "Beyoncé", "Lemonade", "Beyoncé", 1, null, 2016),
            track("Album One Song 1", "Daft Punk", "Album One", "Daft Punk", 1, null, 2001),
            track("Album Two Song 1", "Daft Punk", "Album Two", "Daft Punk", 1, null, 2013),
            track("Summer Song", "Daft Punk", "Summer Hits", "Various Artists", 1, null, 2010),
            track("Clarity", "Zedd", "Clarity", "Zedd", 1, null, 2012),
            track("Somebody", "Queen", "Greatest Hits", "Queen", 1, null, 1981),
            track("Homework Song", "Daft Punk", "Homework", "Daft Punk", null, null, null),
        )

        val catalog: MusicCatalog = MusicCatalog.build(
            tracks,
            UnknownNames(title = "Unknown title", artist = "Unknown artist", album = "Unknown album"),
            Collator.getInstance(Locale.US),
        )

        /** ABBA's Greatest Hits; the key holds a NUL, so it is looked up rather than spelled out. */
        val abbaHits: String = catalog.albums(AlbumSort.Title).first { it.artistName == "ABBA" }.key

        /** One album whose tracks are numbered 9, 12 and 128; 128 (id 903) is the playing row. */
        val numbersLibrary: List<Track> = listOf(9 to 901L, 12 to 902L, 128 to 903L).map { (number, id) ->
            Track(
                id = id, title = "Track $number", artist = "Long Box", album = "The Long Box", durationMs = 200_000L,
                contentUri = "content://track$number", albumArtUri = null, albumArtist = "Long Box",
                trackNumber = number, year = 1999,
            )
        }

        val numbersCatalog: MusicCatalog = MusicCatalog.build(
            numbersLibrary,
            UnknownNames(title = "Unknown title", artist = "Unknown artist", album = "Unknown album"),
            Collator.getInstance(Locale.US),
        )

        val numbersAlbum: String = numbersCatalog.albums(AlbumSort.Title).single().key
    }
}
