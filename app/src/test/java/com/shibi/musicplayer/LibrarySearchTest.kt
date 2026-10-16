package com.shibi.musicplayer

import com.shibi.musicplayer.domain.LibrarySearch
import com.shibi.musicplayer.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchTest {
    private val tamil = "\u0B8E\u0BA9\u0BCD\u0BA9\u0BC8 \u0B95\u0BCA\u0B9E\u0BCD\u0B9A\u0BAE\u0BCD.mp3"
    private val tracks = listOf(
        track(1, "Paper Planes", "Ella Voss"),
        track(2, tamil, "<unknown>"),
        track(3, "Slow Tide", "June & The Current"),
    )

    @Test
    fun blankQueryReturnsEverySong() {
        assertEquals(tracks, LibrarySearch.filter(tracks, "   "))
    }

    @Test
    fun filterMatchesTitleOrArtistAndSkipsUnknownArtist() {
        assertEquals(listOf(tracks[0]), LibrarySearch.filter(tracks, "voss"))
        assertEquals(listOf(tracks[2]), LibrarySearch.filter(tracks, "TIDE"))
        assertEquals(emptyList<Track>(), LibrarySearch.filter(tracks, "unknown"))
    }

    @Test
    fun filterMatchesATamilTitleWithoutTheFileExtension() {
        assertEquals(listOf(tracks[1]), LibrarySearch.filter(tracks, "\u0B95\u0BCA\u0B9E\u0BCD\u0B9A\u0BAE\u0BCD"))
    }

    private fun track(id: Long, title: String, artist: String) = Track(
        id = id,
        title = title,
        artist = artist,
        album = "",
        durationMs = 1L,
        contentUri = "content://tracks/$id",
        albumArtUri = null,
    )
}
