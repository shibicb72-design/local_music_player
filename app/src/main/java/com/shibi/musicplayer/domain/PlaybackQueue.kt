package com.shibi.musicplayer.domain

@ConsistentCopyVisibility
data class PlaybackQueue private constructor(
    val tracks: List<Track>,
    val currentIndex: Int,
) {
    init {
        if (tracks.isEmpty()) {
            require(currentIndex == EMPTY_INDEX) {
                "An empty queue has no current index"
            }
        } else {
            require(currentIndex in tracks.indices) {
                "currentIndex $currentIndex is outside 0..${tracks.lastIndex}"
            }
        }
    }

    val current: Track?
        get() = tracks.getOrNull(currentIndex)

    fun hasNext(): Boolean = nextIndex(currentIndex, tracks.size) != null

    fun next(): PlaybackQueue {
        val index = nextIndex(currentIndex, tracks.size) ?: return this
        return copy(currentIndex = index)
    }

    fun previousAction(
        positionMs: Long,
        restartThresholdMs: Long = RESTART_THRESHOLD_MS,
    ): QueueMove = previousMove(
        currentIndex = currentIndex,
        size = tracks.size,
        positionMs = positionMs,
        restartThresholdMs = restartThresholdMs,
    )

    companion object {
        const val EMPTY_INDEX = -1
        const val RESTART_THRESHOLD_MS = 3_000L

        fun empty(): PlaybackQueue = PlaybackQueue(emptyList(), EMPTY_INDEX)

        fun of(tracks: List<Track>, startIndex: Int): PlaybackQueue {
            require(tracks.isNotEmpty()) { "Queue needs at least one track" }
            return PlaybackQueue(tracks.toList(), startIndex)
        }

        fun startingAt(tracks: List<Track>, trackId: Long): PlaybackQueue {
            val index = tracks.indexOfFirst { it.id == trackId }
            require(index >= 0) { "Track $trackId is not in the queue" }
            return of(tracks, index)
        }

        fun nextIndex(currentIndex: Int, size: Int): Int? {
            if (size <= 0 || currentIndex !in 0 until size - 1) return null
            return currentIndex + 1
        }

        fun previousMove(
            currentIndex: Int,
            size: Int,
            positionMs: Long,
            restartThresholdMs: Long = RESTART_THRESHOLD_MS,
        ): QueueMove {
            if (size <= 0 || currentIndex !in 0 until size) return QueueMove.Stay
            if (positionMs >= restartThresholdMs || currentIndex == 0) {
                return QueueMove.RestartCurrent
            }
            return QueueMove.SkipTo(currentIndex - 1)
        }
    }
}

sealed interface QueueMove {
    data object Stay : QueueMove

    data object RestartCurrent : QueueMove

    data class SkipTo(val index: Int) : QueueMove
}
