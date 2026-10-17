package com.shibi.musicplayer

import com.shibi.musicplayer.domain.LikeShufflePicker
import com.shibi.musicplayer.domain.LikeShuffleQueue
import com.shibi.musicplayer.domain.LikeShuffleTuning
import com.shibi.musicplayer.domain.QueueEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LikeShuffleTest {
    private val library = (1L..40L).toList()
    private val liked = setOf(3L, 7L, 12L, 18L, 25L, 31L, 36L, 40L)

    @Test
    fun minimumGap_scalesWithThePoolAndShrinksForSmallOnes() {
        assertEquals(0, LikeShuffleTuning.minimumGap(1))
        assertEquals(1, LikeShuffleTuning.minimumGap(2))
        assertEquals(1, LikeShuffleTuning.minimumGap(3))
        assertEquals(2, LikeShuffleTuning.minimumGap(4))
        assertEquals(3, LikeShuffleTuning.minimumGap(6))
        assertEquals(3, LikeShuffleTuning.minimumGap(10))
        assertEquals(10, LikeShuffleTuning.minimumGap(40))
        assertEquals(LikeShuffleTuning.MIN_GAP_CAP, LikeShuffleTuning.minimumGap(500))
    }

    @Test
    fun likedSongsPlayMoreOftenThanUnliked() {
        val counts = HashMap<Long, Int>()
        val passes = 300
        repeat(passes) { seed ->
            playPass(library, liked, seed).forEach { counts.merge(it, 1, Int::plus) }
        }
        val likedPerPass = liked.sumOf { counts.getValue(it) }.toDouble() / liked.size / passes
        val unlikedPerPass = (library - liked).sumOf { counts.getValue(it) }.toDouble() /
            (library.size - liked.size) / passes
        assertEquals(1.0, unlikedPerPass, 0.0)
        assertTrue("liked plays per pass $likedPerPass", likedPerPass in 1.4..4.0)
    }

    @Test
    fun likedSongsAlsoComeEarlierInThePass() {
        var likedEarly = 0
        var early = 0
        repeat(400) { seed ->
            val pass = playPass(library, liked, seed)
            pass.drop(1).take(10).forEach { id ->
                early++
                if (id in liked) likedEarly++
            }
        }
        // Uniform would be 8 / 40 = 20%.
        val share = likedEarly.toDouble() / early
        assertTrue("liked share of early picks $share", share in 0.32..0.6)
    }

    @Test
    fun neverTheSameSongBackToBack() {
        val pools = listOf(library, library.take(2), library.take(3), library.take(5))
        pools.forEach { pool ->
            repeat(200) { seed ->
                val pass = playPass(pool, pool.toSet(), seed)
                pass.zipWithNext().forEach { (a, b) -> assertTrue("$a then $b in $pass", a != b) }
            }
        }
    }

    @Test
    fun likedSongsRespectTheMinimumGapAndReturnAtVaryingPoints() {
        val gap = LikeShuffleTuning.minimumGap(library.size)
        val observed = mutableListOf<Int>()
        repeat(300) { seed ->
            val pass = playPass(library, liked, seed)
            liked.forEach { id ->
                val slots = pass.indices.filter { pass[it] == id }
                slots.zipWithNext().forEach { (first, second) -> observed += second - first - 1 }
            }
        }
        assertTrue(observed.size > 100)
        assertTrue("smallest gap ${observed.min()} < $gap", observed.min() >= gap)
        assertTrue("returns cluster on ${observed.toSet()}", observed.toSet().size >= 10)
        assertTrue(observed.max() >= 2 * gap)
    }

    @Test
    fun unlikedSongsPlayExactlyOncePerPassAndLikedAtLeastOnce() {
        repeat(100) { seed ->
            val counts = playPass(library, liked, seed).groupingBy { it }.eachCount()
            (library - liked).forEach { assertEquals(1, counts[it]) }
            liked.forEach { assertTrue((counts[it] ?: 0) >= 1) }
        }
    }

    @Test
    fun withNoLikesItIsAPlainShuffle() {
        val orders = (0 until 20).map { seed -> playPass(library, emptySet(), seed) }
        orders.forEach { assertEquals(library.toSet(), it.toSet()); assertEquals(library.size, it.size) }
        assertTrue(orders.toSet().size > 1)
    }

    @Test
    fun onlyPlaylistSongsAreEligible() {
        val playlist = listOf(3L, 9L, 12L, 21L, 30L, 33L)
        repeat(100) { seed ->
            val pass = playPass(playlist, liked, seed)
            assertTrue(pass.all { it in playlist })
        }
    }

    @Test
    fun singleSongPoolPlaysOnceAndEnds() {
        listOf(emptySet(), setOf(5L)).forEach { likes ->
            val queue = LikeShuffleQueue(listOf(5L), 5L, likes, Random(1))
            assertEquals(listOf(5L), queue.window)
            assertNull(queue.onCurrentIndex(0))
            assertNull(queue.onLikedChanged(setOf(5L)))
        }
    }

    @Test
    fun twoAndThreeSongPoolsAlternateWithoutBackToBack() {
        repeat(50) { seed ->
            val two = playPass(listOf(1L, 2L), setOf(1L, 2L), seed)
            assertEquals(setOf(1L, 2L), two.toSet())
            val three = playPass(listOf(1L, 2L, 3L), setOf(1L, 2L, 3L), seed)
            assertEquals(setOf(1L, 2L, 3L), three.toSet())
            three.zipWithNext().forEach { (a, b) -> assertTrue(a != b) }
            val noneLiked = playPass(listOf(1L, 2L, 3L), emptySet(), seed)
            assertEquals(3, noneLiked.size)
        }
    }

    @Test
    fun allLikedPoolStillPlaysEverySongAndSpacesThemOut() {
        val pool = library.take(12)
        val gap = LikeShuffleTuning.minimumGap(pool.size)
        repeat(100) { seed ->
            val pass = playPass(pool, pool.toSet(), seed)
            assertEquals(pool.toSet(), pass.toSet())
            pool.forEach { id ->
                val slots = pass.indices.filter { pass[it] == id }
                slots.zipWithNext().forEach { (a, b) -> assertTrue(b - a - 1 >= gap) }
            }
        }
    }

    @Test
    fun likingMidSessionLetsAPlayedSongReturn() {
        val pool = library.take(20)
        var returned = 0
        repeat(100) { seed ->
            val queue = LikeShuffleQueue(pool, pool.first(), emptySet(), Random(seed))
            queue.onLikedChanged(setOf(pool.first()))
            playOut(queue)
            if (queue.window.count { it == pool.first() } > 1) returned++
        }
        assertTrue("returned in $returned of 100", returned > 50)
    }

    @Test
    fun unlikingMidSessionStopsReturns() {
        val pool = library.take(20)
        repeat(100) { seed ->
            val queue = LikeShuffleQueue(pool, pool.first(), setOf(pool.first()), Random(seed))
            queue.onLikedChanged(emptySet())
            playOut(queue)
            assertEquals(1, queue.window.count { it == pool.first() })
        }
    }

    @Test
    fun likeChangeRedrawsOnlyTheQueuedSong() {
        val queue = LikeShuffleQueue(library, 1L, emptySet(), Random(4))
        queue.onCurrentIndex(1)
        val history = queue.window.dropLast(1)
        val edits = (0 until 20).mapNotNull { queue.onLikedChanged(setOf(it.toLong() + 2)) }
        assertTrue(edits.isNotEmpty())
        edits.forEach { edit ->
            assertTrue(edit is QueueEdit.Replace && edit.index == 2)
        }
        assertEquals(history, queue.window.dropLast(1))
        assertEquals(3, queue.window.size)
    }

    @Test
    fun repeatsAndStepsBackDoNotCountAsNewPlays() {
        val queue = LikeShuffleQueue(library, 1L, liked, Random(8))
        assertTrue(queue.onCurrentIndex(1) is QueueEdit.Append)
        assertTrue(queue.onCurrentIndex(2) is QueueEdit.Append)
        val window = queue.window
        val plays = queue.plays
        // Repeat-one replays index 2; previous goes back to 1; next returns to 2.
        assertNull(queue.onCurrentIndex(2))
        assertNull(queue.onCurrentIndex(1))
        assertNull(queue.onCurrentIndex(2))
        assertEquals(window, queue.window)
        assertEquals(plays, queue.plays)
        // Previous lands on the song that actually played before.
        assertEquals(1L, queue.window[0])
    }

    @Test
    fun theSameSeedGivesTheSamePass() {
        assertEquals(playPass(library, liked, 42), playPass(library, liked, 42))
        assertFalse(playPass(library, liked, 42) == playPass(library, liked, 43))
    }

    @Test
    fun aFreshPickerFavorsLikedStarts() {
        var likedStarts = 0
        repeat(2_000) { seed ->
            if (LikeShufflePicker(library, liked, Random(seed)).draw() in liked) likedStarts++
        }
        // 8 liked at weight 3 against 32 unliked at weight 1: 24 / 56, about 43%.
        assertTrue("liked starts $likedStarts", likedStarts in 740..1_000)
    }

    private fun playPass(pool: List<Long>, liked: Set<Long>, seed: Int): List<Long> {
        val random = Random(seed)
        val queue = LikeShuffleQueue(pool, pool[random.nextInt(pool.size)], liked, random)
        playOut(queue)
        return queue.window
    }

    private fun playOut(queue: LikeShuffleQueue) {
        var index = 0
        while (index < queue.window.lastIndex) {
            index++
            queue.onCurrentIndex(index)
            check(index < 10_000) { "pass never ended" }
        }
    }
}
