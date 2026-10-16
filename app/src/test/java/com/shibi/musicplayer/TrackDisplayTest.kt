package com.shibi.musicplayer

import com.shibi.musicplayer.domain.TrackDisplay
import com.shibi.musicplayer.domain.containsTamil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackDisplayTest {
    @Test
    fun title_stripsTrailingExtensionCaseInsensitively() {
        assertEquals("Song", TrackDisplay.title("Song.MP3"))
        assertEquals("Song", TrackDisplay.title("Song.mp3"))
        assertEquals("Song", TrackDisplay.title("Song.FLAC"))
    }

    @Test
    fun title_stripsOnlyTheTrailingExtension() {
        assertEquals("a.b", TrackDisplay.title("a.b.flac"))
    }

    @Test
    fun title_leavesNonAudioExtensionsUntouched() {
        assertEquals("notes.txt", TrackDisplay.title("notes.txt"))
    }

    @Test
    fun title_keepsRawWhenStrippingWouldBeBlank() {
        assertEquals(".mp3", TrackDisplay.title(".mp3"))
    }

    @Test
    fun title_trimsSurroundingWhitespace() {
        assertEquals("Song", TrackDisplay.title("  Song.mp3  "))
        assertEquals("Song", TrackDisplay.title("  Song  "))
    }

    @Test
    fun artist_nullOrBlankBecomesNull() {
        assertNull(TrackDisplay.artist(null))
        assertNull(TrackDisplay.artist(""))
        assertNull(TrackDisplay.artist("   "))
    }

    @Test
    fun artist_unknownBecomesNull() {
        assertNull(TrackDisplay.artist("<unknown>"))
        assertNull(TrackDisplay.artist("unknown"))
        assertNull(TrackDisplay.artist("Unknown"))
        assertNull(TrackDisplay.artist("<Unknown>"))
    }

    @Test
    fun artist_returnsTrimmedValue() {
        assertEquals("Artist", TrackDisplay.artist("  Artist  "))
    }

    @Test
    fun album_nullsBlankUnknownAndThePlaceholder() {
        assertNull(TrackDisplay.album(null))
        assertNull(TrackDisplay.album("  "))
        assertNull(TrackDisplay.album("Unknown"))
        assertNull(TrackDisplay.album("Unknown album"))
        assertEquals("Night Highway", TrackDisplay.album(" Night Highway "))
    }

    @Test
    fun subtitle_prefersArtistThenAlbum() {
        assertEquals("Ella Voss", TrackDisplay.subtitle("Ella Voss", "Paper Planes"))
        assertEquals("Night Highway", TrackDisplay.subtitle("<unknown>", "Night Highway"))
        assertNull(TrackDisplay.subtitle("<unknown>", "Unknown album"))
    }

    @Test
    fun tamilEllipsisCut_stepsBackOneGrapheme() {
        val cluster = "\u0B95\u0BCA"
        assertEquals(0, TrackDisplay.tamilEllipsisCut(cluster, 1))
        assertEquals(0, TrackDisplay.tamilEllipsisCut(cluster, 2))
        val line = "AB$cluster"
        assertEquals(2, TrackDisplay.tamilEllipsisCut(line, line.length))
        assertEquals(0, TrackDisplay.tamilEllipsisCut(line, 0))
        val conjunct = "A\u0B95\u0BCD\u0B95"
        assertEquals(1, TrackDisplay.tamilEllipsisCut(conjunct, conjunct.length))
    }

    @Test
    fun containsTamil_trueForTamilTitle() {
        assertTrue(containsTamil("\u0B8E\u0BA9\u0BCD\u0BA9\u0BC8 \u0B95\u0BCA\u0B9E\u0BCD\u0B9A\u0BAE\u0BCD"))
    }

    @Test
    fun containsTamil_trueWhenTamilAnywhere() {
        assertTrue(containsTamil("Track \u0B95\u0BCA\u0B9E\u0BCD\u0B9A\u0BAE\u0BCD mix"))
    }

    @Test
    fun containsTamil_falseForEnglish() {
        assertFalse(containsTamil("Midnight Drive"))
        assertFalse(containsTamil(""))
    }

    @Test
    fun containsTamil_falseForTamilBlockEdges() {
        assertFalse(containsTamil("\u0B7F"))
        assertFalse(containsTamil("\u0C00"))
    }
}
