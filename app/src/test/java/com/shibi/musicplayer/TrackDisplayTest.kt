package com.shibi.musicplayer

import com.shibi.musicplayer.domain.TrackDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
