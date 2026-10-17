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
import com.shibi.musicplayer.domain.RepeatCounter
import com.shibi.musicplayer.domain.RepeatMode
import com.shibi.musicplayer.domain.RepeatState
import com.shibi.musicplayer.domain.ShuffleContext
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
    private var pendingContext: ShuffleContext = ShuffleContext.ALL_SONGS
    private var progressJob: Job? = null
    private var shuffleEnabled: Boolean = false
    private var shuffleContext: ShuffleContext = ShuffleContext.ALL_SONGS
    private var repeatState: RepeatState = RepeatState()
    private var applyingPlayerMode: Boolean = false

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _preferredOutputId = MutableStateFlow<Int?>(null)
    val preferredOutputId: StateFlow<Int?> = _preferredOutputId.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            val player = controller ?: return
            when (reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> {
                    repeatState = RepeatCounter.onSameItemCompleted(repeatState)
                    applyRepeatToPlayer(player)
                }
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
                -> {
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                        repeatState = RepeatCounter.onCurrentItemChanged(repeatState)
                        applyRepeatToPlayer(player)
                    }
                }
            }
            publish(player)
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) && !applyingPlayerMode) {
                shuffleEnabled = player.shuffleModeEnabled
            }
            if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED) && !applyingPlayerMode) {
                syncRepeatFromPlayer(player)
            }
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
                    val context = pendingContext
                    pendingTracks = null
                    pendingTrackId = null
                    if (trackId != null) {
                        startPlayback(connected, tracks, trackId, context)
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

    fun playTrack(
        tracks: List<Track>,
        trackId: Long,
        context: ShuffleContext = ShuffleContext.ALL_SONGS,
    ) {
        val player = controller
        if (player == null) {
            pendingTracks = tracks
            pendingTrackId = trackId
            pendingContext = context
            return
        }
        startPlayback(player, tracks, trackId, context)
    }

    /** The next [playTrack] starts with shuffle on, as a playlist's Shuffle button expects. */
    fun shuffleNextQueue() {
        shuffleEnabled = true
    }

    fun toggleShuffle() {
        val player = controller ?: return
        shuffleEnabled = !shuffleEnabled
        applyingPlayerMode = true
        player.shuffleModeEnabled = shuffleEnabled
        applyingPlayerMode = false
        publish(player)
    }

    fun cycleRepeat() {
        val player = controller ?: return
        repeatState = RepeatCounter.cycle(repeatState)
        applyRepeatToPlayer(player)
        publish(player)
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
        if (!player.hasNextMediaItem()) return
        player.seekToNextMediaItem()
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
            is QueueMove.SkipTo -> {
                if (player.shuffleModeEnabled) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekTo(move.index, 0L)
                }
            }
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

    private fun startPlayback(
        player: MediaController,
        tracks: List<Track>,
        trackId: Long,
        context: ShuffleContext,
    ) {
        shuffleContext = context
        val queue = PlaybackQueue.startingAt(tracks, trackId)
        player.setMediaItems(
            queue.tracks.map { it.toMediaItem() },
            queue.currentIndex,
            0L,
        )
        applyingPlayerMode = true
        player.shuffleModeEnabled = shuffleEnabled
        applyRepeatToPlayer(player)
        applyingPlayerMode = false
        player.prepare()
        player.play()
        publish(player)
    }

    private fun applyRepeatToPlayer(player: Player) {
        applyingPlayerMode = true
        player.repeatMode = if (repeatState.repeatsCurrentItem) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
        applyingPlayerMode = false
    }

    private fun syncRepeatFromPlayer(player: Player) {
        when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> {
                if (repeatState.mode != RepeatMode.OFF) {
                    repeatState = RepeatState()
                }
            }
            Player.REPEAT_MODE_ONE,
            Player.REPEAT_MODE_ALL,
            -> {
                if (repeatState.mode == RepeatMode.OFF) {
                    repeatState = RepeatCounter.start(RepeatMode.INFINITE)
                    if (player.repeatMode == Player.REPEAT_MODE_ALL) {
                        applyRepeatToPlayer(player)
                    }
                }
            }
        }
    }

    private fun publish(player: Player) {
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val rawPosition = player.currentPosition.coerceAtLeast(0L)
        val position = if (duration > 0L) rawPosition.coerceAtMost(duration) else rawPosition
        val index = player.currentMediaItemIndex
        val count = player.mediaItemCount
        val metadata = player.mediaMetadata
        _uiState.value = PlayerUiState(
            currentMediaId = player.currentMediaItem?.mediaId,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            albumArtUri = metadata.artworkUri?.toString(),
            isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
            positionMs = position,
            durationMs = duration,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = count > 0 && index >= 0,
            hasMedia = count > 0 && index in 0 until count,
            isConnected = true,
            shuffleEnabled = shuffleEnabled,
            shuffleContext = shuffleContext,
            repeatMode = repeatState.mode,
        )
    }

    private companion object {
        const val POSITION_POLL_MS = 500L
    }
}
