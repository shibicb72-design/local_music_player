package com.shibi.musicplayer.playback

import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import com.shibi.musicplayer.domain.LikeShuffleQueue
import com.shibi.musicplayer.domain.QueueEdit
import kotlin.random.Random

/**
 * Runs the like-weighted shuffle on the session's player. With shuffle on, the player holds
 * [LikeShuffleQueue.window] (what played, in order, plus one queued draw) under an identity
 * shuffle order, so next, previous, the notification and the lock screen walk the real history.
 * Media3's own [ShuffleOrder] cannot do this: it is a permutation and never repeats a song.
 * Turning shuffle off puts the original queue back around the playing song.
 *
 * Every change is reconciled on the next main-loop turn, so edits made here never race the
 * events they cause. A playlist that no longer matches the window came from a controller and
 * starts a new shuffle from it.
 */
internal class LikeShuffleDriver(
    private val player: ExoPlayer,
    private val random: Random = Random.Default,
) : Player.Listener {
    private val handler = Handler(player.applicationLooper)
    private val reconcileTask = Runnable { reconcile() }
    private var queue: LikeShuffleQueue? = null
    private var pool: List<MediaItem> = emptyList()
    private var itemsById: Map<Long, MediaItem> = emptyMap()
    private var liked: Set<Long> = emptySet()
    private var newQueue = false

    fun attach() {
        player.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(player.mediaItemCount))
        player.addListener(this)
        scheduleReconcile()
    }

    fun release() {
        handler.removeCallbacks(reconcileTask)
        player.removeListener(this)
    }

    fun setLiked(ids: Set<Long>) {
        liked = ids
        val active = queue ?: return
        if (!newQueue && holdsWindow(active)) apply(active.onLikedChanged(ids))
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // The driver never replaces the playing item, so this is a controller's new queue,
        // even when it happens to match the window (re-tapping a song in a tiny playlist).
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) newQueue = true
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (
            events.containsAny(
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
            )
        ) {
            scheduleReconcile()
        }
    }

    private fun scheduleReconcile() {
        handler.removeCallbacks(reconcileTask)
        handler.post(reconcileTask)
    }

    private fun reconcile() {
        val active = queue?.takeIf { !newQueue && holdsWindow(it) }
        newQueue = false
        when {
            !player.shuffleModeEnabled -> {
                if (active != null) restorePool()
                queue = null
            }
            active != null -> apply(active.onCurrentIndex(player.currentMediaItemIndex))
            else -> start()
        }
    }

    private fun start() {
        queue = null
        val count = player.mediaItemCount
        val index = player.currentMediaItemIndex
        if (index !in 0 until count) return
        val items = List(count) { player.getMediaItemAt(it) }
        val ids = items.map { it.mediaId.toLongOrNull() ?: return }
        val started = LikeShuffleQueue(ids, ids[index], liked, random)
        pool = items
        itemsById = ids.zip(items).toMap()
        queue = started
        // Keep the playing item so playback never stutters; swap everything around it.
        keepOnlyCurrent(index, count)
        started.window.drop(1).forEach { player.addMediaItem(itemsById.getValue(it)) }
    }

    private fun restorePool() {
        val currentId = player.currentMediaItem?.mediaId
        val at = pool.indexOfFirst { it.mediaId == currentId }
        if (at < 0) return
        keepOnlyCurrent(player.currentMediaItemIndex, player.mediaItemCount)
        if (at > 0) player.addMediaItems(0, pool.subList(0, at))
        if (at < pool.lastIndex) player.addMediaItems(pool.subList(at + 1, pool.size))
    }

    private fun keepOnlyCurrent(index: Int, count: Int) {
        if (index < count - 1) player.removeMediaItems(index + 1, count)
        if (index > 0) player.removeMediaItems(0, index)
    }

    private fun apply(edit: QueueEdit?) {
        when (edit) {
            null -> Unit
            is QueueEdit.Append -> player.addMediaItem(itemsById.getValue(edit.id))
            is QueueEdit.Replace -> player.replaceMediaItem(edit.index, itemsById.getValue(edit.id))
            is QueueEdit.Remove -> player.removeMediaItem(edit.index)
        }
    }

    private fun holdsWindow(active: LikeShuffleQueue): Boolean {
        val window = active.window
        if (player.mediaItemCount != window.size) return false
        return window.indices.all { player.getMediaItemAt(it).mediaId == window[it].toString() }
    }
}
