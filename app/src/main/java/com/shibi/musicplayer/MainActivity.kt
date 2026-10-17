package com.shibi.musicplayer

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.musicplayer.diagnostics.ErrorLog
import com.shibi.musicplayer.domain.BackgroundPolicy
import com.shibi.musicplayer.playlist.PlaylistViewModel
import com.shibi.musicplayer.ui.MusicApp
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import com.shibi.musicplayer.video.PipParamsFactory
import com.shibi.musicplayer.video.VideoViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** The same activity-scoped instance `MusicApp` gets from `viewModel()`. */
    private val videoViewModel: VideoViewModel by viewModels()

    /** Taps on the video's notification or lock-screen card (V8). */
    private val openVideoRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** True while the activity is in picture-in-picture, so `onStop` can tell the policy. */
    private var pipActive = false

    /** The picture-in-picture window's controls; they arrive as broadcasts from this app only. */
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PipParamsFactory.ACTION_TOGGLE -> videoViewModel.togglePlayPause()
                PipParamsFactory.ACTION_BACK -> videoViewModel.seekBy(-PipParamsFactory.SEEK_STEP_MS)
                PipParamsFactory.ACTION_FORWARD -> videoViewModel.seekBy(PipParamsFactory.SEEK_STEP_MS)
            }
        }
    }

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
        ContextCompat.registerReceiver(
            this,
            pipReceiver,
            IntentFilter().apply {
                addAction(PipParamsFactory.ACTION_TOGGLE)
                addAction(PipParamsFactory.ACTION_BACK)
                addAction(PipParamsFactory.ACTION_FORWARD)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        lifecycleScope.launch {
            videoViewModel.uiState
                .map { Triple(it.isActive, it.isPlaying, it.aspect) }
                .distinctUntilChanged()
                .collect { (active, playing, aspect) -> updatePipParams(active && playing, aspect) }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(pipReceiver)
        super.onDestroy()
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
        videoViewModel.onAppBackground(
            BackgroundPolicy.eventFor(interactive, locked, videoViewModel.isPickingFile, pipActive),
        )
        // Dismissing the picture-in-picture window stops the activity; the stop above already
        // reported it, so the flag goes before the next, real stop.
        pipActive = false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // On API 31+ auto-enter does it; below, enter here when a video is playing.
        if (Build.VERSION.SDK_INT < 31 &&
            PipParamsFactory.supported(this) &&
            BackgroundPolicy.shouldEnterPip(videoViewModel.uiState.value.isPlaying)
        ) {
            try {
                enterPictureInPictureMode(
                    PipParamsFactory.build(this, true, videoViewModel.uiState.value.aspect),
                )
            } catch (e: IllegalArgumentException) {
                logPipError("Enter picture-in-picture", e)
            } catch (e: IllegalStateException) {
                logPipError("Enter picture-in-picture", e)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        videoViewModel.setInPip(isInPictureInPictureMode)
        if (isInPictureInPictureMode) pipActive = true
    }

    /** The video pane's aspect ratio and controls, kept current while a video is playing. */
    private fun updatePipParams(playing: Boolean, aspect: Float?) {
        if (!PipParamsFactory.supported(this)) return
        try {
            setPictureInPictureParams(PipParamsFactory.build(this, playing, aspect))
        } catch (e: IllegalArgumentException) {
            logPipError("Update picture-in-picture", e)
        } catch (e: IllegalStateException) {
            logPipError("Update picture-in-picture", e)
        }
    }

    /** A short step into the error log; no toast, no copy button. */
    private fun logPipError(step: String, e: Throwable) {
        ErrorLog.get(this).log(step = step, error = e, shown = null, media = null)
    }

    companion object {
        /** The video session's activity intent: open the app where the video is. */
        const val ActionOpenVideo = "com.shibi.musicplayer.action.OPEN_VIDEO"
    }
}
