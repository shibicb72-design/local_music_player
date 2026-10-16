package com.shibi.musicplayer

import com.shibi.musicplayer.ui.playlistColumnCount
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistGridTest {
    @Test
    fun phoneDropsToOneColumnWhenTwoColumnsLeaveAHole() {
        assertEquals(
            1,
            playlistColumnCount(
                widthDp = 411f,
                heightDp = 640f,
                cardCount = 4,
                bottomPaddingDp = 16f,
            ),
        )
    }

    @Test
    fun shortPhoneKeepsTwoColumns() {
        assertEquals(
            2,
            playlistColumnCount(
                widthDp = 411f,
                heightDp = 280f,
                cardCount = 4,
                bottomPaddingDp = 16f,
            ),
        )
    }

    @Test
    fun wideWindowStartsAtThreeColumns() {
        assertEquals(
            3,
            playlistColumnCount(
                widthDp = 900f,
                heightDp = 360f,
                cardCount = 4,
                bottomPaddingDp = 16f,
            ),
        )
    }
}
