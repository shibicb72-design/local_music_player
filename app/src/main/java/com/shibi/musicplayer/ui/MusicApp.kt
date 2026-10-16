package com.shibi.musicplayer.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shibi.musicplayer.library.LibraryViewModel
import com.shibi.musicplayer.playback.PlayerViewModel

private const val LibraryRoute = "library"
private const val PlayerRoute = "player"
private val TwoPaneBreakpoint = 600.dp

@Composable
fun MusicApp(
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
) {
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val selectedOutputId by playerViewModel.preferredOutputId.collectAsStateWithLifecycle()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= TwoPaneBreakpoint
        if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    nowPlaying = playerState,
                    onTrackSelected = { tracks, trackId ->
                        playerViewModel.playTrack(tracks, trackId)
                    },
                    onOpenPlayer = {},
                    onTogglePlayPause = playerViewModel::togglePlayPause,
                    showMiniPlayer = false,
                    modifier = Modifier.weight(0.42f),
                )
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
        } else {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = LibraryRoute,
            ) {
                composable(LibraryRoute) {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        nowPlaying = playerState,
                        onTrackSelected = { tracks, trackId ->
                            playerViewModel.playTrack(tracks, trackId)
                            navController.navigate(PlayerRoute) { launchSingleTop = true }
                        },
                        onOpenPlayer = {
                            navController.navigate(PlayerRoute) { launchSingleTop = true }
                        },
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                    )
                }
                composable(PlayerRoute) {
                    PlayerScreen(
                        state = playerState,
                        onBack = { navController.popBackStack() },
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        onNext = playerViewModel::skipNext,
                        onPrevious = playerViewModel::skipPrevious,
                        onSeek = playerViewModel::seekTo,
                        selectedOutputId = selectedOutputId,
                        onSelectOutput = playerViewModel::setOutputDevice,
                    )
                }
            }
        }
    }
}
