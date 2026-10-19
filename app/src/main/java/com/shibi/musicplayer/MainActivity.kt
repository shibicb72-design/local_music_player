package com.shibi.musicplayer

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.musicplayer.background.BackgroundViewModel
import com.shibi.musicplayer.diagnostics.CrashSafetyNet
import com.shibi.musicplayer.diagnostics.ErrorLog
import com.shibi.musicplayer.domain.BackgroundPolicy
import com.shibi.musicplayer.domain.ExternalVideo
import com.shibi.musicplayer.domain.ExternalVideoRequest
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.playlist.PlaylistViewModel
import com.shibi.musicplayer.ui.MusicApp
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import com.shibi.musicplayer.ui.theme.backdropIsLight
import com.shibi.musicplayer.ui.theme.systemBarStyleFor
import com.shibi.musicplayer.video.FontConfigSetup
import com.shibi.musicplayer.video.FontWarmup
import com.shibi.musicplayer.video.PipParamsFactory
import com.shibi.musicplayer.video.VideoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** The same activity-scoped instance `MusicApp` gets from `viewModel()`. */
    private val videoViewModel: VideoViewModel by viewModels()

    /** Taps on the video's notification or lock-screen card (V8). */
    private val openVideoRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Videos opened from another app (Files, Gallery). A Channel, not a SharedFlow with replay,
     * so a video is played exactly once even when the composable's collector restarts.
     */
    private val externalVideos = Channel<Video>(Channel.BUFFERED)

    /**
     * Hoisted out of `setContent`: built there, every recomposition (a dark-theme change, say)
     * made a new Flow and restarted `MusicApp`'s collector.
     */
    private val externalVideoFlow = externalVideos.receiveAsFlow()

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

    /**
     * Re-applies edge-to-edge whenever the painted backdrop flips between light
     * and dark, so status and navigation icons follow the app, not the system.
     */
    @Composable
    private fun SystemBarIcons(
        darkTheme: Boolean,
        backgroundViewModel: BackgroundViewModel = viewModel(),
    ) {
        val background by backgroundViewModel.selection.collectAsStateWithLifecycle()
        val customColors by backgroundViewModel.customColors.collectAsStateWithLifecycle()
        val lightBackdrop = backdropIsLight(darkTheme, background, customColors)
        // Fine-tune sliders emit colours every frame; touch the window only on a flip.
        val applied = remember { BooleanArray(1) { false } }
        SideEffect {
            if (applied[0] != lightBackdrop) {
                applied[0] = lightBackdrop
                val style = systemBarStyleFor(lightBackdrop)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashSafetyNet.install(this)
        super.onCreate(savedInstanceState)
        Thread(
            { runCatching { CrashSafetyNet.recordExitReasons(applicationContext) } },
            "exit-reasons",
        ).start()
        FontConfigSetup.prepare(applicationContext)
        FontWarmup.startIfNeeded(applicationContext)
        // Cold-start default (dark theme, Default backdrop); SystemBarIcons takes over from here.
        val style = systemBarStyleFor(lightBackdrop = false)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        setContent {
            val playlistViewModel: PlaylistViewModel = viewModel()
            val darkTheme = playlistViewModel.darkTheme.collectAsStateWithLifecycle()
            SystemBarIcons(darkTheme = darkTheme.value)
            MusicPlayerTheme(darkTheme = darkTheme.value) {
                MusicApp(
                    playlistViewModel = playlistViewModel,
                    openVideoRequests = openVideoRequests,
                    externalVideos = externalVideoFlow,
                )
            }
        }
        if (savedInstanceState == null) handleExternalVideo(intent)
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
            combine(
                videoViewModel.uiState
                    .map { Triple(it.isActive, it.isPlaying, it.aspect) }
                    .distinctUntilChanged(),
                videoViewModel.playerBounds,
            ) { t, rect -> t to rect }
                .collect { (t, rect) -> updatePipParams(t.first && t.second, t.third, rect) }
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
        handleExternalVideo(intent)
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
                    PipParamsFactory.build(
                        this,
                        true,
                        videoViewModel.uiState.value.aspect,
                        videoViewModel.playerBounds.value,
                    ),
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
    private fun updatePipParams(playing: Boolean, aspect: Float?, sourceRect: Rect?) {
        if (!PipParamsFactory.supported(this)) return
        try {
            setPictureInPictureParams(PipParamsFactory.build(this, playing, aspect, sourceRect))
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

    /**
     * An "open with" request from another app (Files, Gallery). Only a content or file video is
     * played; nothing from the incoming intent is launched or forwarded. Rejections show a toast
     * and go to the error log (without the address).
     */
    private fun handleExternalVideo(intent: Intent?) {
        if (intent == null) return
        val request = ExternalVideo.parse(intent.action, intent.data?.toString(), intent.type)
            ?: return
        when (request) {
            is ExternalVideoRequest.Rejected -> {
                Toast.makeText(this, R.string.open_with_error, Toast.LENGTH_SHORT).show()
                ErrorLog.get(this).log(
                    step = "Open with Theatrum",
                    error = IllegalArgumentException(request.reason),
                    shown = getString(R.string.open_with_error),
                    media = null,
                )
            }

            is ExternalVideoRequest.Accepted -> {
                val uri = intent.data ?: return
                if (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (e: SecurityException) {
                        // The one-time grant still lets it play now.
                    }
                }
                val declaredType = intent.type
                lifecycleScope.launch(Dispatchers.IO) {
                    val video = readExternalVideo(uri, declaredType)
                    externalVideos.send(video)
                }
            }
        }
    }

    /** Reads the display name and size for an external address, then builds its [Video] row. */
    private fun readExternalVideo(uri: Uri, declaredType: String?): Video {
        var name: String? = null
        var size: Long? = null
        try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: SecurityException) {
            // No name or size; the video still plays.
        } catch (e: IllegalArgumentException) {
            // No name or size; the video still plays.
        } catch (e: UnsupportedOperationException) {
            // No name or size; the video still plays.
        }
        val type = declaredType ?: queryType(uri)
        return ExternalVideo.videoFor(uri.toString(), name, size, type)
    }

    /** The provider's own type for the address; null when it is not declared or not readable. */
    private fun queryType(uri: Uri): String? = try {
        contentResolver.getType(uri)
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: UnsupportedOperationException) {
        null
    }

    companion object {
        /** The video session's activity intent: open the app where the video is. */
        const val ActionOpenVideo = "com.shibi.musicplayer.action.OPEN_VIDEO"
    }
}

