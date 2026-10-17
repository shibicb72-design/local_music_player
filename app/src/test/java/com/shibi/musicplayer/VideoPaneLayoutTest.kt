package com.shibi.musicplayer

import com.shibi.musicplayer.domain.VideoPaneLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPaneLayoutTest {

    @Test
    fun wideVideoFitsWidthAndKeepsDetails() {
        val layout = VideoPaneLayout.of(width = 480f, height = 520f, aspect = 16f / 9f)
        assertEquals(270f, layout.videoHeight, 0.01f)
        assertTrue(layout.showDetails)
    }

    @Test
    fun unknownAspectFallsBackToSixteenByNine() {
        assertEquals(270f, VideoPaneLayout.of(480f, 520f, null).videoHeight, 0.01f)
        assertEquals(270f, VideoPaneLayout.of(480f, 520f, Float.NaN).videoHeight, 0.01f)
        assertEquals(270f, VideoPaneLayout.of(480f, 520f, 0f).videoHeight, 0.01f)
    }

    @Test
    fun portraitVideoLeavesRoomForDetails() {
        val layout = VideoPaneLayout.of(width = 480f, height = 520f, aspect = 1080f / 2340f)
        assertEquals(520f - VideoPaneLayout.DetailsHeight, layout.videoHeight, 0.01f)
        assertTrue(layout.showDetails)
    }

    @Test
    fun shortPaneDropsDetailsAndFitsHeight() {
        // 140dp of pane: details would leave the picture under 60% of it.
        val layout = VideoPaneLayout.of(width = 480f, height = 140f, aspect = 16f / 9f)
        assertFalse(layout.showDetails)
        assertEquals(140f, layout.videoHeight, 0.01f)
    }

    @Test
    fun exactFitKeepsDetails() {
        val height = 270f + VideoPaneLayout.DetailsHeight
        val layout = VideoPaneLayout.of(width = 480f, height = height, aspect = 16f / 9f)
        assertEquals(270f, layout.videoHeight, 0.01f)
        assertTrue(layout.showDetails)
    }

    @Test
    fun emptyPaneIsZeroNotNegative() {
        val layout = VideoPaneLayout.of(width = 0f, height = 0f, aspect = 16f / 9f)
        assertEquals(0f, layout.videoHeight, 0f)
        assertFalse(layout.showDetails)
    }
}
