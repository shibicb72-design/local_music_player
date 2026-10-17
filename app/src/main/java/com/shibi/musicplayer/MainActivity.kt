package com.shibi.musicplayer

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.musicplayer.domain.BackgroundPolicy
import com.shibi.musicplayer.playlist.PlaylistViewModel
import com.shibi.musicplayer.ui.MusicApp
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import com.shibi.musicplayer.video.VideoViewModel
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    /** The same activity-scoped instance `MusicApp` gets from `viewModel()`. */
    private val videoViewModel: VideoViewModel by viewModels()

    /** Taps on the video's notification or lock-screen card (V8). */
    private val openVideoRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val playlistViewModel: PlaylistViewModel = viewModel()
            val darkTheme = playlistViewModel.darkTheme.collectAsStateWithLifecycle()
            MusicPlayerTheme(darkTheme = darkTheme.value) {
                MusicApp(playlistViewModel = playlistViewModel, openVideoRequests = openVideoRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Only a request to show the video; there is nothing in the intent to trust or forward.
        if (intent.action == ActionOpenVideo) openVideoRequests.tryEmit(Unit)
    }

    override fun onStart() {
        super.onStart()
        // Unlocked, or back from Home: the picture comes back where the sound is.
        videoViewModel.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        // A rotation or fold that recreates is not the app leaving the screen.
        if (isChangingConfigurations) return
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false
        videoViewModel.onAppBackground(BackgroundPolicy.eventFor(interactive, locked, videoViewModel.isPickingFile))
    }

    companion object {
        /** The video session's activity intent: open the app where the video is. */
        const val ActionOpenVideo = "com.shibi.musicplayer.action.OPEN_VIDEO"
    }
}
