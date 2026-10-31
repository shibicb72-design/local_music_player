package com.shibi.musicplayer

import com.shibi.musicplayer.domain.DotTickState
import com.shibi.musicplayer.domain.MagneticSnap
import com.shibi.musicplayer.domain.ScrollSection
import com.shibi.musicplayer.domain.dragTargetIndex
import com.shibi.musicplayer.domain.magneticSnap
import com.shibi.musicplayer.domain.sectionIndexAt
import com.shibi.musicplayer.domain.stopFractions
import com.shibi.musicplayer.domain.thinStops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastScrollTest {
    // Binary fractions, so gaps like 0.625 - 0.5 are exact in Float.
    private val dots = listOf(0.25f, 0.5f)
    private val radius = 0.125f

    @Test
    fun withoutDotsTheThumbStaysUnderTheFinger() {
        assertEquals(MagneticSnap(0.3f), magneticSnap(0.3f, emptyList(), radius, 1f))
    }

    @Test
    fun insideTheRadiusTheThumbLocksAndIsPulledTowardTheDot() {
        // Half the radius from the dot: the pull closes strength * (1 - 0.5) of the gap.
        val above = magneticSnap(0.5625f, dots, radius, 1f)
        assertEquals(1, above.lockedIndex)
        assertEquals(0.53125f, above.fraction, 1e-6f)
        val below = magneticSnap(0.4375f, dots, radius, 1f)
        assertEquals(1, below.lockedIndex)
        assertEquals(0.46875f, below.fraction, 1e-6f)
    }

    @Test
    fun outsideTheRadiusTheThumbIsFree() {
        val snap = magneticSnap(0.6875f, dots, radius, 1f)
        assertNull(snap.lockedIndex)
        assertEquals(1, snap.nearestIndex)
        assertEquals(0.6875f, snap.fraction, 0f)
    }

    @Test
    fun theRadiusEdgeLocksWithoutAJump() {
        val edge = magneticSnap(0.625f, dots, radius, 1f)
        assertEquals(1, edge.lockedIndex)
        assertEquals(0.625f, edge.fraction, 0f)
        val inside = magneticSnap(0.6249f, dots, radius, 1f)
        val outside = magneticSnap(0.6251f, dots, radius, 1f)
        assertEquals(1, inside.lockedIndex)
        assertNull(outside.lockedIndex)
        assertEquals(inside.fraction, outside.fraction, 0.001f)
    }

    @Test
    fun onTheDotTheThumbSitsExactlyOnIt() {
        assertEquals(
            MagneticSnap(0.5f, lockedIndex = 1, nearestIndex = 1, proximity = 1f),
            magneticSnap(0.5f, dots, radius, 0.6f),
        )
    }

    @Test
    fun thePullKeepsTheThumbInOrderAndNeverPassesTheDot() {
        var previous = -1f
        for (step in 0..400) {
            val raw = 0.375f + step * (0.25f / 400)
            val snap = magneticSnap(raw, dots, radius, 1f)
            assertTrue("thumb went backwards at $raw", snap.fraction >= previous)
            if (raw < 0.5f) assertTrue(snap.fraction in raw..0.5f)
            if (raw > 0.5f) assertTrue(snap.fraction in 0.5f..raw)
            previous = snap.fraction
        }
    }

    @Test
    fun strengthScalesThePull() {
        val none = magneticSnap(0.5625f, dots, radius, 0f)
        assertEquals(1, none.lockedIndex)
        assertEquals(0.5625f, none.fraction, 0f)
        assertEquals(0.546875f, magneticSnap(0.5625f, dots, radius, 0.5f).fraction, 1e-6f)
        assertEquals(magneticSnap(0.5625f, dots, radius, 1f), magneticSnap(0.5625f, dots, radius, 3f))
    }

    @Test
    fun theNearestDotWinsAndATieGoesToTheFirst() {
        assertEquals(0, magneticSnap(0.3125f, dots, radius, 1f).lockedIndex)
        assertEquals(1, magneticSnap(0.4375f, dots, radius, 1f).lockedIndex)
        val tie = magneticSnap(0.375f, dots, radius, 1f)
        assertEquals(0, tie.lockedIndex)
        assertEquals(0.375f, tie.fraction, 0f)
    }

    @Test
    fun proximityIsOneOnTheDotHalfAtTheRadiusAndZeroAtTwiceIt() {
        assertEquals(1f, magneticSnap(0.5f, dots, radius, 1f).proximity, 0f)
        assertEquals(0.75f, magneticSnap(0.5625f, dots, radius, 1f).proximity, 1e-6f)
        assertEquals(0.5f, magneticSnap(0.625f, dots, radius, 1f).proximity, 1e-6f)
        assertEquals(0.25f, magneticSnap(0.6875f, dots, radius, 1f).proximity, 1e-6f)
        assertEquals(0f, magneticSnap(0.75f, dots, radius, 1f).proximity, 0f)
        assertEquals(0f, magneticSnap(1f, dots, radius, 1f).proximity, 0f)
    }

    @Test
    fun theRawFractionIsClampedAndNaNIsTheTop() {
        assertEquals(1f, magneticSnap(3f, dots, radius, 1f).fraction, 0f)
        assertEquals(0f, magneticSnap(-1f, dots, radius, 1f).fraction, 0f)
        assertEquals(MagneticSnap(0f), magneticSnap(Float.NaN, emptyList(), radius, 1f))
    }

    @Test
    fun aZeroRadiusNeverLocks() {
        assertEquals(MagneticSnap(0.5f, nearestIndex = 1), magneticSnap(0.5f, dots, 0f, 1f))
    }

    @Test
    fun aDotTicksOnceWhenEnteredAndAgainAfterLeaving() {
        var state = DotTickState()
        fun lock(index: Int?): Boolean {
            state = state.next(index)
            return state.tick
        }
        assertFalse(lock(null))
        assertTrue(lock(2))
        assertFalse(lock(2))
        assertFalse(lock(2))
        assertFalse(lock(null))
        assertFalse(lock(null))
        assertTrue(lock(2))
        assertTrue(lock(3))
        assertFalse(lock(3))
    }

    @Test
    fun stopsSitAtTheirShareOfTheListAndADragLandsOnTheLockedOne() {
        val stops = listOf(ScrollSection(0, 10), ScrollSection(50, 5), ScrollSection(100, 1))
        assertEquals(listOf(0f, 0.5f, 1f), stopFractions(stops, 101))
        assertEquals(50, dragTargetIndex(MagneticSnap(0.48f, lockedIndex = 1), stops, 101))
        // Between stops the entry under the thumb wins.
        assertEquals(30, dragTargetIndex(MagneticSnap(0.3f), stops, 101))
    }

    @Test
    fun theSectionHoldingAnEntryIsTheLastOneStartingAtOrBeforeIt() {
        val sections = listOf(ScrollSection(0, 3), ScrollSection(4, 2), ScrollSection(7, 9))
        assertEquals(0, sectionIndexAt(sections, 0))
        assertEquals(0, sectionIndexAt(sections, 3))
        assertEquals(1, sectionIndexAt(sections, 4))
        assertEquals(2, sectionIndexAt(sections, 20))
        assertNull(sectionIndexAt(emptyList(), 2))
    }

    @Test
    fun crowdedStopsAreThinnedSoTheBiggestKeepTheirDots() {
        // 1000 entries on a 1000 px track: one entry per pixel. Letters X, Y, Z are 3 px apart.
        val a = ScrollSection(0, 400)
        val x = ScrollSection(990, 2)
        val y = ScrollSection(993, 1)
        val z = ScrollSection(996, 4)
        val kept = thinStops(listOf(a, x, y, z), entryCount = 1001, trackPx = 1000f, minGapPx = 16f)
        // Z is the biggest of the crowded three; A is far away and stays.
        assertEquals(listOf(a, z), kept)
        // Far apart, nothing is dropped, and the order is the list's.
        val spread = listOf(ScrollSection(0, 1), ScrollSection(500, 1), ScrollSection(1000, 1))
        assertEquals(spread, thinStops(spread, 1001, 1000f, 16f))
    }

    @Test
    fun thinnedStopsAreAlwaysAtLeastTheSnapDistanceApart() {
        val stops = (0 until 60).map { ScrollSection(it * 7, it % 5 + 1) }
        val kept = thinStops(stops, entryCount = 420, trackPx = 600f, minGapPx = 16f)
        val positions = stopFractions(kept, 420).map { it * 600f }
        positions.zipWithNext().forEach { (first, second) -> assertTrue(second - first >= 16f) }
        assertEquals(kept.sortedBy { it.startIndex }, kept)
        assertTrue(kept.size in 2 until stops.size)
    }
}
