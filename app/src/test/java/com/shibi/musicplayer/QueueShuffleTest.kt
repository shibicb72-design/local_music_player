package com.shibi.musicplayer

import com.shibi.musicplayer.domain.QueueShuffle
import com.shibi.musicplayer.domain.ShuffleBadge
import com.shibi.musicplayer.domain.ShuffleContext
import com.shibi.musicplayer.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueShuffleTest {
    private val tracks = listOf(
        track(1, "One"),
        track(2, "Two"),
        track(3, "Three"),
        track(4, "Four"),
        track(5, "Five"),
    )

    @Test
    fun badgeIsHiddenWhenShuffleIsOff() {
        assertNull(ShuffleBadge.label(enabled = false, ShuffleContext.ALL_SONGS))
        assertNull(ShuffleBadge.label(enabled = false, ShuffleContext.PLAYLIST))
    }

    @Test
    fun badgeMarksAllSongsVersusPlaylist() {
        assertEquals(ShuffleBadge.ALL, ShuffleBadge.label(true, ShuffleContext.ALL_SONGS))
        assertEquals(ShuffleBadge.PLAYLIST, ShuffleBadge.label(true, ShuffleContext.PLAYLIST))
    }

    @Test
    fun keepCurrentAndShuffleRest_leavesThePlayingTrackInPlace() {
        val shuffled = QueueShuffle.keepCurrentAndShuffleRest(tracks, currentIndex = 2, Random(7))

        assertEquals(5, shuffled.size)
        assertEquals(tracks[2], shuffled[2])
        assertEquals(tracks.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
        val differed = (0..40).any { seed ->
            QueueShuffle.keepCurrentAndShuffleRest(tracks, 2, Random(seed)) != tracks
        }
        assertTrue(differed)
    }

    @Test
    fun keepCurrentAndShuffleRest_isDeterministicForASeed() {
        val first = QueueShuffle.keepCurrentAndShuffleRest(tracks, 0, Random(99))
        val second = QueueShuffle.keepCurrentAndShuffleRest(tracks, 0, Random(99))
        assertEquals(first, second)
        assertEquals(tracks[0], first[0])
    }

    @Test
    fun keepCurrentAndShuffleRest_singleTrackIsUnchanged() {
        val one = listOf(tracks[0])
        assertEquals(one, QueueShuffle.keepCurrentAndShuffleRest(one, 0, Random(1)))
    }

    @Test
    fun playlistContextShufflesThePlaylistQueueNotTheLibrary() {
        val playlist = tracks.take(3)
        val shuffled = QueueShuffle.keepCurrentAndShuffleRest(playlist, 1, Random(3))
        assertEquals(3, shuffled.size)
        assertEquals(playlist[1], shuffled[1])
        assertTrue(shuffled.all { track -> playlist.any { it.id == track.id } })
    }

    private fun track(id: Long, title: String) = Track(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 180_000L,
        contentUri = "content://tracks/$id",
        albumArtUri = null,
    )
}
