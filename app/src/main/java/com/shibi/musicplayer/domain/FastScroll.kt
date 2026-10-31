package com.shibi.musicplayer.domain

import kotlin.math.abs

/** How long the fast-scroll thumb stays visible after the last scroll or drag activity. */
const val FAST_SCROLL_HIDE_DELAY_MS = 2_000L

/** True while the thumb is being used, or for [FAST_SCROLL_HIDE_DELAY_MS] after the last activity. */
fun fastScrollVisible(active: Boolean, lastActiveAtMs: Long?, nowMs: Long): Boolean =
    active || (lastActiveAtMs != null && nowMs - lastActiveAtMs < FAST_SCROLL_HIDE_DELAY_MS)

/**
 * The fast-scroll thumb after the dots' pull. [fraction] is where the thumb is drawn and
 * [lockedIndex] the dot holding it (null between dots). [nearestIndex] and [proximity] say how close
 * the finger is to the nearest dot: 1 on it, 0.5 at the lock radius, 0 at twice the radius or farther.
 */
data class MagneticSnap(
    val fraction: Float,
    val lockedIndex: Int? = null,
    val nearestIndex: Int? = null,
    val proximity: Float = 0f,
)

/**
 * Pulls the raw thumb [fraction] toward the nearest of [dots] and locks onto that dot while the
 * finger is within [radius] of it (all in track fractions, 0..1). The pull closes [strength] (0..1)
 * of the gap right at the dot and fades to nothing at the radius, so the thumb never jumps as it
 * enters or leaves, and it never passes the dot.
 */
fun magneticSnap(fraction: Float, dots: List<Float>, radius: Float, strength: Float): MagneticSnap {
    val raw = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    var nearest = -1
    var distance = Float.POSITIVE_INFINITY
    dots.forEachIndexed { index, dot ->
        val d = abs(raw - dot)
        if (d < distance) {
            nearest = index
            distance = d
        }
    }
    if (nearest < 0) return MagneticSnap(raw)
    if (radius <= 0f) return MagneticSnap(raw, nearestIndex = nearest)
    val proximity = (1f - distance / (2f * radius)).coerceIn(0f, 1f)
    if (distance > radius) return MagneticSnap(raw, nearestIndex = nearest, proximity = proximity)
    val pull = strength.coerceIn(0f, 1f) * (1f - distance / radius)
    val pulled = (raw + (dots[nearest] - raw) * pull).coerceIn(0f, 1f)
    return MagneticSnap(pulled, lockedIndex = nearest, nearestIndex = nearest, proximity = proximity)
}

/**
 * A run of list entries the fast scroller can name and stop at: a week, a letter, a size band.
 * [startIndex] is its first entry (a header, when the list has them); [size] counts its items.
 */
data class ScrollSection(val startIndex: Int, val size: Int)

/** The entry under the thumb at [fraction] (0..1) of the track; NaN is the top. */
fun entryIndexForFraction(fraction: Float, entryCount: Int): Int {
    if (entryCount <= 0) return 0
    val f = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    return (f * (entryCount - 1)).toInt().coerceIn(0, entryCount - 1)
}

/** Where the thumb rests for a list scrolled to [firstVisibleIndex]: 0 at the top, 1 at the end. */
fun thumbFraction(firstVisibleIndex: Int, visibleCount: Int, entryCount: Int): Float {
    if (entryCount <= 0 || visibleCount >= entryCount) return 0f
    if (firstVisibleIndex + visibleCount >= entryCount) return 1f
    val maxFirst = entryCount - visibleCount
    return (firstVisibleIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
}

/** Each stop's place on the track, on the same scale as [entryIndexForFraction]. */
fun stopFractions(stops: List<ScrollSection>, entryCount: Int): List<Float> {
    val denominator = maxOf(1, entryCount - 1)
    return stops.map { it.startIndex.toFloat() / denominator }
}

/** The index in [sections] (in list order) of the one holding [entryIndex]; null without sections. */
fun sectionIndexAt(sections: List<ScrollSection>, entryIndex: Int): Int? {
    if (sections.isEmpty()) return null
    val found = sections.binarySearchBy(entryIndex) { it.startIndex }
    return if (found >= 0) found else (-found - 2).coerceAtLeast(0)
}

/** Where a drag puts the list: the start of the locked stop, else the entry under the thumb. */
fun dragTargetIndex(snap: MagneticSnap, stops: List<ScrollSection>, entryCount: Int): Int =
    snap.lockedIndex?.let { stops.getOrNull(it)?.startIndex }
        ?: entryIndexForFraction(snap.fraction, entryCount)

/**
 * The stops that keep a dot on a track [trackPx] long, so no two dots are closer than
 * [minGapPx] (the snap radius): in a crowded stretch the biggest sections win, a tie goes to
 * the earlier one. Returned in list order.
 */
fun thinStops(stops: List<ScrollSection>, entryCount: Int, trackPx: Float, minGapPx: Float): List<ScrollSection> {
    if (stops.size < 2 || minGapPx <= 0f) return stops
    val positions = stopFractions(stops, entryCount).map { it * trackPx }
    val kept = java.util.TreeMap<Float, Int>()
    val byPriority = stops.indices.sortedWith(compareByDescending<Int> { stops[it].size }.thenBy { it })
    for (index in byPriority) {
        val position = positions[index]
        val below = kept.floorKey(position)
        val above = kept.ceilingKey(position)
        if (below != null && position - below < minGapPx) continue
        if (above != null && above - position < minGapPx) continue
        kept[position] = index
    }
    return kept.values.sorted().map { stops[it] }
}

/**
 * Tick-once state for the fast-scroll dots. [tick] is true only on the update where the thumb locks
 * onto a dot it was not already on: staying on a dot is silent, and leaving it (or sliding straight
 * onto another dot) arms the next tick.
 */
data class DotTickState(val lockedIndex: Int? = null, val tick: Boolean = false) {
    fun next(lockedIndex: Int?): DotTickState =
        DotTickState(lockedIndex, tick = lockedIndex != null && lockedIndex != this.lockedIndex)
}
