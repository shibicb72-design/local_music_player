package com.shibi.musicplayer.domain

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Tuning for the like-weighted shuffle. All counts are in songs started, not minutes, so a
 * pool of short tracks and a pool of long ones space liked songs the same way.
 */
object LikeShuffleTuning {
    /** Draw weight of an unliked song that has not played yet in this pass. */
    const val UNLIKED_WEIGHT = 1.0

    /**
     * Draw weight of a liked song, used both for its first play in the pass and for every
     * return after its cooldown. Three means a liked song is three times as likely as an
     * unliked one to be the next pick, in the 2-3x range that rating-biased shuffles (Amarok's
     * weighted shuffle, Symfonium's rating-band quotas) give a top-rated track.
     */
    const val LIKED_WEIGHT = 3.0

    /**
     * Ceiling on the chance that one draw is a liked return rather than a song still waiting
     * for its first play. Without it the end of a pass, where few unplayed songs are left,
     * would turn into a loop of liked songs.
     */
    const val MAX_RETURN_SHARE = 1.0 / 3.0

    /** Minimum gap as a share of the pool: a quarter of the songs must play in between. */
    const val GAP_POOL_FRACTION = 0.25

    /** Smallest minimum gap for a normal pool, so a liked song never returns within three songs. */
    const val MIN_GAP_FLOOR = 3

    /** Largest minimum gap, about one and a half to two hours of music, so big libraries still bring likes back. */
    const val MIN_GAP_CAP = 30

    /**
     * Random extra cooldown as a multiple of the minimum gap. Each play draws its own extra
     * uniformly from `0..gap * RETURN_JITTER` songs, so liked songs that played close together
     * do not all become eligible on the same step (the random offset of Spotify's dithering).
     * After that point the return is still a weighted draw, never a fixed slot.
     */
    const val RETURN_JITTER = 1.0

    /**
     * Weight multiplier for a song whose artist played in the last [ARTIST_WINDOW] songs, so
     * one artist's songs spread across the pass (the idea of Spotify's 2014 shuffle and Martin
     * Fiedler's balanced shuffle). Applied once, however many of those songs were by the artist.
     * Untagged songs count as recent only when at least two of those songs were untagged.
     */
    const val ARTIST_RECENT_FACTOR = 0.15

    /**
     * How many of the latest songs count as recent for [ARTIST_RECENT_FACTOR]. Shrunk to one
     * less than the number of artists in the pool, so at least one artist is never recent.
     */
    const val ARTIST_WINDOW = 4

    /** Weight multiplier for a song from the same album as the song that just played. */
    const val SAME_ALBUM_FACTOR = 0.3

    /**
     * Floor of the balance factor `left / meanLeft`: an artist with few songs left (against the
     * mean of the tagged artists that still have some) is drawn at no less than half weight.
     */
    const val BALANCE_MIN = 0.5

    /**
     * Ceiling of the balance factor, so one artist owning a big share of a library is drawn
     * sooner but does not crowd the start of the pass.
     */
    const val BALANCE_MAX = 2.0

    /**
     * Songs that must play between two plays of the same song:
     * `clamp(round(poolSize * GAP_POOL_FRACTION), MIN_GAP_FLOOR, MIN_GAP_CAP)`, then shrunk to at
     * most half the pool (and at least one) so small playlists keep variety and never repeat a
     * song back to back. A single-song pool has no gap.
     */
    fun minimumGap(poolSize: Int): Int {
        if (poolSize <= 1) return 0
        val scaled = (poolSize * GAP_POOL_FRACTION).roundToInt()
            .coerceIn(MIN_GAP_FLOOR, MIN_GAP_CAP)
        return scaled.coerceAtMost(maxOf(1, poolSize / 2))
    }
}

/**
 * What the shuffle spreads apart: [artist] is the primary artist's sort key, [album] the album's
 * sort key under that artist. Null means unknown and never matches another song, except that
 * untagged songs count as recent once at least two of the latest plays were untagged. Untagged
 * songs get no balance factor.
 */
data class SpreadKey(val artist: String?, val album: String?)

private val NO_SPREAD = SpreadKey(null, null)

