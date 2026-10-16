package com.shibi.musicplayer

import com.shibi.musicplayer.domain.PlaybackQueue
import com.shibi.musicplayer.domain.QueueMove
import com.shibi.musicplayer.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueTest {
    private val first = track(1, "First")
    private val second = track(2, "Second")
    private val third = track(3, "Third")
    private val tracks = listOf(first, second, third)

    @Test
    fun startingAt_selectsTheRequestedTrackAndCopiesTheList() {
        val source = mutableListOf(first, second, third)
        val queue = PlaybackQueue.startingAt(source, trackId = 2)

        source.clear()

        assertEquals(1, queue.currentIndex)
        assertEquals(second, queue.current)
        assertEquals(listOf(first, second, third), queue.tracks)
    }

    @Test
    fun startingAt_rejectsAMissingTrack() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueue.startingAt(tracks, trackId = 99)
        }
    }

    @Test
    fun of_rejectsAnEmptyListAndAnOutOfRangeIndex() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueue.of(emptyList(), 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackQueue.of(tracks, 3)
        }
    }

    @Test
    fun emptyQueue_hasNoCurrentTrack() {
        val queue = PlaybackQueue.empty()

        assertNull(queue.current)
        assertFalse(queue.hasNext())
        assertSame(queue, queue.next())
        assertEquals(QueueMove.Stay, queue.previousAction(positionMs = 0L))
    }

    @Test
    fun next_advancesUntilTheLastTrackAndThenStays() {
        val start = PlaybackQueue.of(tracks, 0)
        val middle = start.next()
        val end = middle.next()

        assertEquals(1, middle.currentIndex)
        assertEquals(second, middle.current)
        assertEquals(2, end.currentIndex)
        assertFalse(end.hasNext())
        assertSame(end, end.next())
        assertEquals(listOf(first, second, third), start.tracks)
        assertEquals(0, start.currentIndex)
    }

    @Test
    fun nextIndex_matchesQueueAdvancement() {
        assertEquals(1, PlaybackQueue.nextIndex(currentIndex = 0, size = 3))
        assertNull(PlaybackQueue.nextIndex(currentIndex = 2, size = 3))
        assertNull(PlaybackQueue.nextIndex(currentIndex = 0, size = 1))
        assertNull(PlaybackQueue.nextIndex(currentIndex = 0, size = 0))
        assertNull(PlaybackQueue.nextIndex(currentIndex = -1, size = 3))
    }

    @Test
    fun previous_restartsTheCurrentTrackAfterTheThreshold() {
        val queue = PlaybackQueue.of(tracks, 1)

        assertEquals(
            QueueMove.RestartCurrent,
            queue.previousAction(positionMs = PlaybackQueue.RESTART_THRESHOLD_MS),
        )
        assertEquals(QueueMove.RestartCurrent, queue.previousAction(positionMs = 12_000L))
    }

    @Test
    fun previous_skipsToThePriorTrackBeforeTheThreshold() {
        val queue = PlaybackQueue.of(tracks, 2)

        assertEquals(QueueMove.SkipTo(1), queue.previousAction(positionMs = 2_999L))
        assertEquals(QueueMove.SkipTo(1), queue.previousAction(positionMs = 0L))
    }

    @Test
    fun previous_restartsTheFirstTrackWhenNothingPrecedesIt() {
        val queue = PlaybackQueue.of(tracks, 0)

        assertEquals(QueueMove.RestartCurrent, queue.previousAction(positionMs = 0L))
        assertEquals(QueueMove.RestartCurrent, queue.previousAction(positionMs = 500L))
    }

    @Test
    fun previousMove_staysPutForAnInvalidPlayerIndex() {
        assertEquals(
            QueueMove.Stay,
            PlaybackQueue.previousMove(currentIndex = -1, size = 3, positionMs = 0L),
        )
        assertEquals(
            QueueMove.Stay,
            PlaybackQueue.previousMove(currentIndex = 1, size = 0, positionMs = 0L),
        )
    }

    @Test
    fun singleTrack_canRestartAndCannotAdvance() {
        val queue = PlaybackQueue.of(listOf(first), 0)

        assertFalse(queue.hasNext())
        assertSame(queue, queue.next())
        assertEquals(QueueMove.RestartCurrent, queue.previousAction(positionMs = 100L))
        assertTrue(queue.current == first)
    }

    private fun track(id: Long, title: String) = Track(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 180_000L,
        contentUri = "content://media/external/audio/media/$id",
        albumArtUri = null,
    )
}
