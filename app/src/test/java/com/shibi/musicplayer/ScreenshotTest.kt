package com.shibi.musicplayer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.domain.AudioOutput
import com.shibi.musicplayer.domain.OutputKind
import com.shibi.musicplayer.domain.RouteIcon
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.TrackDisplay
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.AddToPlaylistContent
import com.shibi.musicplayer.ui.FavoritesContent
import com.shibi.musicplayer.ui.LibraryContent
import com.shibi.musicplayer.ui.MiniPlayerCarouselGap
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.NowPlayingBar
import com.shibi.musicplayer.ui.SectionScaffold
import com.shibi.musicplayer.ui.SettingsContent
import com.shibi.musicplayer.ui.OutputPickerContent
import com.shibi.musicplayer.ui.OutputScrim
import com.shibi.musicplayer.ui.TwoPaneCarouselPadding
import com.shibi.musicplayer.ui.PlayerScreen
import com.shibi.musicplayer.ui.PlaylistCardUi
import com.shibi.musicplayer.ui.PlaylistDetailContent
import com.shibi.musicplayer.ui.PlaylistListBottomPadding
import com.shibi.musicplayer.ui.PlaylistSongRow
import com.shibi.musicplayer.ui.PlaylistsContent
import com.shibi.musicplayer.ui.SearchContent
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.TwoPanePlayerBottomPadding
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class, ExperimentalCoilApi::class, ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @Test
    fun list() {
        captureRoboImage("build/outputs/roborazzi/list.png") {
            MusicPlayerTheme {
                SongsWithCarousel(tracks, NavSection.Songs)
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
    fun nowPlayingArt() {
        val bitmap = createArtBitmap()
        captureRoboImage("build/outputs/roborazzi/now_playing_art.png") {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler {
                    bitmap.asImage()
                },
            ) {
                MusicPlayerTheme {
                    PlayerScreen(
                        state = englishState.copy(albumArtUri = "test://art"),
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
    }

    @Test
    fun miniPlayer() {
        captureRoboImage("build/outputs/roborazzi/mini_player.png") {
            MusicPlayerTheme {
                SongsWithCarousel(emptyList(), NavSection.Songs)
            }
        }
    }

    @Test
    fun deviceSheet() {
        captureRoboImage("build/outputs/roborazzi/device_sheet.png") {
            MusicPlayerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(OutputScrim),
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            BottomSheetDefaults.DragHandle(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            OutputPickerContent(
                                outputs = outputs,
                                selectedId = MAJOR_V_ID,
                                onSelect = {},
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun twoPane() {
        captureRoboImage("build/outputs/roborazzi/two_pane.png") {
            MusicPlayerTheme {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(0.42f).fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            LibraryContent(
                                tracks = tracks,
                                nowPlaying = playingState,
                                showMiniPlayer = false,
                                onTrackSelected = { _, _ -> },
                                onOpenPlayer = {},
                                onTogglePlayPause = {},
                                favoriteIds = setOf(8L),
                                contentBottomPadding = SongListBottomPadding,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        NavCarousel(
                            selected = NavSection.Songs,
                            onSelect = {},
                            modifier = Modifier.padding(
                                top = TwoPaneCarouselPadding,
                                bottom = TwoPaneCarouselPadding,
                            ),
                        )
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

    @Test
    fun carouselSongs() = captureCarousel(NavSection.Songs, "carousel_songs.png")

    @Test
    fun carouselNowPlaying() = captureCarousel(NavSection.NowPlaying, "carousel_now_playing.png")

    @Test
    fun carouselDevices() = captureCarousel(NavSection.Devices, "carousel_devices.png")

    @Test
    fun playlists() {
        captureRoboImage("build/outputs/roborazzi/playlists.png") {
            MusicPlayerTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        PlaylistsContent(
                            cards = playlistCards,
                            onOpen = {},
                            onCreate = {},
                            contentBottomPadding = PlaylistListBottomPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    NavCarousel(
                        selected = NavSection.Playlists,
                        onSelect = {},
                    )
                }
            }
        }
    }

    @Test
    fun playlistDetail() {
        captureRoboImage("build/outputs/roborazzi/playlist_detail.png") {
            MusicPlayerTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        PlaylistDetailContent(
                            name = "Late night",
                            songs = detailSongs,
                            onBack = {},
                            onPlayAll = {},
                            onShuffle = {},
                            onRenameClick = {},
                            onDeleteClick = {},
                            onRemove = {},
                            onReorder = {},
                            onPlayFrom = {},
                            contentBottomPadding = SongListBottomPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    NavCarousel(
                        selected = NavSection.Playlists,
                        onSelect = {},
                    )
                }
            }
        }
    }

    @Test
    fun addToPlaylist() {
        captureRoboImage("build/outputs/roborazzi/add_to_playlist.png") {
            MusicPlayerTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        PlaylistsContent(
                            cards = playlistCards,
                            onOpen = {},
                            onCreate = {},
                            contentBottomPadding = PlaylistListBottomPadding,
                            modifier = Modifier.weight(1f),
                        )
                        NavCarousel(
                            selected = NavSection.Playlists,
                            onSelect = {},
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.72f)),
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            BottomSheetDefaults.DragHandle(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            AddToPlaylistContent(
                                trackTitle = "Paper Planes",
                                playlists = playlistCards,
                                creating = true,
                                draftName = "",
                                onDraftChange = {},
                                onStartCreate = {},
                                onConfirmCreate = {},
                                onPick = {},
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun favorites() {
        captureRoboImage("build/outputs/roborazzi/favorites.png") {
            MusicPlayerTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        FavoritesContent(
                            tracks = emptyList(),
                            favoriteIds = emptySet(),
                            nowPlaying = PlayerUiState(),
                            onTrackSelected = { _, _ -> },
                            onToggleFavorite = {},
                            onAddToPlaylist = {},
                            contentBottomPadding = SongListBottomPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    NavCarousel(
                        selected = NavSection.Favorites,
                        onSelect = {},
                    )
                }
            }
        }
    }

    @Test
    fun miniPlayerOnSongs() {
        captureRoboImage("build/outputs/roborazzi/mini_player_songs.png") {
            MusicPlayerTheme {
                SongsWithCarousel(tracks, NavSection.Songs)
            }
        }
    }

    @Test
    fun miniPlayerOnPlaylists() {
        captureRoboImage("build/outputs/roborazzi/mini_player_playlists.png") {
            MusicPlayerTheme {
                SectionScaffold(
                    modifier = Modifier.fillMaxSize(),
                    showMiniPlayer = true,
                    showCarousel = true,
                    contentBottomPadding = PlaylistListBottomPadding,
                    miniPlayer = {
                        MiniBar(longState, carRoute, liked = true)
                    },
                    carousel = {
                        NavCarousel(selected = NavSection.Playlists, onSelect = {})
                    },
                ) { bottom ->
                    PlaylistsContent(
                        cards = playlistCards,
                        onOpen = {},
                        onCreate = {},
                        contentBottomPadding = bottom,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Test
    fun miniPlayerOnSettings() {
        captureRoboImage("build/outputs/roborazzi/mini_player_settings.png") {
            MusicPlayerTheme {
                SectionScaffold(
                    modifier = Modifier.fillMaxSize(),
                    showMiniPlayer = true,
                    showCarousel = true,
                    contentBottomPadding = SongListBottomPadding,
                    miniPlayer = {
                        MiniBar(playingState, headphonesRoute, liked = false)
                    },
                    carousel = {
                        NavCarousel(selected = NavSection.Settings, onSelect = {})
                    },
                ) { bottom ->
                    SettingsContent(
                        darkTheme = true,
                        onDarkThemeChange = {},
                        contentBottomPadding = bottom,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Test
    fun miniPlayerOnPlaylistDetail() {
        captureRoboImage("build/outputs/roborazzi/mini_player_playlist_detail.png") {
            MusicPlayerTheme {
                SectionScaffold(
                    modifier = Modifier.fillMaxSize(),
                    showMiniPlayer = true,
                    showCarousel = true,
                    contentBottomPadding = SongListBottomPadding,
                    miniPlayer = { MiniBar(playingState, phoneRoute, liked = false) },
                    carousel = {
                        NavCarousel(selected = NavSection.Playlists, onSelect = {})
                    },
                ) { bottom ->
                    PlaylistDetailContent(
                        name = "Late night",
                        songs = detailSongs,
                        onBack = {},
                        onPlayAll = {},
                        onShuffle = {},
                        onRenameClick = {},
                        onDeleteClick = {},
                        onRemove = {},
                        onReorder = {},
                        onPlayFrom = {},
                        contentBottomPadding = bottom,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Test
    fun miniPlayerOnFavorites() {
        captureRoboImage("build/outputs/roborazzi/mini_player_favorites.png") {
            MusicPlayerTheme {
                SectionScaffold(
                    modifier = Modifier.fillMaxSize(),
                    showMiniPlayer = true,
                    showCarousel = true,
                    contentBottomPadding = SongListBottomPadding,
                    miniPlayer = { MiniBar(playingState, phoneRoute, liked = true) },
                    carousel = {
                        NavCarousel(selected = NavSection.Favorites, onSelect = {})
                    },
                ) { bottom ->
                    FavoritesContent(
                        tracks = tracks.filter { it.id in setOf(2L, 8L) },
                        favoriteIds = setOf(2L, 8L),
                        nowPlaying = playingState,
                        onTrackSelected = { _, _ -> },
                        onToggleFavorite = {},
                        onAddToPlaylist = {},
                        contentBottomPadding = bottom,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Test
    fun miniPlayerOnSearch() {
        captureRoboImage("build/outputs/roborazzi/mini_player_search.png") {
            MusicPlayerTheme {
                SectionScaffold(
                    modifier = Modifier.fillMaxSize(),
                    showMiniPlayer = true,
                    showCarousel = true,
                    contentBottomPadding = SongListBottomPadding,
                    miniPlayer = { MiniBar(playingState, headphonesRoute, liked = false) },
                    carousel = {
                        NavCarousel(selected = NavSection.Search, onSelect = {})
                    },
                ) { bottom ->
                    SearchContent(
                        tracks = tracks,
                        query = "Paper",
                        onQueryChange = {},
                        favoriteIds = setOf(8L),
                        nowPlaying = playingState,
                        onTrackSelected = { _, _ -> },
                        onToggleFavorite = {},
                        onAddToPlaylist = {},
                        recentSearches = listOf("Tide", "Golden", "Paper"),
                        contentBottomPadding = bottom,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Test
    fun miniPlayerPhoneUnliked() = captureRoute("mini_player_phone_unliked.png", phoneRoute, liked = false)

    @Test
    fun miniPlayerPhoneLiked() = captureRoute("mini_player_phone_liked.png", phoneRoute, liked = true)

    @Test
    fun miniPlayerCarUnliked() = captureRoute("mini_player_car_unliked.png", carRoute, liked = false)

    @Test
    fun miniPlayerCarLiked() = captureRoute("mini_player_car_liked.png", carRoute, liked = true)

    @Test
    fun miniPlayerHeadphonesUnliked() =
        captureRoute("mini_player_headphones_unliked.png", headphonesRoute, liked = false)

    @Test
    fun miniPlayerHeadphonesLiked() =
        captureRoute("mini_player_headphones_liked.png", headphonesRoute, liked = true)

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun twoPaneMiniPlayer() {
        captureRoboImage("build/outputs/roborazzi/two_pane_mini.png") {
            MusicPlayerTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    ) {
                        LibraryContent(
                            tracks = tracks,
                            nowPlaying = longState,
                            showMiniPlayer = false,
                            onTrackSelected = { _, _ -> },
                            onOpenPlayer = {},
                            onTogglePlayPause = {},
                            favoriteIds = setOf(2L, 8L),
                            contentBottomPadding = SongListBottomPadding,
                            modifier = Modifier.weight(0.42f).fillMaxSize(),
                        )
                        PlayerScreen(
                            state = longState,
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
                                .padding(
                                    start = 8.dp,
                                    top = 16.dp,
                                    end = 16.dp,
                                    bottom = SongListBottomPadding,
                                )
                                .clip(RoundedCornerShape(28.dp)),
                        )
                    }
                    MiniBar(longState, carRoute, liked = true)
                    NavCarousel(selected = NavSection.Songs, onSelect = {})
                }
            }
        }
    }

    @Composable
    private fun MiniBar(
        state: PlayerUiState,
        route: RoutePresentation,
        liked: Boolean,
    ) {
        NowPlayingBar(
            state = state,
            onOpen = {},
            onTogglePlayPause = {},
            route = route,
            isFavorite = liked,
            padForNavigationBars = false,
            bottomPadding = MiniPlayerCarouselGap,
        )
    }

    private fun captureRoute(fileName: String, route: RoutePresentation, liked: Boolean) {
        val state = if (route.icon == RouteIcon.PHONE) playingState else longState
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0E0E12)),
                ) {
                    Column(modifier = Modifier.align(Alignment.BottomCenter)) {
                        MiniBar(state, route, liked)
                    }
                }
            }
        }
    }

    @Test
    fun search() {
        captureRoboImage("build/outputs/roborazzi/search.png") {
            MusicPlayerTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    SearchContent(
                        tracks = tracks,
                        query = "",
                        onQueryChange = {},
                        favoriteIds = setOf(8L),
                        nowPlaying = playingState,
                        onTrackSelected = { _, _ -> },
                        onToggleFavorite = {},
                        onAddToPlaylist = {},
                        recentSearches = listOf("Tide", "Golden", "Paper"),
                        contentBottomPadding = SongListBottomPadding,
                        modifier = Modifier.weight(1f),
                    )
                    NavCarousel(
                        selected = NavSection.Search,
                        onSelect = {},
                    )
                }
            }
        }
    }

    @Composable
    private fun SongsWithCarousel(
        library: List<Track>,
        selected: NavSection,
        route: RoutePresentation = RoutePresentation.phone("This phone"),
        liked: Boolean = false,
        nowPlaying: PlayerUiState = if (library.isEmpty()) PlayerUiState() else playingState,
    ) {
        SectionScaffold(
            modifier = Modifier.fillMaxSize(),
            showMiniPlayer = nowPlaying.hasMedia,
            showCarousel = true,
            contentBottomPadding = SongListBottomPadding,
            miniPlayer = {
                NowPlayingBar(
                    state = nowPlaying,
                    onOpen = {},
                    onTogglePlayPause = {},
                    route = route,
                    isFavorite = liked,
                    padForNavigationBars = false,
                    bottomPadding = MiniPlayerCarouselGap,
                )
            },
            carousel = {
                NavCarousel(selected = selected, onSelect = {})
            },
        ) { bottom ->
            LibraryContent(
                tracks = library,
                nowPlaying = nowPlaying,
                showMiniPlayer = false,
                onTrackSelected = { _, _ -> },
                onOpenPlayer = {},
                onTogglePlayPause = {},
                favoriteIds = if (library.isEmpty()) emptySet() else setOf(8L),
                contentBottomPadding = bottom,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private fun captureCarousel(selected: NavSection, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                SongsWithCarousel(tracks, selected)
            }
        }
    }

    private fun createArtBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f,
            0f,
            600f,
            600f,
            0xFFFF5C8A.toInt(),
            0xFF3D2BFF.toInt(),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, 600f, 600f, paint)
        paint.shader = null
        paint.color = 0x40FFFFFF
        canvas.drawCircle(150f, 170f, 95f, paint)
        canvas.drawCircle(430f, 300f, 130f, paint)
        canvas.drawCircle(280f, 470f, 70f, paint)
        return bitmap
    }

    private companion object {
        const val MAJOR_V_ID = 2

        val tamilTitle =
            "\u0B8E\u0BA9\u0BCD\u0BA9\u0BC8 \u0B95\u0BCA\u0B9E\u0BCD\u0B9A\u0BAE\u0BCD.mp3"

        val tracks = listOf(
            Track(1L, "Midnight Drive", "Neon Cascade", "Night Highway", 215_000L, "content://tracks/1", null),
            Track(2L, "Paper Planes", "Ella Voss", "Paper Planes", 198_000L, "content://tracks/2", null),
            Track(8L, tamilTitle, "<unknown>", "Unknown album", 215_000L, "content://tracks/8", null),
            Track(3L, "Golden Hour", "Marina Cole", "Golden Hour", 224_000L, "content://tracks/3", null),
            Track(4L, "Saltwater", "Northbound", "Tidal", 187_000L, "content://tracks/4", null),
            Track(5L, "Ember", "Rilo Ash", "Ember", 245_000L, "content://tracks/5", null),
            Track(6L, "Static Bloom", "Hollow Coast", "Static Bloom", 202_000L, "content://tracks/6", null),
            Track(7L, "Slow Tide", "June & The Current", "Slow Tide", 176_000L, "content://tracks/7", null),
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

        val longState = playingState.copy(
            title = "Why Do We Fall From The Dark Knight Rises Original Motion Picture Score",
            artist = "Hans Zimmer, James Newton Howard",
        )

        val phoneRoute = RoutePresentation.phone("This phone")

        val carRoute = RoutePresentation(
            icon = RouteIcon.CAR,
            deviceName = "HandsFreeLink",
            showDeviceName = true,
            showBluetoothGlyph = true,
        )

        val headphonesRoute = RoutePresentation(
            icon = RouteIcon.HEADPHONES,
            deviceName = "WH-1000XM5",
            showDeviceName = true,
            showBluetoothGlyph = true,
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

        val lateNight = tracks

        val playlistCards = listOf(
            PlaylistCardUi(
                id = 1,
                name = "Late night",
                songCount = lateNight.size,
                coverTitles = lateNight.take(4).map { TrackDisplay.title(it.title) },
            ),
            PlaylistCardUi(
                id = 2,
                name = "Focus",
                songCount = 3,
                coverTitles = lateNight.slice(4..6).map { TrackDisplay.title(it.title) },
            ),
            PlaylistCardUi(
                id = 3,
                name = "\u0BA4\u0BAE\u0BBF\u0BB4\u0BCD",
                songCount = 1,
                coverTitles = listOf(TrackDisplay.title(tamilTitle)),
            ),
            PlaylistCardUi(
                id = 4,
                name = "Empty set",
                songCount = 0,
                coverTitles = emptyList(),
            ),
        )

        val detailSongs = lateNight.map { track ->
            PlaylistSongRow(
                track.id,
                TrackDisplay.title(track.title),
                TrackDisplay.artist(track.artist),
                TrackDisplay.album(track.album),
                null,
                false,
            )
        }

        val outputs = listOf(
            AudioOutput(1, OutputKind.PHONE_SPEAKER, "This phone"),
            AudioOutput(MAJOR_V_ID, OutputKind.BLUETOOTH, "MAJOR V"),
            AudioOutput(3, OutputKind.USB, "USB-C headphones"),
        )
    }
}
