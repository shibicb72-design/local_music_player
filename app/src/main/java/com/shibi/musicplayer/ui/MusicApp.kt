package com.shibi.musicplayer.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.library.LibraryViewModel
import com.shibi.musicplayer.playback.PlayerViewModel
import com.shibi.musicplayer.playlist.PlaylistViewModel

private const val SongsRoute = "songs"
private const val PlaylistsRoute = "playlists"
private const val PlayerRoute = "player"
private const val PlaylistDetailRoute = "playlist/{playlistId}"
private const val PlaylistIdArg = "playlistId"
private val TwoPaneBreakpoint = 600.dp
private val ListBottomPadding = 16.dp

@Composable
fun MusicApp(
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
    playlistViewModel: PlaylistViewModel = viewModel(),
) {
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val selectedOutputId by playerViewModel.preferredOutputId.collectAsStateWithLifecycle()
    val playlistState by playlistViewModel.state.collectAsStateWithLifecycle()
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val tracksById = remember(libraryState.tracks) {
        libraryState.tracks.associateBy { it.id }
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    var wheelSelection by remember { mutableStateOf(WheelDestination.Songs) }
    var showDevices by remember { mutableStateOf(false) }
    var pendingTrack by remember { mutableStateOf<Track?>(null) }
    val outputs by rememberAudioOutputs()

    LaunchedEffect(route) {
        if (showDevices) return@LaunchedEffect
        val mapped = destinationForRoute(route) ?: return@LaunchedEffect
        if (mapped != wheelSelection) wheelSelection = mapped
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= TwoPaneBreakpoint
        LaunchedEffect(wide) {
            if (wide && navController.currentDestination?.route == PlayerRoute) {
                navController.popBackStack()
            }
        }
        val showMini = !wide && playerState.hasMedia && route != PlayerRoute

        fun playQueue(tracks: List<Track>, startId: Long) {
            if (tracks.isEmpty()) return
            playerViewModel.playTrack(tracks, startId)
            if (!wide) {
                wheelSelection = WheelDestination.NowPlaying
                navController.openPlayer()
            }
        }

        fun onWheel(destination: WheelDestination) {
            wheelSelection = destination
            when (destination) {
                WheelDestination.Songs -> {
                    if (route != SongsRoute) navController.openSection(SongsRoute)
                }
                WheelDestination.Playlists -> {
                    val inPlaylists = route == PlaylistsRoute || route == PlaylistDetailRoute
                    if (!inPlaylists) navController.openSection(PlaylistsRoute)
                }
                WheelDestination.NowPlaying -> {
                    if (!wide && route != PlayerRoute) navController.openPlayer()
                }
                WheelDestination.Devices -> showDevices = true
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(if (wide) 0.42f else 1f)
                    .fillMaxHeight(),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = SongsRoute,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    composable(SongsRoute) {
                        LibraryScreen(
                            viewModel = libraryViewModel,
                            nowPlaying = playerState,
                            onTrackSelected = { tracks, trackId -> playQueue(tracks, trackId) },
                            onOpenPlayer = {
                                if (!wide) {
                                    wheelSelection = WheelDestination.NowPlaying
                                    navController.openPlayer()
                                }
                            },
                            onTogglePlayPause = playerViewModel::togglePlayPause,
                            onAddToPlaylist = { pendingTrack = it },
                            showMiniPlayer = false,
                            contentBottomPadding = ListBottomPadding,
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
                            contentBottomPadding = ListBottomPadding,
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
                            onPlay = { tracks, startId -> playQueue(tracks, startId) },
                            onShuffle = { tracks ->
                                val shuffled = tracks.shuffled()
                                shuffled.firstOrNull()?.let { playQueue(shuffled, it.id) }
                            },
                            contentBottomPadding = ListBottomPadding,
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
                                selectedOutputId = selectedOutputId,
                                onSelectOutput = playerViewModel::setOutputDevice,
                                includeNavigationBars = false,
                            )
                        }
                    }
                }
                if (showMini) {
                    NowPlayingBar(
                        state = playerState,
                        onOpen = {
                            wheelSelection = WheelDestination.NowPlaying
                            navController.openPlayer()
                        },
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        padForNavigationBars = false,
                    )
                }
                NavWheel(
                    selected = wheelSelection,
                    onSelect = { destination -> onWheel(destination) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                        )
                        .navigationBarsPadding(),
                )
            }
            if (wide) {
                PlayerScreen(
                    state = playerState,
                    onBack = {},
                    onTogglePlayPause = playerViewModel::togglePlayPause,
                    onNext = playerViewModel::skipNext,
                    onPrevious = playerViewModel::skipPrevious,
                    onSeek = playerViewModel::seekTo,
                    selectedOutputId = selectedOutputId,
                    onSelectOutput = playerViewModel::setOutputDevice,
                    showBack = false,
                    modifier = Modifier
                        .weight(0.58f)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(28.dp)),
                )
            }
        }
    }

    if (showDevices) {
        OutputPickerSheet(
            outputs = outputs,
            selectedId = selectedOutputId,
            onSelect = { deviceId -> playerViewModel.setOutputDevice(deviceId) },
            onDismiss = {
                showDevices = false
                wheelSelection = destinationForRoute(navController.currentDestination?.route)
                    ?: WheelDestination.Songs
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

private fun destinationForRoute(route: String?): WheelDestination? = when (route) {
    SongsRoute -> WheelDestination.Songs
    PlaylistsRoute -> WheelDestination.Playlists
    PlayerRoute -> WheelDestination.NowPlaying
    PlaylistDetailRoute -> WheelDestination.Playlists
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
