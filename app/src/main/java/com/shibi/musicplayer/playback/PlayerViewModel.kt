package com.shibi.musicplayer.playback

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.shibi.musicplayer.domain.PlaybackQueue
import com.shibi.musicplayer.domain.PlaybackTime
import com.shibi.musicplayer.domain.QueueMove
import com.shibi.musicplayer.domain.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionToken = SessionToken(
        application,
        ComponentName(application, PlaybackService::class.java),
    )
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(application, sessionToken).buildAsync()

    private var controller: MediaController? = null
    private var pendingTracks: List<Track>? = null
    private var pendingTrackId: Long? = null
    private var progressJob: Job? = null

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _preferredOutputId = MutableStateFlow<Int?>(null)
    val preferredOutputId: StateFlow<Int?> = _preferredOutputId.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }
    }

    init {
        controllerFuture.addListener(
            {
                val connected = runCatching { controllerFuture.get() }.getOrNull() ?: return@addListener
                controller = connected
                connected.addListener(playerListener)
                pendingTracks?.let { tracks ->
                    val trackId = pendingTrackId
                    pendingTracks = null
                    pendingTrackId = null
                    if (trackId != null) {
                        startPlayback(connected, tracks, trackId)
                    }
                }
                publish(connected)
            },
            ContextCompat.getMainExecutor(application),
        )
        progressJob = viewModelScope.launch {
            while (isActive) {
                val player = controller
                if (player != null && player.isPlaying) {
                    publish(player)
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    fun playTrack(tracks: List<Track>, trackId: Long) {
        val player = controller
        if (player == null) {
            pendingTracks = tracks
            pendingTrackId = trackId
            return
        }
        startPlayback(player, tracks, trackId)
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            player.pause()
        } else {
            player.play()
        }
        publish(player)
    }

    fun skipNext() {
        val player = controller ?: return
        val target = PlaybackQueue.nextIndex(player.currentMediaItemIndex, player.mediaItemCount) ?: return
        player.seekTo(target, 0L)
        publish(player)
    }

    fun skipPrevious() {
        val player = controller ?: return
        when (
            val move = PlaybackQueue.previousMove(
                currentIndex = player.currentMediaItemIndex,
                size = player.mediaItemCount,
                positionMs = player.currentPosition,
            )
        ) {
            QueueMove.Stay -> Unit
            QueueMove.RestartCurrent -> player.seekTo(0L)
            is QueueMove.SkipTo -> player.seekTo(move.index, 0L)
        }
        publish(player)
    }

    fun seekTo(positionMs: Long) {
        val player = controller ?: return
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        player.seekTo(PlaybackTime.clampSeek(positionMs, duration))
        publish(player)
    }

    fun setOutputDevice(deviceId: Int?) {
        _preferredOutputId.value = deviceId
        controller?.sendCustomCommand(
            OutputCommands.SET_OUTPUT,
            Bundle().apply { putInt(OutputCommands.EXTRA_DEVICE_ID, deviceId ?: -1) },
        )
    }

    override fun onCleared() {
        progressJob?.cancel()
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
        super.onCleared()
    }

    private fun startPlayback(player: MediaController, tracks: List<Track>, trackId: Long) {
        val queue = PlaybackQueue.startingAt(tracks, trackId)
        player.setMediaItems(
            queue.tracks.map { it.toMediaItem() },
            queue.currentIndex,
            0L,
        )
        player.prepare()
        player.play()
        publish(player)
    }

    private fun publish(player: Player) {
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val rawPosition = player.currentPosition.coerceAtLeast(0L)
        val position = if (duration > 0L) rawPosition.coerceAtMost(duration) else rawPosition
        val index = player.currentMediaItemIndex
        val count = player.mediaItemCount
        val metadata = player.mediaMetadata
        _uiState.value = PlayerUiState(
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            albumArtUri = metadata.artworkUri?.toString(),
            isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
            positionMs = position,
            durationMs = duration,
            hasNext = PlaybackQueue.nextIndex(index, count) != null,
            hasPrevious = count > 0 && index >= 0,
            hasMedia = count > 0 && index in 0 until count,
            isConnected = true,
        )
    }

    private companion object {
        const val POSITION_POLL_MS = 500L
    }
}
