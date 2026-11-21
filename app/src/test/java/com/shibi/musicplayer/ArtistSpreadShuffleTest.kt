package com.shibi.musicplayer

import com.shibi.musicplayer.domain.LikeShuffleQueue
import com.shibi.musicplayer.domain.SpreadKey
import com.shibi.musicplayer.domain.UnknownNames
import com.shibi.musicplayer.domain.spreadKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ArtistSpreadShuffleTest {
    private val unknown = UnknownNames(title = "Unknown title", artist = "Unknown artist", album = "Unknown album")

    /** 5 artists with 6 songs each, two albums per artist. */
    private val fiveBySix: Map<Long, SpreadKey> = (0 until 30).associate { index ->
        val artist = index / 6
        (index + 1L) to SpreadKey("artist $artist", "artist $artist\u0000album ${index % 2}")
    }

    @Test
    fun theSameArtistNeverPlaysTwiceInARowWhileAnotherArtistIsLeft() {
        var backToBack = 0
        repeat(500) { seed ->
            val pass = playPass(fiveBySix, emptySet(), seed)
            assertEquals(fiveBySix.keys, pass.toSet())
            assertEquals(fiveBySix.size, pass.size)
            backToBack += forcedBackToBack(pass, fiveBySix)
        }
        // A plain shuffled() of the same pool gives about 2500.
        println("ArtistSpread back-to-back same artist over 500 passes: $backToBack")
        assertTrue("back-to-back same artist $backToBack", backToBack <= 15)
    }

    @Test
    fun sameArtistSongsArePlayedFurtherApartThanAPlainShuffle() {
        var spreadNear = 0
        var spreadAll = 0
        var plainNear = 0
        var plainAll = 0
        repeat(500) { seed ->
            val (near, all) = nearGaps(playPass(fiveBySix, emptySet(), seed), fiveBySix)
            spreadNear += near
            spreadAll += all
            val (plainN, plainA) = nearGaps(fiveBySix.keys.shuffled(Random(seed)), fiveBySix)
            plainNear += plainN
            plainAll += plainA
        }
        val spread = spreadNear.toDouble() / spreadAll
        val plain = plainNear.toDouble() / plainAll
        println("ArtistSpread same-artist gap<=2 share: spread $spread, plain $plain")
        assertTrue("gap<=2 share $spread against plain $plain", spread <= plain / 2)
    }

    @Test
    fun likedSongsStillComeEarlierWithArtistSpread() {
        val library = (1L..40L).toList()
        val liked = setOf(3L, 7L, 12L, 18L, 25L, 31L, 36L, 40L)
        val keys = library.associateWith { SpreadKey("artist ${it % 5}", null) }
        var likedEarly = 0
        var early = 0
        repeat(400) { seed ->
            playPass(keys, liked, seed).drop(1).take(10).forEach { id ->
                early++
                if (id in liked) likedEarly++
            }
        }
        val share = likedEarly.toDouble() / early
        assertTrue("liked share of early picks $share", share in 0.32..0.6)
    }

    @Test
    fun oneArtistWithMostOfThePoolStillPlaysEverySongOnce() {
        val keys = (1L..8L).associateWith { SpreadKey("big", null) } +
            mapOf(9L to SpreadKey("small one", null), 10L to SpreadKey("small two", null))
        repeat(300) { seed ->
            val pass = playPass(keys, emptySet(), seed)
            assertEquals(keys.keys.toList().sorted(), pass.sorted())
            forcedBackToBack(pass, keys)
        }
    }

    @Test
    fun untaggedSongsNeitherCrowdNorStarveTheStart() {
        val keys = fiveBySix + (101L..110L).associateWith { SpreadKey(null, null) }
        var untagged = 0
        var early = 0
        repeat(400) { seed ->
            playPass(keys, emptySet(), seed).drop(1).take(10).forEach { id ->
                early++
                if (keys.getValue(id).artist == null) untagged++
            }
        }
        // Uniform would be 10 / 40 = 25%.
        val share = untagged.toDouble() / early
        println("ArtistSpread untagged share of the first 10 picks: $share")
        assertTrue("untagged share of early picks $share", share in 0.15..0.40)
    }

    @Test
    fun spreadKeyGroupsFeaturedAndTheFormsOfOneArtist() {
        assertEquals(spreadKey("X feat. Y", null, unknown).artist, spreadKey("X", null, unknown).artist)
        assertEquals(spreadKey("The Beatles", null, unknown).artist, spreadKey("Beatles", null, unknown).artist)
        assertEquals("beatles", spreadKey("The Beatles", null, unknown).artist)
    }

    @Test
    fun spreadKeyDropsPlaceholdersAndMissingAlbums() {
        assertNull(spreadKey("Unknown artist", "Abbey Road", unknown).artist)
        assertEquals("abbey road", spreadKey("Unknown artist", "Abbey Road", unknown).album)
        assertNull(spreadKey("Beatles", null, unknown).album)
        assertNull(spreadKey("Beatles", "Unknown album", unknown).album)
        assertEquals(SpreadKey(null, null), spreadKey(null, " ", unknown))
    }

    @Test
    fun spreadKeyTellsTheSameAlbumNameUnderTwoArtistsApart() {
        val first = spreadKey("Weezer", "Greatest Hits", unknown).album
        val second = spreadKey("Queen", "Greatest Hits", unknown).album
        assertNotEquals(first, second)
        assertEquals("weezer\u0000greatest hits", first)
    }

    /**
     * Back-to-back same-artist pairs, each checked to be forced: with no likes every song plays
     * once, so the songs still unplayed at a pick are the rest of the pass, and all must share
     * the artist that just played.
     */
    private fun forcedBackToBack(pass: List<Long>, keys: Map<Long, SpreadKey>): Int {
        var pairs = 0
        for (index in 0 until pass.lastIndex) {
            val artist = keys.getValue(pass[index]).artist ?: continue
            if (keys.getValue(pass[index + 1]).artist != artist) continue
            val rest = pass.subList(index + 1, pass.size)
            assertTrue("$artist twice in a row with others left in $pass", rest.all { keys.getValue(it).artist == artist })
            pairs++
        }
        return pairs
    }

    /** Same-artist neighbours (next song by that artist) at distance 1 or 2, and all of them. */
    private fun nearGaps(pass: List<Long>, keys: Map<Long, SpreadKey>): Pair<Int, Int> {
        val lastAt = HashMap<String, Int>()
        var near = 0
        var all = 0
        pass.forEachIndexed { index, id ->
            val artist = keys.getValue(id).artist ?: return@forEachIndexed
            lastAt[artist]?.let { previous ->
                all++
                if (index - previous <= 2) near++
            }
            lastAt[artist] = index
        }
        return near to all
    }

    private fun playPass(keys: Map<Long, SpreadKey>, liked: Set<Long>, seed: Int): List<Long> {
        val pool = keys.keys.toList()
        val random = Random(seed)
        val queue = LikeShuffleQueue(pool, pool[random.nextInt(pool.size)], liked, random, keys::get)
        var index = 0
        while (index < queue.window.lastIndex) {
            index++
            queue.onCurrentIndex(index)
            check(index < 10_000) { "pass never ended" }
        }
        return queue.window
    }
}
