package com.shibi.musicplayer.video

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** What the video's notification and lock screen show; [VideoSessionMapping.snapshotOf] builds it. */
data class VideoSessionSnapshot(
    val videoId: Long,
    val title: String,
    /** The video's `content://` URI; the session loads its thumbnail as the artwork. */
    val artworkUri: String,
    /** 0 while unknown. */
    val durationMs: Long,
    val status: VideoSessionStatus,
)

enum class VideoSessionStatus { Buffering, Playing, Paused }

/** Where the session's buttons go (the video view model). The position is read live. */
interface VideoSessionControls {
    val positionMs: Long
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)

    /** A controller's stop, or the paused notification swiped away: the video closes. */
    fun stop()
}

/**
 * The video's state in the session's terms. Pure. The session exists while a video is
 * loaded and has neither ended nor failed; the position stays out of the snapshot, so a
 * new one only comes with a new status, title or length (not 4 times a second).
 */
object VideoSessionMapping {

    /** The ±10 s of the notification, the lock screen and earbud seek buttons. */
    const val SeekIncrementMs = 10_000L

    fun snapshotOf(state: VideoUiState): VideoSessionSnapshot? {
        val video = state.video ?: return null
        val status = when (state.status) {
            VideoStatus.Playing -> VideoSessionStatus.Playing
            VideoStatus.Paused -> VideoSessionStatus.Paused
            // Idle only passes between two inputs (a reopen); the video is still loading.
            VideoStatus.Buffering, VideoStatus.Idle -> VideoSessionStatus.Buffering
            VideoStatus.Ended, VideoStatus.Error -> return null
        }
        return VideoSessionSnapshot(
            videoId = video.id,
            title = video.title,
            artworkUri = video.contentUri,
            durationMs = state.durationMs.coerceAtLeast(0L),
            status = status,
        )
    }

    /** Play/pause, seek (also ±10 s), stop, and what is playing; no playlist, speed or volume. */
    val AvailableCommands: Player.Commands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_RELEASE,
        )
        .build()

    fun metadataOf(snapshot: VideoSessionSnapshot): MediaMetadata = MediaMetadata.Builder()
        .setTitle(snapshot.title)
        .setDisplayTitle(snapshot.title)
        .setArtworkUri(snapshot.artworkUri.toUri())
        .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .apply { if (snapshot.durationMs > 0L) setDurationMs(snapshot.durationMs) }
        .build()
}

/**
 * libVLC as a Media3 [Player] for the video's own `MediaSession`, so earbud and watch buttons,
 * the notification and the lock screen control the video (not the music) while one is loaded.
 * It holds no engine itself: [show] gives it the view model's state, and every command goes
 * back through [controls], so the in-app and the session paths are one path. Main thread only.
 */
@OptIn(UnstableApi::class)
internal class VlcSessionPlayer(
    looper: Looper,
    private val controls: VideoSessionControls,
) : SimpleBasePlayer(looper) {

    private var snapshot: VideoSessionSnapshot? = null

    /** A play or pause pressed here, shown until the engine's next state confirms it. */
    private var requestedPlayWhenReady: Boolean? = null

    private val handler = Handler(looper)

    fun show(next: VideoSessionSnapshot) {
        if (next == snapshot && requestedPlayWhenReady == null) return
        snapshot = next
        requestedPlayWhenReady = null
        invalidateState()
    }

    override fun getState(): State {
        val shown = snapshot
        val builder = State.Builder()
            .setSeekBackIncrementMs(VideoSessionMapping.SeekIncrementMs)
            .setSeekForwardIncrementMs(VideoSessionMapping.SeekIncrementMs)
        if (shown == null) {
            return builder
                .setAvailableCommands(Player.Commands.EMPTY)
                .setPlaybackState(Player.STATE_IDLE)
                .build()
        }
        val item = MediaItem.Builder()
            .setMediaId(shown.videoId.toString())
            .setMediaMetadata(VideoSessionMapping.metadataOf(shown))
            .build()
        val playWhenReady = requestedPlayWhenReady ?: (shown.status != VideoSessionStatus.Paused)
        return builder
            .setAvailableCommands(VideoSessionMapping.AvailableCommands)
            .setPlaylist(
                listOf(
                    MediaItemData.Builder(shown.videoId)
                        .setMediaItem(item)
                        .setDurationUs(if (shown.durationMs > 0L) shown.durationMs * 1_000L else C.TIME_UNSET)
                        .setIsSeekable(true)
                        .build(),
                ),
            )
            .setCurrentMediaItemIndex(0)
            .setPlaybackState(
                if (shown.status == VideoSessionStatus.Buffering) Player.STATE_BUFFERING else Player.STATE_READY,
            )
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs { controls.positionMs }
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        requestedPlayWhenReady = playWhenReady
        if (playWhenReady) controls.play() else controls.pause()
        // An engine that never confirms (it failed, or ignored the call) must not leave the
        // notification showing the request: after a moment, the engine's state is the truth.
        val request = playWhenReady
        handler.postDelayed({
            if (requestedPlayWhenReady == request) {
                requestedPlayWhenReady = null
                invalidateState()
            }
        }, RequestTimeoutMs)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        controls.seekTo(if (positionMs == C.TIME_UNSET) 0L else positionMs.coerceAtLeast(0L))
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        // Closing the video releases this player; not from inside its own stop().
        handler.post { controls.stop() }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        handler.removeCallbacksAndMessages(null)
        return Futures.immediateVoidFuture()
    }

    private companion object {
        const val RequestTimeoutMs = 2_000L
    }
}
