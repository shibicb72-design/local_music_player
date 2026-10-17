package com.shibi.musicplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shibi.musicplayer.R
import com.shibi.musicplayer.background.BackgroundViewModel
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.ShuffleContext
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.library.LibraryViewModel
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.playback.PlayerViewModel
import com.shibi.musicplayer.playlist.PlaylistViewModel
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.background.BackgroundPickerScreen
import com.shibi.musicplayer.ui.background.backgroundTitleRes
import com.shibi.musicplayer.ui.background.titleId

private const val SongsRoute = "songs"
private const val PlaylistsRoute = "playlists"
private const val PlayerRoute = "player"
private const val PlaylistDetailRoute = "playlist/{playlistId}"
private const val FavoritesRoute = "favorites"
private const val RecentRoute = "recent"
private const val SearchRoute = "search"
private const val SettingsRoute = "settings"
private const val BackgroundsRoute = "settings/backgrounds"
private const val PlaylistIdArg = "playlistId"
private val TwoPaneBreakpoint = 600.dp

@Composable
fun MusicApp(
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
    playlistViewModel: PlaylistViewModel = viewModel(),
    backgroundViewModel: BackgroundViewModel = viewModel(),
) {
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val selectedOutputId by playerViewModel.preferredOutputId.collectAsStateWithLifecycle()
    val playlistState by playlistViewModel.state.collectAsStateWithLifecycle()
    val favoriteIds by playlistViewModel.favoriteIds.collectAsStateWithLifecycle()
    val playbackRoute by rememberPlaybackRoute(selectedOutputId)
    val playingTrackId = playerState.currentMediaId?.toLongOrNull()
    val playingIsFavorite = playingTrackId != null && playingTrackId in favoriteIds
    val recentIds by playlistViewModel.recentTrackIds.collectAsStateWithLifecycle()
    val recentSearches by playlistViewModel.recentSearches.collectAsStateWithLifecycle()
    val darkTheme by playlistViewModel.darkTheme.collectAsStateWithLifecycle()
    val appBackground by backgroundViewModel.selection.collectAsStateWithLifecycle()
    val backgroundColors by backgroundViewModel.customColors.collectAsStateWithLifecycle()
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val tracksById = remember(libraryState.tracks) {
        libraryState.tracks.associateBy { it.id }
    }
    val recentTracks = remember(recentIds, tracksById) {
        recentIds.mapNotNull { tracksById[it] }
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    var section by remember { mutableStateOf(NavSection.Songs) }
    var showDevices by remember { mutableStateOf(false) }
    var pendingTrack by remember { mutableStateOf<Track?>(null) }
    val outputs by rememberAudioOutputs()

    LaunchedEffect(route) {
        if (showDevices) return@LaunchedEffect
        val mapped = sectionForRoute(route) ?: return@LaunchedEffect
        if (mapped != section) section = mapped
    }

    AppBackgroundHost(
        background = appBackground,
        customColors = backgroundColors,
    ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= TwoPaneBreakpoint
        LaunchedEffect(wide) {
            if (wide && navController.currentDestination?.route == PlayerRoute) {
                navController.popBackStack()
            }
        }
        val showMini = playerState.hasMedia && route != PlayerRoute
        val showCarousel = wide || route != PlayerRoute
        val activeOutputId = playbackRoute.activeDeviceId

        fun playQueue(
            tracks: List<Track>,
            startId: Long,
            context: ShuffleContext = ShuffleContext.ALL_SONGS,
        ) {
            if (tracks.isEmpty()) return
            playerViewModel.playTrack(tracks, startId, context)
            playlistViewModel.recordPlay(startId)
            if (!wide) {
                section = NavSection.NowPlaying
                navController.openPlayer()
            }
        }

        fun onSection(destination: NavSection) {
            section = destination
            when (destination) {
                NavSection.Songs -> {
                    if (route != SongsRoute) navController.openSection(SongsRoute)
                }
                NavSection.Playlists -> {
                    val inPlaylists = route == PlaylistsRoute || route == PlaylistDetailRoute
                    if (!inPlaylists) navController.openSection(PlaylistsRoute)
                }
                NavSection.NowPlaying -> {
                    if (!wide && route != PlayerRoute) navController.openPlayer()
                }
                NavSection.Devices -> showDevices = true
                NavSection.Favorites -> {
                    if (route != FavoritesRoute) navController.openSection(FavoritesRoute)
                }
                NavSection.Recent -> {
                    if (route != RecentRoute) navController.openSection(RecentRoute)
                }
                NavSection.Search -> {
                    if (route != SearchRoute) navController.openSection(SearchRoute)
                }
                NavSection.Settings -> {
                    if (route != SettingsRoute) navController.openSection(SettingsRoute)
                }
            }
        }

        val miniBar: @Composable () -> Unit = {
            NowPlayingBar(
                state = playerState,
                onOpen = {
                    section = NavSection.NowPlaying
                    navController.openPlayer()
                },
                onTogglePlayPause = playerViewModel::togglePlayPause,
                route = playbackRoute.presentation,
                isFavorite = playingIsFavorite,
                onToggleFavorite = {
                    playingTrackId?.let(playlistViewModel::toggleFavorite)
                },
                onOpenDevices = { showDevices = true },
                padForNavigationBars = false,
                bottomPadding = MiniPlayerCarouselGap,
            )
        }
        val carouselBar: @Composable (Modifier) -> Unit = { carouselModifier ->
            NavCarousel(
                selected = section,
                onSelect = { destination -> onSection(destination) },
                modifier = carouselModifier.windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                ),
            )
        }
        val playerPane: @Composable (Dp) -> Unit = { extraBottom ->
            PlayerScreen(
                state = playerState,
                onBack = {},
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onNext = playerViewModel::skipNext,
                onPrevious = playerViewModel::skipPrevious,
                    onSeek = playerViewModel::seekTo,
                    selectedOutputId = activeOutputId,
                    onSelectOutput = playerViewModel::setOutputDevice,
                    onToggleShuffle = playerViewModel::toggleShuffle,
                    onCycleRepeat = playerViewModel::cycleRepeat,
                    showBack = false,
                includeNavigationBars = false,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = extraBottom)
                    .clip(RoundedCornerShape(28.dp)),
            )
        }

        if (!wide) {
            SectionScaffold(
                modifier = Modifier.fillMaxSize(),
                showMiniPlayer = showMini,
                showCarousel = showCarousel,
                contentBottomPadding = SongListBottomPadding,
                miniPlayer = miniBar,
                carousel = { carouselBar(Modifier) },
            ) { bottomPadding ->
                AppNavHost(
                    navController = navController,
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    playlistViewModel = playlistViewModel,
                    playerState = playerState,
                    favoriteIds = favoriteIds,
                    libraryTracks = libraryState.tracks,
                    tracksById = tracksById,
                    recentTracks = recentTracks,
                    recentSearches = recentSearches,
                    darkTheme = darkTheme,
                    appBackground = appBackground,
                    backgroundColors = backgroundColors,
                    onSelectBackground = backgroundViewModel::select,
                    onBackgroundColors = backgroundViewModel::setColors,
                    onResetBackgroundColors = backgroundViewModel::resetColors,
                    wide = false,
                    contentBottomPadding = bottomPadding,
                    activeOutputId = activeOutputId,
                    onPlayQueue = { tracks, startId, context -> playQueue(tracks, startId, context) },
                    onOpenPlayer = {
                        section = NavSection.NowPlaying
                        navController.openPlayer()
                    },
                    onAddToPlaylist = { pendingTrack = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else if (showMini) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    ListViewport(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight(),
                    ) {
                    AppNavHost(
                        navController = navController,
                        libraryViewModel = libraryViewModel,
                        playerViewModel = playerViewModel,
                        playlistViewModel = playlistViewModel,
                        playerState = playerState,
                        favoriteIds = favoriteIds,
                        libraryTracks = libraryState.tracks,
                        tracksById = tracksById,
                        recentTracks = recentTracks,
                        recentSearches = recentSearches,
                        darkTheme = darkTheme,
                        appBackground = appBackground,
                        backgroundColors = backgroundColors,
                        onSelectBackground = backgroundViewModel::select,
                        onBackgroundColors = backgroundViewModel::setColors,
                        onResetBackgroundColors = backgroundViewModel::resetColors,
                        wide = true,
                        contentBottomPadding = SongListBottomPadding,
                        activeOutputId = activeOutputId,
                        onPlayQueue = { tracks, startId, context -> playQueue(tracks, startId, context) },
                        onOpenPlayer = {},
                        onAddToPlaylist = { pendingTrack = it },
                        modifier = Modifier.fillMaxHeight(),
                    )
                    }
                    Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
                        playerPane(SongListBottomPadding)
                    }
                }
                ChromeSlot {
                    miniBar()
                    if (showCarousel) carouselBar(Modifier)
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                ) {
                    ListViewport(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    ) {
                    AppNavHost(
                        navController = navController,
                        libraryViewModel = libraryViewModel,
                        playerViewModel = playerViewModel,
                        playlistViewModel = playlistViewModel,
                        playerState = playerState,
                        favoriteIds = favoriteIds,
                        libraryTracks = libraryState.tracks,
                        tracksById = tracksById,
                        recentTracks = recentTracks,
                        recentSearches = recentSearches,
                        darkTheme = darkTheme,
                        appBackground = appBackground,
                        backgroundColors = backgroundColors,
                        onSelectBackground = backgroundViewModel::select,
                        onBackgroundColors = backgroundViewModel::setColors,
                        onResetBackgroundColors = backgroundViewModel::resetColors,
                        wide = true,
                        contentBottomPadding = SongListBottomPadding,
                        activeOutputId = activeOutputId,
                        onPlayQueue = { tracks, startId, context -> playQueue(tracks, startId, context) },
                        onOpenPlayer = {},
                        onAddToPlaylist = { pendingTrack = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    }
                    ChromeSlot {
                        carouselBar(
                            Modifier.padding(
                                top = TwoPaneCarouselPadding,
                                bottom = TwoPaneCarouselPadding,
                            ),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .navigationBarsPadding(),
                ) {
                    playerPane(TwoPanePlayerBottomPadding)
                }
            }
        }
    }

    if (showDevices) {
        OutputPickerSheet(
            outputs = outputs,
            selectedId = playbackRoute.activeDeviceId,
            onSelect = { deviceId -> playerViewModel.setOutputDevice(deviceId) },
            onDismiss = {
                showDevices = false
                section = sectionForRoute(navController.currentDestination?.route)
                    ?: NavSection.Songs
            },
        )
    }
    pendingTrack?.let { track ->
        AddToPlaylistSheet(
            trackTitle = track.title,
            playlists = playlistState.playlists.map { it.toCard(tracksById) },
            onPick = { playlistId ->
                playlistViewModel.addSong(playlistId, track.id)
                pendingTrack = null
            },
            onCreate = { name ->
                playlistViewModel.create(name) { playlistId ->
                    playlistViewModel.addSong(playlistId, track.id)
                }
                pendingTrack = null
            },
            onDismiss = { pendingTrack = null },
        )
    }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel,
    playerState: PlayerUiState,
    favoriteIds: Set<Long>,
    libraryTracks: List<Track>,
    tracksById: Map<Long, Track>,
    recentTracks: List<Track>,
    recentSearches: List<String>,
    darkTheme: Boolean,
    appBackground: AppBackground,
    backgroundColors: Map<String, List<Long>>,
    onSelectBackground: (AppBackground) -> Unit,
    onBackgroundColors: (String, List<Long>) -> Unit,
    onResetBackgroundColors: (String) -> Unit,
    wide: Boolean,
    contentBottomPadding: Dp,
    activeOutputId: Int?,
    onPlayQueue: (List<Track>, Long, ShuffleContext) -> Unit,
    onOpenPlayer: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = SongsRoute,
        modifier = modifier,
    ) {
        composable(SongsRoute) {
            LibraryScreen(
                viewModel = libraryViewModel,
                nowPlaying = playerState,
                onTrackSelected = { tracks, trackId ->
                    onPlayQueue(tracks, trackId, ShuffleContext.ALL_SONGS)
                },
                onOpenPlayer = onOpenPlayer,
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onAddToPlaylist = onAddToPlaylist,
                favoriteIds = favoriteIds,
                onToggleFavorite = { playlistViewModel.toggleFavorite(it.id) },
                showMiniPlayer = false,
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(PlaylistsRoute) {
            PlaylistsScreen(
                viewModel = playlistViewModel,
                tracksById = tracksById,
                onOpen = { id ->
                    navController.navigate("playlist/$id") { launchSingleTop = true }
                },
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = PlaylistDetailRoute,
            arguments = listOf(navArgument(PlaylistIdArg) { type = NavType.LongType }),
        ) { entry ->
            val playlistId = entry.arguments?.getLong(PlaylistIdArg) ?: return@composable
            PlaylistDetailScreen(
                playlistId = playlistId,
                viewModel = playlistViewModel,
                tracksById = tracksById,
                onBack = { navController.popBackStack() },
                onPlay = { tracks, startId ->
                    onPlayQueue(tracks, startId, ShuffleContext.PLAYLIST)
                },
                onShuffle = { tracks ->
                    val shuffled = tracks.shuffled()
                    shuffled.firstOrNull()?.let {
                        onPlayQueue(shuffled, it.id, ShuffleContext.PLAYLIST)
                    }
                },
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(FavoritesRoute) {
            FavoritesScreen(
                tracks = libraryTracks,
                favoriteIds = favoriteIds,
                nowPlaying = playerState,
                onTrackSelected = { tracks, startId ->
                    onPlayQueue(tracks, startId, ShuffleContext.ALL_SONGS)
                },
                onToggleFavorite = { playlistViewModel.toggleFavorite(it.id) },
                onAddToPlaylist = onAddToPlaylist,
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(RecentRoute) {
            RecentScreen(
                tracks = recentTracks,
                favoriteIds = favoriteIds,
                nowPlaying = playerState,
                onTrackSelected = { tracks, startId ->
                    onPlayQueue(tracks, startId, ShuffleContext.ALL_SONGS)
                },
                onToggleFavorite = { playlistViewModel.toggleFavorite(it.id) },
                onAddToPlaylist = onAddToPlaylist,
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(SearchRoute) {
            SearchScreen(
                tracks = libraryTracks,
                favoriteIds = favoriteIds,
                nowPlaying = playerState,
                onTrackSelected = { tracks, startId ->
                    onPlayQueue(tracks, startId, ShuffleContext.ALL_SONGS)
                },
                onToggleFavorite = { playlistViewModel.toggleFavorite(it.id) },
                onAddToPlaylist = onAddToPlaylist,
                recentSearches = recentSearches,
                onSearchCommitted = playlistViewModel::recordSearch,
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(SettingsRoute) {
            SettingsScreen(
                darkTheme = darkTheme,
                onDarkThemeChange = playlistViewModel::setDarkTheme,
                backgroundName = stringResource(backgroundTitleRes(appBackground.titleId())),
                onOpenBackgrounds = {
                    navController.navigate(BackgroundsRoute) { launchSingleTop = true }
                },
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(BackgroundsRoute) {
            BackgroundPickerScreen(
                selected = appBackground,
                customColors = backgroundColors,
                onSelect = onSelectBackground,
                onColorsChange = onBackgroundColors,
                onResetColors = onResetBackgroundColors,
                onBack = { navController.popBackStack() },
                contentBottomPadding = contentBottomPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(PlayerRoute) {
            if (!wide) {
                PlayerScreen(
                    state = playerState,
                    onBack = { navController.popBackStack() },
                    onTogglePlayPause = playerViewModel::togglePlayPause,
                    onNext = playerViewModel::skipNext,
                    onPrevious = playerViewModel::skipPrevious,
                    onSeek = playerViewModel::seekTo,
                    selectedOutputId = activeOutputId,
                    onSelectOutput = playerViewModel::setOutputDevice,
                    onToggleShuffle = playerViewModel::toggleShuffle,
                    onCycleRepeat = playerViewModel::cycleRepeat,
                )
            }
        }
    }
}

private fun sectionForRoute(route: String?): NavSection? = when (route) {
    SongsRoute -> NavSection.Songs
    PlaylistsRoute -> NavSection.Playlists
    PlayerRoute -> NavSection.NowPlaying
    PlaylistDetailRoute -> NavSection.Playlists
    FavoritesRoute -> NavSection.Favorites
    RecentRoute -> NavSection.Recent
    SearchRoute -> NavSection.Search
    SettingsRoute -> NavSection.Settings
    BackgroundsRoute -> NavSection.Settings
    else -> null
}

private fun NavHostController.openSection(route: String) {
    navigate(route) {
        popUpTo(SongsRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.openPlayer() {
    navigate(PlayerRoute) { launchSingleTop = true }
}
