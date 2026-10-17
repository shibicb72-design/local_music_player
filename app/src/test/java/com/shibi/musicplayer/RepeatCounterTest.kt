package com.shibi.musicplayer

import com.shibi.musicplayer.domain.RepeatCounter
import com.shibi.musicplayer.domain.RepeatMode
import com.shibi.musicplayer.domain.RepeatState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatCounterTest {
    @Test
    fun cycleWalksOffOnceThriceInfiniteThenOff() {
        var state = RepeatState()
        state = RepeatCounter.cycle(state)
        assertEquals(RepeatMode.ONCE, state.mode)
        assertEquals(1, state.remainingExtras)
        assertTrue(state.repeatsCurrentItem)

        state = RepeatCounter.cycle(state)
        assertEquals(RepeatMode.THRICE, state.mode)
        assertEquals(3, state.remainingExtras)

        state = RepeatCounter.cycle(state)
        assertEquals(RepeatMode.INFINITE, state.mode)
        assertTrue(state.repeatsCurrentItem)

        state = RepeatCounter.cycle(state)
        assertEquals(RepeatMode.OFF, state.mode)
        assertFalse(state.repeatsCurrentItem)
    }

    @Test
    fun onceConsumesASingleCompletionThenTurnsOff() {
        var state = RepeatCounter.start(RepeatMode.ONCE)
        state = RepeatCounter.onSameItemCompleted(state)
        assertEquals(RepeatMode.OFF, state.mode)
        assertEquals(0, state.remainingExtras)
        assertFalse(state.repeatsCurrentItem)
    }

    @Test
    fun thriceCountsThreeCompletionsOfTheSameItem() {
        var state = RepeatCounter.start(RepeatMode.THRICE)
        state = RepeatCounter.onSameItemCompleted(state)
        assertEquals(RepeatMode.THRICE, state.mode)
        assertEquals(2, state.remainingExtras)
        state = RepeatCounter.onSameItemCompleted(state)
        assertEquals(1, state.remainingExtras)
        state = RepeatCounter.onSameItemCompleted(state)
        assertEquals(RepeatMode.OFF, state.mode)
        assertFalse(state.repeatsCurrentItem)
    }

    @Test
    fun infiniteIgnoresCompletions() {
        val start = RepeatCounter.start(RepeatMode.INFINITE)
        val after = RepeatCounter.onSameItemCompleted(start)
        assertEquals(start, after)
        assertTrue(after.repeatsCurrentItem)
    }

    @Test
    fun skippingToANewItemResetsCountedRepeats() {
        val once = RepeatCounter.onCurrentItemChanged(
            RepeatState(RepeatMode.ONCE, remainingExtras = 0),
        )
        assertEquals(1, once.remainingExtras)
        assertEquals(RepeatMode.ONCE, once.mode)

        val thrice = RepeatCounter.onSameItemCompleted(RepeatCounter.start(RepeatMode.THRICE))
        val reset = RepeatCounter.onCurrentItemChanged(thrice)
        assertEquals(3, reset.remainingExtras)
    }
}
