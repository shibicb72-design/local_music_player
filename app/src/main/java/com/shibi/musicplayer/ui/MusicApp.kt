package com.shibi.musicplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shibi.musicplayer.library.LibraryViewModel
import com.shibi.musicplayer.playback.PlayerViewModel

private const val LibraryRoute = "library"
private const val PlayerRoute = "player"

@Composable
fun MusicApp(
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
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
            )
        }
    }
}
