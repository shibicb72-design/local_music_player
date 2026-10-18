package com.shibi.musicplayer

import com.shibi.musicplayer.domain.Bounds
import com.shibi.musicplayer.domain.BoundsLerp
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundsLerpTest {
    private val from = Bounds(left = 10f, top = 20f, width = 100f, height = 50f)
    private val to = Bounds(left = 30f, top = 60f, width = 200f, height = 150f)

    @Test
    fun lerp_zeroGivesFrom() {
        assertEquals(from, BoundsLerp.lerp(from, to, 0f))
    }

    @Test
    fun lerp_oneGivesTo() {
        assertEquals(to, BoundsLerp.lerp(from, to, 1f))
    }

    @Test
    fun lerp_halfIsHalfway() {
        assertEquals(
            Bounds(left = 20f, top = 40f, width = 150f, height = 100f),
            BoundsLerp.lerp(from, to, 0.5f),
        )
    }

    @Test
    fun startFor_nullUsesTheCentredEightyPercentBox() {
        assertEquals(
            Bounds(left = 100f, top = 50f, width = 800f, height = 400f),
            BoundsLerp.startFor(null, windowWidth = 1_000f, windowHeight = 500f),
        )
    }

    @Test
    fun startFor_emptyBoxFallsBackToTheWindowBox() {
        val empty = Bounds(left = 5f, top = 5f, width = 0f, height = 40f)
        assertEquals(
            Bounds(left = 100f, top = 50f, width = 800f, height = 400f),
            BoundsLerp.startFor(empty, windowWidth = 1_000f, windowHeight = 500f),
        )
    }

    @Test
    fun startFor_realBoxIsReturnedAsIs() {
        assertEquals(from, BoundsLerp.startFor(from, windowWidth = 1_000f, windowHeight = 500f))
    }
}
