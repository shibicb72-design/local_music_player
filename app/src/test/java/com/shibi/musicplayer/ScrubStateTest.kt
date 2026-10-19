package com.shibi.musicplayer

import com.shibi.musicplayer.ui.ScrubState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubStateTest {

    private val calls = mutableListOf<String>()
    private val previews = mutableListOf<Long>()

    private fun drag(scrub: ScrubState, ms: Long, playing: Boolean) =
        scrub.onDrag(ms, playing, onPause = { calls += "pause" }, onRequestPreview = { previews += it })

    private fun release(scrub: ScrubState) =
        scrub.onDragEnd(onSeek = { calls += "seek $it" }, onPlay = { calls += "play" })

    @Test
    fun aDragWhilePlayingPausesOnceThenSeeksAndResumes() {
        val scrub = ScrubState(null)
        assertFalse(scrub.dragging)
        drag(scrub, 1_000L, playing = true)
        drag(scrub, 2_000L, playing = true)
        drag(scrub, 3_000L, playing = true)
        assertTrue(scrub.dragging)
        assertEquals(3_000L, scrub.positionMs)
        assertEquals(listOf("pause"), calls)

        release(scrub)
        assertEquals(listOf("pause", "seek 3000", "play"), calls)
        assertFalse(scrub.dragging)
    }

    @Test
    fun aDragWhilePausedNeverPausesOrPlays() {
        val scrub = ScrubState(null)
        drag(scrub, 5_000L, playing = false)
        drag(scrub, 6_000L, playing = false)
        release(scrub)
        assertEquals(listOf("seek 6000"), calls)
    }

    @Test
    fun everyDraggedPositionIsRequestedForPreview() {
        val scrub = ScrubState(null)
        drag(scrub, 1_000L, playing = true)
        drag(scrub, 1_400L, playing = true)
        drag(scrub, 9_000L, playing = true)
        assertEquals(listOf(1_000L, 1_400L, 9_000L), previews)
    }

    @Test
    fun theNextGestureRemembersItsOwnPlayingState() {
        val scrub = ScrubState(null)
        drag(scrub, 1_000L, playing = true)
        release(scrub)
        calls.clear()

        // Paused by the first release? No: it resumed, then the user paused and dragged again.
        drag(scrub, 2_000L, playing = false)
        release(scrub)
        assertEquals(listOf("seek 2000"), calls)
    }

    @Test
    fun aStartingPositionShowsTheBubbleWithoutAGesture() {
        val scrub = ScrubState(90_000L)
        assertTrue(scrub.dragging)
        assertEquals(90_000L, scrub.positionMs)
    }
}