/** The [SpreadKey] for a song's raw artist and album tags; blanks and placeholders become null. */
fun spreadKey(artist: String?, album: String?, unknown: UnknownNames): SpreadKey {
    val artistKey = ArtistNames.artist(artist, unknown)?.let(ArtistNames::sortKey)
    val albumKey = ArtistNames.album(album, unknown)?.let { name ->
        val albumName = ArtistNames.sortKey(name)
        if (artistKey == null) albumName else artistKey + "\u0000" + albumName
    }
    return SpreadKey(artistKey, albumKey)
}

/**
 * The like-weighted shuffle picker. A pass plays every song in the pool at least once; unliked
 * songs play exactly once, liked songs may come back. Each draw is weighted ([LikeShuffleTuning]):
 * unplayed songs by liked or not, plus liked songs whose cooldown has run out. The last song
 * played is never drawn, so nothing repeats back to back.
 *
 * Artists are spread by [spreadOf]: the last song's artist is skipped while another artist's
 * song is unplayed, recent artists and the last album weigh less, and artists with more songs
 * left weigh more, so a pass does not end on one artist. With no keys the draws are unchanged.
 *
 * [draw] only proposes; [onPlayed] commits a song that actually started, so a queued proposal
 * can be redrawn when likes change. Pure Kotlin with an injected [random] for tests.
 */
class LikeShufflePicker(
    pool: List<Long>,
    liked: Set<Long>,
    private val random: Random = Random.Default,
    spreadOf: (Long) -> SpreadKey? = { null },
) {
    private val pool: List<Long> = pool.distinct()
    private val members: Set<Long> = this.pool.toSet()
    private var liked: Set<Long> = liked
    private val unplayed = LinkedHashSet(this.pool)
    private val eligibleAt = HashMap<Long, Int>()
    private var last: Long? = null
    private val spread: Map<Long, SpreadKey> = this.pool.associateWith { spreadOf(it) ?: NO_SPREAD }
    private val distinctArtists: Int = spread.values.mapNotNullTo(HashSet()) { it.artist }.size
    private val artistWindow: Int =
        minOf(LikeShuffleTuning.ARTIST_WINDOW, distinctArtists - 1).coerceAtLeast(0)

    /** Keys of the last [artistWindow] songs played, oldest first. */
    private val recent = ArrayDeque<SpreadKey>()

    /** Songs started so far in this pass. */
    var plays: Int = 0
        private set

    /** Songs that must play between two plays of the same song for this pool. */
    val minimumGap: Int = LikeShuffleTuning.minimumGap(this.pool.size)

    /** True once every song has played; [draw] then returns null and playback can end. */
    val isPassComplete: Boolean
        get() = unplayed.isEmpty()

    fun setLiked(ids: Set<Long>) {
        liked = ids
    }

    /** Records that [id] started playing. Its cooldown starts now, liked or not, so a later like counts it. */
    fun onPlayed(id: Long) {
        require(id in members) { "Track $id is not in the shuffle pool" }
        unplayed.remove(id)
        val maxJitter = (minimumGap * LikeShuffleTuning.RETURN_JITTER).roundToInt()
        eligibleAt[id] = plays + 1 + minimumGap + random.nextInt(maxJitter + 1)
        plays++
        last = id
        if (artistWindow > 0) {
            recent.addLast(spread.getValue(id))
            if (recent.size > artistWindow) recent.removeFirst()
        }
    }

    /** The next song, or null when the pass is complete. Does not change the pass. */
    fun draw(): Long? {
        if (unplayed.isEmpty()) return null
        val lastKey = last?.let { spread.getValue(it) } ?: NO_SPREAD
        val otherArtist = { id: Long -> lastKey.artist == null || spread.getValue(id).artist != lastKey.artist }
        val fresh = unplayed.filter(otherArtist).ifEmpty { unplayed.toList() }
        val returning = returningSongs().filter(otherArtist)
        val recentArtists = recent.mapNotNullTo(HashSet()) { it.artist }
        // Untagged songs are many artists in one group: one untagged play must not hold them all back.
        val untaggedRecent = recent.count { it.artist == null } >= 2
        val left: Map<String?, Int> =
            if (distinctArtists > 0) unplayed.groupingBy { spread.getValue(it).artist }.eachCount() else emptyMap()
        val spreadWeight = { id: Long ->
            val key = spread.getValue(id)
            val isRecent = if (key.artist == null) untaggedRecent else key.artist in recentArtists
            val recency = if (isRecent) LikeShuffleTuning.ARTIST_RECENT_FACTOR else 1.0
            val album = if (key.album != null && key.album == lastKey.album) LikeShuffleTuning.SAME_ALBUM_FACTOR else 1.0
            weight(id) * recency * album
        }
        val taggedLeft = left.entries.filter { it.key != null }
        val meanLeft = if (taggedLeft.isEmpty()) 0.0 else taggedLeft.sumOf { it.value }.toDouble() / taggedLeft.size
        // Balance: an artist with more songs left than the mean is drawn sooner, clamped so a big
        // artist never crowds the pass. Untagged songs and returns skip it, and it only shapes
        // the pick, so the share of liked returns stays as it was.
        val pickWeight = { id: Long ->
            val artist = spread.getValue(id).artist
            val balance = if (artist == null || meanLeft == 0.0) {
                1.0
            } else {
                (left.getValue(artist) / meanLeft).coerceIn(LikeShuffleTuning.BALANCE_MIN, LikeShuffleTuning.BALANCE_MAX)
            }
            spreadWeight(id) * balance
        }
        if (returning.isNotEmpty()) {
            val freshTotal = fresh.sumOf(spreadWeight)
            val returnWeight = returning.sumOf(spreadWeight)
            val share = minOf(
                LikeShuffleTuning.MAX_RETURN_SHARE,
                returnWeight / (freshTotal + returnWeight),
            )
            if (random.nextDouble() < share) return returning[random.nextInt(returning.size)]
        }
        return weightedPick(fresh, pickWeight)
    }

    /** Liked, already played, past the cooldown, and not the song that just played. */
    fun returningSongs(): List<Long> = pool.filter { id ->
        id in liked && id !in unplayed && id != last && plays >= (eligibleAt[id] ?: Int.MAX_VALUE)
    }

    private fun weight(id: Long): Double =
        if (id in liked) LikeShuffleTuning.LIKED_WEIGHT else LikeShuffleTuning.UNLIKED_WEIGHT

    /** [ids] is never empty: [draw] falls back to every unplayed song. */
    private fun weightedPick(ids: List<Long>, weightOf: (Long) -> Double): Long {
        var roll = random.nextDouble() * ids.sumOf(weightOf)
        for (id in ids) {
            roll -= weightOf(id)
            if (roll < 0.0) return id
        }
        return ids.last()
    }
}

