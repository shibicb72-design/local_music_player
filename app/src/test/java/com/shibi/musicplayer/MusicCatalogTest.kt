package com.shibi.musicplayer

import com.shibi.musicplayer.domain.AlbumSort
import com.shibi.musicplayer.domain.MusicCatalog
import com.shibi.musicplayer.domain.SortGroup
import com.shibi.musicplayer.domain.SortRun
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.UnknownNames
import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicCatalogTest {
    private val unknown = UnknownNames(title = "Unknown title", artist = "Unknown artist", album = "Unknown album")
    private val collator: Collator = Collator.getInstance(Locale.US)
    private var nextId = 1L

    private fun track(
        title: String,
        artist: String,
        album: String,
        albumArtist: String? = null,
        number: Int? = null,
        disc: Int? = null,
        year: Int? = null,
        folder: String? = null,
        fileName: String? = null,
        art: String? = null,
        durationMs: Long = 1_000L,
    ) = Track(
        id = nextId++, title = title, artist = artist, album = album, durationMs = durationMs,
        contentUri = "content://$title", albumArtUri = art, albumArtist = albumArtist,
        trackNumber = number, discNumber = disc, year = year, folder = folder, fileName = fileName,
    )

    private fun library(): List<Track> = listOf(
        track("Revolver One", "The Beatles", "Revolver", "The Beatles", 1, year = 1966),
        track("Revolver Two", "The Beatles", "Revolver", "The Beatles", 2, year = 1966),
        track("Abbey One", "The Beatles", "Abbey Road", "The Beatles", 1, year = 1969),
        track("Abbey Two", "The Beatles", "Abbey Road", "The Beatles", 2, year = 1969),
        track("Formation", "Beyoncé", "Lemonade", "Beyoncé", 1, year = 2016),
        track("One A", "Daft Punk", "Album One", "Daft Punk", 1, year = 2001),
        track("One B", "Daft Punk", "Album One", "Daft Punk", 2, year = 2001),
        track("One C", "Daft Punk", "Album One", "Daft Punk", 3, year = 2001),
        track("Two A", "Daft Punk", "Album Two", "Daft Punk", 1, year = 2005),
        track("Two B", "Daft Punk", "Album Two", "Daft Punk", 2, year = 2005),
        track("Two C", "Daft Punk (ft. Someone)", "Album Two", null, 3, year = 2005),
        track("Two D", "Daft Punk", "Album Two", "Daft Punk", 4, year = 2005),
        track("Club DP", "Daft Punk", "Club Mix", "Various Artists", 1, year = 2010),
        track("Club Zedd", "Zedd", "Club Mix", "Various Artists", 2, year = 2010),
        track("Clarity", "Zedd", "Clarity", "Zedd", 1, year = 2012),
        track("Stay", "Zedd feat. Guest Singer", "Clarity", "Zedd", 2, year = 2012),
        track("Dancing Queen", "ABBA", "Greatest Hits", "ABBA", 1, disc = 2, year = 1976),
        track("Waterloo", "ABBA", "Greatest Hits", "ABBA", 2, disc = 1, year = 1976),
        track("Mamma Mia", "ABBA", "Greatest Hits", "ABBA", 1, disc = 1, year = 1976),
        track("Fernando", "ABBA", "Greatest Hits", "ABBA", 2, disc = 2, year = 1976),
        track("Bohemian Rhapsody", "Unknown artist", "TestSongs", folder = "Music/TestSongs", fileName = "03 Queen - Bohemian Rhapsody.mp3"),
        track("Zeta", "Unknown artist", "TestSongs", folder = "Music/TestSongs", fileName = "zeta.mp3"),
        track("Alpha", "Unknown artist", "TestSongs", folder = "Music/TestSongs", fileName = "alpha.mp3"),
        track("California", "2Pac", "All Eyez", "2Pac"),
    )

    private fun catalog(tracks: List<Track> = library()) = MusicCatalog.build(tracks, unknown, collator)

    @Test
    fun artistsAreOrderedHashFirstThenByLetterWithUnknownLast() {
        val artists = catalog().artists
        assertEquals(
            listOf("2pac", "abba", "beatles", "beyonce", "daft punk", "queen", "various artists", "zedd", null),
            artists.map { it.key },
        )
        assertNull(artists.last().name)
        assertEquals("The Beatles", artists.first { it.key == "beatles" }.name)
        assertEquals("Beyoncé", artists.first { it.key == "beyonce" }.name)
    }

    @Test
    fun theLetterRunsGiveEachLetterOneRunAndTheBeatlesSitUnderB() {
        assertEquals(
            listOf(
                SortRun(SortGroup.Letter("#"), 0, 1),
                SortRun(SortGroup.Letter("A"), 1, 1),
                SortRun(SortGroup.Letter("B"), 2, 2),
                SortRun(SortGroup.Letter("D"), 4, 1),
                SortRun(SortGroup.Letter("Q"), 5, 1),
                SortRun(SortGroup.Letter("V"), 6, 1),
                SortRun(SortGroup.Letter("Z"), 7, 1),
                SortRun(SortGroup.Letter(null), 8, 1),
            ),
            catalog().artistRuns,
        )
    }

    @Test
    fun theCountsFollowTheArtistPageDefinition() {
        val artists = catalog().artists.associateBy { it.key }
        val daft = artists.getValue("daft punk")
        assertEquals(2, daft.albumCount)
        assertEquals(8, daft.songCount)
        val various = artists.getValue("various artists")
        assertEquals(1, various.albumCount)
        assertEquals(2, various.songCount)
        val queen = artists.getValue("queen")
        assertEquals(0, queen.albumCount)
        assertEquals(1, queen.songCount)
        assertEquals(2, artists.getValue("beatles").albumCount)
        // Zedd's own two songs plus his compilation track.
        assertEquals(3, artists.getValue("zedd").songCount)
        assertEquals(1, artists.getValue("zedd").albumCount)
    }

    @Test
    fun aFolderNamedAlbumOfAnUntaggedFileIsNoAlbum() {
        val catalog = catalog()
        assertTrue(catalog.albums(AlbumSort.Title).none { it.title == "TestSongs" })
        val queen = catalog.artistPage("queen")!!
        assertTrue(queen.albums.isEmpty())
        assertEquals(listOf("Bohemian Rhapsody"), queen.songs.map { it.title })
    }

    @Test
    fun aFolderNamedAlbumStaysWhenTheFileHasAnArtistTag() {
        val tracks = listOf(track("Song", "Someone", "TestSongs", folder = "Music/testsongs"))
        assertEquals(listOf("TestSongs"), catalog(tracks).albums(AlbumSort.Title).map { it.title })
    }

    @Test
    fun theDaftPunkPage() {
        val page = catalog().artistPage("daft punk")!!
        assertEquals("Daft Punk", page.artist.name)
        assertEquals(listOf("Album One", "Album Two"), page.albums.map { it.title })
        assertEquals(listOf("Club Mix"), page.appearsOn.map { it.title })
        assertEquals(
            listOf("One A", "One B", "One C", "Two A", "Two B", "Two C", "Two D", "Club DP"),
            page.songs.map { it.title },
        )
        assertEquals(1, page.songs.count { it.title == "Two C" })
    }

    @Test
    fun aPerformerWithoutAlbumsOfHisOwnGetsNoEntryAndNoPage() {
        val tracks = listOf(
            track("Duet", "Guest", "Host Album", "Host", 1),
            track("Solo", "Host", "Host Album", "Host", 2),
        )
        val catalog = catalog(tracks)
        assertEquals(listOf("host"), catalog.artists.map { it.key })
        assertNull(catalog.artistPage("guest"))
        assertEquals(2, catalog.artistPage("host")!!.songs.size)
    }

    @Test
    fun theZeddPageHasHisFeaturedSongOnceAndTheCompilationUnderAppearsOn() {
        val page = catalog().artistPage("zedd")!!
        assertEquals(listOf("Clarity"), page.albums.map { it.title })
        assertEquals(listOf("Club Mix"), page.appearsOn.map { it.title })
        assertEquals(listOf("Clarity", "Stay", "Club Zedd"), page.songs.map { it.title })
    }

    @Test
    fun theTwoDiscAlbumPageHasDiscsInTrackOrder() {
        val catalog = catalog()
        val key = catalog.albums(AlbumSort.Title).first { it.title == "Greatest Hits" }.key
        val page = catalog.albumPage(key)!!
        assertEquals(listOf(1, 2), page.discs.map { it.number })
        assertEquals(listOf("Mamma Mia", "Waterloo"), page.discs[0].tracks.map { it.title })
        assertEquals(listOf("Dancing Queen", "Fernando"), page.discs[1].tracks.map { it.title })
        assertEquals(4, page.album.songCount)
        assertNull(catalog.albumPage("missing"))
    }

    @Test
    fun aSingleDiscAlbumHasOneDiscAndMissingTrackNumbersComeLast() {
        val tracks = listOf(
            track("Zed", "Band", "LP", "Band", null),
            track("Bee", "Band", "LP", "Band", 2),
            track("Aye", "Band", "LP", "Band", null),
            track("Cee", "Band", "LP", "Band", 1),
        )
        val catalog = catalog(tracks)
        val page = catalog.albumPage(catalog.albums(AlbumSort.Title).single().key)!!
        assertEquals(1, page.discs.size)
        assertEquals(listOf("Cee", "Bee", "Aye", "Zed"), page.discs[0].tracks.map { it.title })
    }

    @Test
    fun sameNamedAlbumsOfTwoArtistsAreTwoEntries() {
        val tracks = listOf(
            track("A1", "Abba", "Greatest Hits", "Abba"),
            track("Q1", "Queen", "Greatest Hits", "Queen"),
            track("Q2", "Queen", "greatest hits", "Queen"),
        )
        val albums = catalog(tracks).albums(AlbumSort.Title)
        assertEquals(2, albums.size)
        assertEquals(listOf("Abba", "Queen"), albums.map { it.artistName })
        assertEquals(listOf(1, 2), albums.map { it.songCount })
    }

    @Test
    fun albumEntriesCarryTheMostCommonSpellingYearArtAndDuration() {
        val tracks = listOf(
            track("A", "Band", "Live", "Band", year = 2001, art = null, durationMs = 10),
            track("B", "Band", "LIVE", "Band", year = 2000, art = "art://b", durationMs = 20),
            track("C", "Band", "LIVE", "Band", year = 2001, art = "art://c", durationMs = 30),
            track("D", "Band", "Live", "Band", year = null, durationMs = 40),
        )
        val album = catalog(tracks).albums(AlbumSort.Title).single()
        // "Live" and "LIVE" tie at two each: the one that sorts first wins.
        assertEquals("Live", album.title)
        assertEquals(2001, album.year)
        assertEquals("art://b", album.albumArtUri)
        assertEquals(100L, album.durationMs)
        assertEquals("band", album.artistKey)
        assertEquals("Band", album.artistName)
    }

    @Test
    fun aYearTieTakesTheEarliest() {
        val tracks = listOf(
            track("A", "Band", "LP", "Band", year = 2005),
            track("B", "Band", "LP", "Band", year = 1999),
        )
        assertEquals(1999, catalog(tracks).albums(AlbumSort.Title).single().year)
    }

    @Test
    fun eachAlbumSortHasItsOrder() {
        val catalog = catalog()
        assertEquals(
            listOf(
                "Abbey Road", "Album One", "Album Two", "All Eyez", "Clarity", "Club Mix",
                "Greatest Hits", "Lemonade", "Revolver",
            ),
            catalog.albums(AlbumSort.Title).map { it.title },
        )
        assertEquals(
            listOf(
                "All Eyez", "Greatest Hits", "Abbey Road", "Revolver", "Lemonade",
                "Album One", "Album Two", "Club Mix", "Clarity",
            ),
            catalog.albums(AlbumSort.Artist).map { it.title },
        )
        assertEquals(
            listOf(
                "Lemonade", "Clarity", "Club Mix", "Album Two", "Album One",
                "Greatest Hits", "Abbey Road", "Revolver", "All Eyez",
            ),
            catalog.albums(AlbumSort.YearNewest).map { it.title },
        )
    }

    @Test
    fun albumsAreNotSortedPerCall() {
        val catalog = catalog()
        assertSame(catalog.albums(AlbumSort.Artist), catalog.albums(AlbumSort.Artist))
    }

    @Test
    fun theUnknownArtistPageHasNoAlbumsAndItsSongsByTitle() {
        val catalog = catalog(library() + track("Real", "Unknown artist", "Real LP"))
        val page = catalog.artistPage(null)!!
        assertNull(page.artist.key)
        assertNull(page.artist.name)
        assertTrue(page.albums.isEmpty())
        assertTrue(page.appearsOn.isEmpty())
        assertEquals(listOf("Alpha", "Real", "Zeta"), page.songs.map { it.title })
        assertEquals(0, page.artist.albumCount)
        assertEquals(3, page.artist.songCount)
        // The untagged "Real LP" has no artist, so nobody owns its album.
        val album = catalog.albums(AlbumSort.Artist).last()
        assertEquals("Real LP", album.title)
        assertNull(album.artistKey)
        assertNull(album.artistName)
    }

    @Test
    fun noUnknownEntryWhenEveryTrackHasAnArtist() {
        assertNull(catalog(listOf(track("A", "Band", "LP", "Band"))).artistPage(null))
    }

    @Test
    fun anArtistsOwnAlbumsRunByYearNotTitle() {
        val page = catalog().artistPage("beatles")!!
        assertEquals(listOf("Revolver", "Abbey Road"), page.albums.map { it.title })
        assertEquals(
            listOf("Revolver One", "Revolver Two", "Abbey One", "Abbey Two"),
            page.songs.map { it.title },
        )
    }

    @Test
    fun aTieBetweenArtistSpellingsTakesTheOneThatSortsFirst() {
        val tracks = listOf(
            track("A", "ABBA", "Gold", "ABBA"),
            track("B", "Abba", "Gold", "Abba"),
        )
        val artist = catalog(tracks).artists.single()
        assertEquals("abba", artist.key)
        assertEquals("Abba", artist.name)
    }

    @Test
    fun anArtistOnSeveralAlbumsListsThemByYearAndSongsFollowThatOrder() {
        val tracks = listOf(
            track("Own", "Guest", "G LP", "Guest", 1, year = 1995),
            track("Late", "Guest", "Alpha Hits", "Various Artists", 1, year = 2010),
            track("Early", "Guest", "Zeta Hits", "Various Artists", 2, year = 2000),
            track("Other", "Someone", "Zeta Hits", "Various Artists", 3, year = 2000),
        )
        val page = catalog(tracks).artistPage("guest")!!
        assertEquals(listOf("G LP"), page.albums.map { it.title })
        assertEquals(listOf("Zeta Hits", "Alpha Hits"), page.appearsOn.map { it.title })
        assertEquals(listOf("Own", "Early", "Late"), page.songs.map { it.title })
    }

    @Test
    fun songsWithNoAlbumFollowTheAlbumSongs() {
        val tracks = listOf(
            track("Mango", "Solo", "Unknown album", "Solo"),
            track("Zzz", "Solo", "Unknown album", "Solo", 5),
            track("Zed", "Solo", "LP", "Solo", 1),
        )
        val page = catalog(tracks).artistPage("solo")!!
        assertEquals(listOf("LP"), page.albums.map { it.title })
        assertEquals(listOf("Zed", "Zzz", "Mango"), page.songs.map { it.title })
    }

    @Test
    fun theArtistArtIsTheFirstOwnAlbumInPageOrderThatHasArt() {
        val tracks = listOf(
            track("A", "Band", "Early", "Band", year = 1990),
            track("B", "Band", "Later", "Band", year = 2010, art = "art://later"),
            track("C", "Band", "Late", "Band", year = 2000, art = "art://late"),
            track("D", "Mute", "Quiet", "Mute", year = 2000),
            track("E", "Unknown artist", "Loose", art = "art://loose"),
        )
        val artists = catalog(tracks).artists.associateBy { it.key }
        assertEquals("art://late", artists.getValue("band").artUri)
        assertNull(artists.getValue("mute").artUri)
        assertNull(artists.getValue(null).artUri)
    }

    @Test
    fun buildOnTwentyThousandTracksIsFast() {
        fun many(count: Int) = (0 until count).map { i ->
            val artist = "Artist ${i % 2_000}"
            track("Song $i", artist, "Album ${(i / 5) % 4}", artist, number = i % 12, year = 1990 + i % 30)
        }
        // Compilations whose tracks each have their own guest performer.
        fun compilations(count: Int) = (0 until count).map { i ->
            track("Comp $i", "Guest $i", "Comp ${i % 20}", "Various Artists", number = i, year = 2000 + i % 20)
        }
        MusicCatalog.build(many(2_000) + compilations(200), unknown, collator)
        val tracks = many(20_000) + compilations(2_000)
        val start = System.nanoTime()
        val catalog = MusicCatalog.build(tracks, unknown, collator)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("MusicCatalog.build(20000 tracks) took $ms ms")
        assertEquals(2_001, catalog.artists.size)
        assertTrue("build took $ms ms", ms < 2_000)
    }
}
