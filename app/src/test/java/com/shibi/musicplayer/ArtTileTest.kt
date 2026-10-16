package com.shibi.musicplayer

import com.shibi.musicplayer.domain.ArtTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtTileTest {
    private val tamilTitle = "\u0B8E\u0BA9\u0BCD\u0BA9\u0BC8 \u0B95\u0BCA\u0B9E\u0BCD\u0B9A\u0BAE\u0BCD.mp3"

    @Test
    fun specFor_isDeterministicForTheSameTitle() {
        assertEquals(ArtTile.specFor("Midnight Drive"), ArtTile.specFor("Midnight Drive"))
    }

    @Test
    fun specFor_ignoresTrailingAudioExtension() {
        assertEquals(ArtTile.specFor("Song"), ArtTile.specFor("Song.mp3"))
    }

    @Test
    fun specFor_uppercasesTheGlyph() {
        assertEquals("M", ArtTile.specFor("midnight").glyph)
    }

    @Test
    fun specFor_keepsTheFirstCodePointOfATamilTitle() {
        assertEquals("\u0B8E", ArtTile.specFor(tamilTitle).glyph)
    }

    @Test
    fun specFor_blankOrPunctuationFallsBackToQuestionMark() {
        assertEquals("?", ArtTile.specFor("").glyph)
        assertEquals("?", ArtTile.specFor("   ").glyph)
        assertEquals("?", ArtTile.specFor("!!!").glyph)
        assertEquals("?", ArtTile.specFor("-Song").glyph)
    }

    @Test
    fun specFor_indexStaysWithinGradients() {
        val spec = ArtTile.specFor("Midnight Drive")
        val index = ArtTile.GRADIENTS.indexOfFirst {
            it.first == spec.startArgb && it.second == spec.endArgb
        }
        assertTrue(index in ArtTile.GRADIENTS.indices)
    }
}