/** A change the player must make so it keeps holding [LikeShuffleQueue.window]. */
sealed interface QueueEdit {
    data class Append(val id: Long) : QueueEdit

    data class Replace(val index: Int, val id: Long) : QueueEdit

    data class Remove(val index: Int) : QueueEdit
}

/**
 * What the player holds while the like-weighted shuffle runs: the songs played so far, in play
 * order, plus one queued draw. Previous walks back through what really played; next plays the
 * queued draw. Repeats and steps back into the history do not count as new plays.
 */
class LikeShuffleQueue(
    pool: List<Long>,
    startId: Long,
    liked: Set<Long>,
    random: Random = Random.Default,
    spreadOf: (Long) -> SpreadKey? = { null },
) {
    private val picker = LikeShufflePicker(pool, liked, random, spreadOf)
    private val ids = mutableListOf(startId)
    private var furthest = 0

    init {
        picker.onPlayed(startId)
        picker.draw()?.let(ids::add)
    }

    val window: List<Long>
        get() = ids.toList()

    /** Songs started so far, including the first one. */
    val plays: Int
        get() = picker.plays

    /** The player is now on [index] of [window]. Returns the song to queue after it, if any. */
    fun onCurrentIndex(index: Int): QueueEdit? {
        if (index !in ids.indices || index <= furthest) return null
        furthest = index
        picker.onPlayed(ids[index])
        if (index < ids.lastIndex) return null
        val next = picker.draw() ?: return null
        ids += next
        return QueueEdit.Append(next)
    }

    /** Likes changed: redraw the queued song so the change applies to the very next pick. */
    fun onLikedChanged(liked: Set<Long>): QueueEdit? {
        picker.setLiked(liked)
        val queued = ids.lastIndex
        if (queued <= furthest) return null
        val redraw = picker.draw()
        return when (redraw) {
            null -> {
                ids.removeAt(queued)
                QueueEdit.Remove(queued)
            }
            ids[queued] -> null
            else -> {
                ids[queued] = redraw
                QueueEdit.Replace(queued, redraw)
            }
        }
    }
}
