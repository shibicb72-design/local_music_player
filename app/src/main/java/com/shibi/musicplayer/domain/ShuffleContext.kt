package com.shibi.musicplayer.domain

import kotlin.random.Random

/** Where the current queue was started from. Drives the shuffle badge. */
enum class ShuffleContext {
    ALL_SONGS,
    PLAYLIST,
}

object ShuffleBadge {
    const val ALL = 'A'
    const val PLAYLIST = 'P'

    fun label(enabled: Boolean, context: ShuffleContext): Char? {
        if (!enabled) return null
        return when (context) {
            ShuffleContext.ALL_SONGS -> ALL
            ShuffleContext.PLAYLIST -> PLAYLIST
        }
    }
}

/**
 * Shuffle the current queue without moving the playing track.
 * Upcoming and already-played items are reshuffled around it.
 */
object QueueShuffle {
    fun keepCurrentAndShuffleRest(
        tracks: List<Track>,
        currentIndex: Int,
        random: Random = Random.Default,
    ): List<Track> {
        require(currentIndex in tracks.indices) { "currentIndex $currentIndex is outside the queue" }
        if (tracks.size <= 1) return tracks.toList()
        val current = tracks[currentIndex]
        val rest = tracks.filterIndexed { index, _ -> index != currentIndex }.shuffled(random)
        return rest.take(currentIndex) + current + rest.drop(currentIndex)
    }
}
