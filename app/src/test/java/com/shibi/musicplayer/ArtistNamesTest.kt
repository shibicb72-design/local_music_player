package com.shibi.musicplayer

import com.shibi.musicplayer.domain.ArtistNames
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.UnknownNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistNamesTest {
    private val unknown = UnknownNames(title = "Unknown title", artist = "Unknown artist", album = "Unknown album")

    private fun track(
        artist: String = "Artist",
        albumArtist: String? = null,
        fileName: String? = null,
    ) = Track(
        id = 1L,
        title = "T",
        artist = artist,
        album = "Album",
        durationMs = 1L,
        contentUri = "content://t/1",
        albumArtUri = null,
        albumArtist = albumArtist,
        fileName = fileName,
    )

    private fun split(raw: String) = ArtistNames.split(raw).let { it.primary to it.featured }

    @Test
    fun featuredArtistsAreSplitOffThePrimary() {
        assertEquals("X" to listOf("Y"), split("X feat. Y"))
        assertEquals("X" to listOf("Y"), split("X FEAT Y"))
        assertEquals("X" to listOf("Y"), split("X ft. Y"))
        assertEquals("X" to listOf("Y"), split("X ft Y"))
        assertEquals("X" to listOf("Y"), split("X Featuring Y"))
        assertEquals("X" to listOf("Y"), split("X (feat. Y)"))
        assertEquals("X" to listOf("Y"), split("X (ft. Y)"))
        assertEquals("X" to listOf("Y"), split("X [feat. Y]"))
    }

    @Test
    fun semicolonsSplitTheListAndTheFirstIsPrimary() {
        assertEquals("A" to listOf("B", "C"), split("A; B ;C"))
        assertEquals("A" to listOf("B", "C"), split("A feat. B; C"))
    }

    @Test
    fun ampersandCommaSlashAndWordsNeverSplit() {
        for (name in listOf("Simon & Garfunkel", "Earth, Wind & Fire", "AC/DC", "Tom and Jerry", "Mr x Mrs", "Defeat Ft")) {
            assertEquals(name to emptyList<String>(), split(name))
        }
    }

    @Test
    fun aNameThatIsOnlyAFeatureStaysWhole() {
        assertEquals("(feat. Y)" to emptyList<String>(), split("(feat. Y)"))
    }

    @Test
    fun missingValuesIncludeTheLocalizedPlaceholders() {
        assertNull(ArtistNames.libraryArtist(track(artist = "Unknown artist"), unknown))
        assertNull(ArtistNames.libraryArtist(track(artist = "UNKNOWN ARTIST", albumArtist = "Unknown Album"), unknown))
        assertNull(ArtistNames.libraryArtist(track(artist = "<unknown>", albumArtist = " "), unknown))
        assertEquals("Real", ArtistNames.libraryArtist(track(artist = "Unknown artist", albumArtist = "Real"), unknown))
    }

    @Test
    fun libraryArtistPrefersAlbumArtistThenPrimaryTrackArtist() {
        assertEquals("Various Artists", ArtistNames.libraryArtist(track(artist = "X feat. Y", albumArtist = "Various Artists"), unknown))
        assertEquals("X", ArtistNames.libraryArtist(track(artist = "X feat. Y"), unknown))
    }

    @Test
    fun fileNameFallbackNeedsExactlyOneSeparator() {
        fun fallback(name: String?) = ArtistNames.libraryArtist(track(artist = "Unknown artist", fileName = name), unknown)
        assertEquals("Abba", fallback("Abba - Waterloo.mp3"))
        assertEquals("Abba", fallback("01 Abba - Waterloo.flac"))
        assertEquals("Abba", fallback("01. Abba - Waterloo.m4a"))
        assertEquals("Abba", fallback("01 - Abba - Waterloo.mp3"))
        assertEquals("Abba", fallback("1-01 Abba - Waterloo.mp3"))
        assertNull(fallback("Waterloo.mp3"))
        assertNull(fallback("A - B - C.mp3"))
        assertNull(fallback(" - Waterloo.mp3"))
        assertNull(fallback(null))
    }

    @Test
    fun performerIsTheTrackArtistWhereLibraryArtistIsTheAlbumArtist() {
        val compilation = track(artist = "X feat. Y", albumArtist = "Various Artists")
        assertEquals("X", ArtistNames.performer(compilation, unknown))
        assertEquals("Various Artists", ArtistNames.libraryArtist(compilation, unknown))
        assertEquals("Various Artists", ArtistNames.performer(track(artist = "Unknown artist", albumArtist = "Various Artists"), unknown))
        assertNull(ArtistNames.performer(track(artist = "Unknown artist"), unknown))
    }

    @Test
    fun sortKeyFoldsCaseAccentsTheAndSpaces() {
        assertEquals("beatles", ArtistNames.sortKey("The Beatles"))
        assertEquals("beatles", ArtistNames.sortKey("  THE   Beatles "))
        assertEquals("beyonce", ArtistNames.sortKey("Beyonc\u00e9"))
        assertEquals("the", ArtistNames.sortKey("The"))
        assertEquals("various artists", ArtistNames.sortKey("Various Artists"))
    }
}
