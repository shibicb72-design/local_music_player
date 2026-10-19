package com.shibi.musicplayer

import com.shibi.musicplayer.domain.DoubleTapSkipState
import com.shibi.musicplayer.domain.TapAction
import com.shibi.musicplayer.domain.TapSide
import com.shibi.musicplayer.domain.onTap
import com.shibi.musicplayer.domain.onTimeout
import com.shibi.musicplayer.domain.sideOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoubleTapSkipTest {

    @Test
    fun theThirdsPickTheSide() {
        assertEquals(TapSide.Back, sideOf(10f, 900f))
        assertEquals(TapSide.Center, sideOf(450f, 900f))
        assertEquals(TapSide.Forward, sideOf(890f, 900f))
    }

    @Test
    fun aCenterTapTogglesAtOnce() {
        val (state, action) = onTap(DoubleTapSkipState(), TapSide.Center, 0L)
        assertEquals(TapAction.ToggleControls, action)
        assertEquals(DoubleTapSkipState(), state)
    }

    @Test
    fun aSingleSideTapIsPendingThenTogglesAfterTheTimeout() {
        val (pending, action) = onTap(DoubleTapSkipState(), TapSide.Back, 1_000L)
        assertEquals(TapAction.Pending, action)
        val (early, none) = onTimeout(pending, 1_100L)
        assertNull(none)
        assertEquals(pending, early)
        val (cleared, toggle) = onTimeout(pending, 1_300L)
        assertEquals(TapAction.ToggleControls, toggle)
        assertEquals(DoubleTapSkipState(), cleared)
    }

    @Test
    fun aDoubleTapSkipsAndTheChainAccumulates() {
        var (state, action) = onTap(DoubleTapSkipState(), TapSide.Forward, 1_000L)
        assertEquals(TapAction.Pending, action)
        onTap(state, TapSide.Forward, 1_200L).let { state = it.first; action = it.second }
        assertEquals(TapAction.Skip(10_000L, 10_000L), action)
        onTap(state, TapSide.Forward, 1_500L).let { state = it.first; action = it.second }
        assertEquals(TapAction.Skip(10_000L, 20_000L), action)
        onTap(state, TapSide.Forward, 2_100L).let { state = it.first; action = it.second }
        assertEquals(TapAction.Skip(10_000L, 30_000L), action)
    }

    @Test
    fun theBackSideGivesNegativeSteps() {
        var (state, _) = onTap(DoubleTapSkipState(), TapSide.Back, 0L)
        val result = onTap(state, TapSide.Back, 100L)
        state = result.first
        assertEquals(TapAction.Skip(-10_000L, 10_000L), result.second)
        assertEquals(TapAction.Skip(-10_000L, 20_000L), onTap(state, TapSide.Back, 200L).second)
    }

    @Test
    fun aChainEndsAfterItsWindow() {
        val (first, _) = onTap(DoubleTapSkipState(), TapSide.Forward, 0L)
        val (chain, _) = onTap(first, TapSide.Forward, 100L)
        val (ended, none) = onTimeout(chain, 900L)
        assertNull(none)
        assertEquals(DoubleTapSkipState(), ended)
        // A late tap starts over as a pending single tap.
        assertEquals(TapAction.Pending, onTap(chain, TapSide.Forward, 900L).second)
    }

    @Test
    fun theOppositeSideEndsTheChainAndIsPending() {
        val (first, _) = onTap(DoubleTapSkipState(), TapSide.Forward, 0L)
        val (chain, _) = onTap(first, TapSide.Forward, 100L)
        val (state, action) = onTap(chain, TapSide.Back, 200L)
        assertEquals(TapAction.Pending, action)
        assertEquals(TapSide.Back, state.side)
        assertEquals(0L, state.totalMs)
        assertEquals(200L, state.pendingAtMs)
    }

    @Test
    fun aSlowSecondTapIsAnotherPendingTap() {
        val (first, _) = onTap(DoubleTapSkipState(), TapSide.Forward, 0L)
        assertEquals(TapAction.Pending, onTap(first, TapSide.Forward, 400L).second)
    }
}
