package com.shibi.musicplayer

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * A media3 player that only keeps state (the queue, play/pause, shuffle, repeat), for
 * real-activity tests where Robolectric cannot bind the playback service. [playRequests] counts
 * every time anything asked it to play.
 */
@OptIn(UnstableApi::class)
class FakeMusicPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    private var items: List<MediaItem> = emptyList()
    private var index = 0
    private var playWhenReady = false
    private var prepared = false
    private var shuffle = false
    private var repeat = Player.REPEAT_MODE_OFF

    var playRequests = 0
        private set

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (prepared && items.isNotEmpty()) Player.STATE_READY else Player.STATE_IDLE)
            .setShuffleModeEnabled(shuffle)
            .setRepeatMode(repeat)
            .setPlaylist(
                items.mapIndexed { i, item ->
                    MediaItemData.Builder(i).setMediaItem(item).setDurationUs(200_000_000L).build()
                },
            )
        if (items.isNotEmpty()) builder.setCurrentMediaItemIndex(index)
        return builder.build()
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        items = mediaItems.toList()
        index = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        return done()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        prepared = true
        return done()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) playRequests++
        this.playWhenReady = playWhenReady
        return done()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        shuffle = shuffleModeEnabled
        return done()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        repeat = repeatMode
        return done()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (mediaItemIndex != C.INDEX_UNSET) index = mediaItemIndex
        return done()
    }

    override fun handleStop(): ListenableFuture<*> {
        prepared = false
        playWhenReady = false
        return done()
    }

    override fun handleRelease(): ListenableFuture<*> = done()

    private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()
}
