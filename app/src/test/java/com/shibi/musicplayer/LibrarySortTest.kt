package com.shibi.musicplayer

import com.shibi.musicplayer.domain.LibrarySorter
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.SortBands
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortGroup
import com.shibi.musicplayer.domain.SortLetters
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.SortedList
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.UnknownNames
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoFormat
import com.shibi.musicplayer.domain.VideoListEntry
import com.shibi.musicplayer.domain.VideoTimeline
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySortTest {
    private val collator: Collator = Collator.getInstance(Locale.ENGLISH)
    private val utc = ZoneId.of("UTC")
    private val unknown = UnknownNames(title = "Unknown title", artist = "Unknown artist", album = "Unknown album")

    private fun ms(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun video(
        id: Long,
        title: String = "v$id",
        takenMs: Long = 0L,
        addedSeconds: Long = 0L,
        size: Long? = null,
        duration: Long? = null,
    ) = Video(
        id = id,
        title = title,
        displayName = "$title.mp4",
        durationMs = duration,
        sizeBytes = size,
        width = null,
        height = null,
        mimeType = null,
        dateAddedSeconds = addedSeconds,
        folder = null,
        contentUri = "content://v/$id",
        dateTakenMs = takenMs,
    )

    private fun track(
        id: Long,
        title: String = "t$id",
        artist: String = "Artist",
        album: String = "Album",
        duration: Long = 200_000L,
        addedSeconds: Long = 0L,
        size: Long? = null,
    ) = Track(id, title, artist, album, duration, "content://a/$id", null, addedSeconds, size)

    private fun sortVideos(videos: List<Video>, field: SortField, ascending: Boolean): SortedList<Video> =
        LibrarySorter.videos(videos, SortSpec(field, ascending), collator, utc, unknown.title)

    private fun sortTracks(tracks: List<Track>, field: SortField, ascending: Boolean): SortedList<Track> =
        LibrarySorter.tracks(tracks, SortSpec(field, ascending), collator, utc, unknown)

    private fun SortedList<Video>.titles() = items.map { it.title }

    @JvmName("trackTitles")
    private fun SortedList<Track>.titles() = items.map { it.title }

    /** Every group is one run, and the runs cover the items in order. */
    private fun SortedList<*>.assertOneRunPerGroup() {
        assertEquals(runs.map { it.group.key }.distinct(), runs.map { it.group.key })
        var next = 0
        for (run in runs) {
            assertEquals(next, run.start)
            next += run.count
        }
        assertEquals(items.size, next)
    }

    // --- Specs ---

    @Test
    fun specsRoundTripAndAnythingElseFallsBackToTheDefault() {
        for (spec in LibrarySorts.MusicOptions) {
            assertEquals(spec, SortSpec.decode(spec.encode(), LibrarySorts.MusicOptions, LibrarySorts.MusicDefault))
        }
        assertEquals("date_desc", LibrarySorts.VideoDefault.encode())
        val fallback = LibrarySorts.VideoDefault
        assertEquals(fallback, SortSpec.decode(null, LibrarySorts.VideoOptions, fallback))
        assertEquals(fallback, SortSpec.decode("colour_asc", LibrarySorts.VideoOptions, fallback))
        // Videos have no artist sort, so a stray value never sticks there.
        assertEquals(fallback, SortSpec.decode("artist_asc", LibrarySorts.VideoOptions, fallback))
    }

    @Test
    fun bothDefaultsAreNewestFirstAndEveryFieldHasBothDirections() {
        assertEquals(SortSpec(SortField.Date, ascending = false), LibrarySorts.VideoDefault)
        assertEquals(SortSpec(SortField.Date, ascending = false), LibrarySorts.MusicDefault)
        assertEquals(8, LibrarySorts.VideoOptions.size)
        assertEquals(12, LibrarySorts.MusicOptions.size)
        for (field in SortField.entries) {
            assertTrue(SortSpec(field, true) in LibrarySorts.MusicOptions)
            assertTrue(SortSpec(field, false) in LibrarySorts.MusicOptions)
        }
        assertTrue(LibrarySorts.VideoOptions.none { it.field == SortField.Artist || it.field == SortField.Album })
    }

    // --- Date ---

    @Test
    fun dateNewestFirstMatchesTheOldTimelineOrderAndKeepsTheTakenThenAddedFallback() {
        val videos = listOf(
            video(1, takenMs = ms("2025-03-10T12:00:00Z")),
            // No DATE_TAKEN: DATE_ADDED (seconds) stands in.
            video(2, addedSeconds = ms("2025-04-01T12:00:00Z") / 1000),
            video(3),
            video(4, takenMs = ms("2025-03-10T12:00:00Z")),
            video(5),
        )
        val sorted = sortVideos(videos, SortField.Date, ascending = false)
        assertEquals(VideoTimeline.sortNewestFirst(videos), sorted.items)
        assertEquals(listOf(2L, 4L, 1L, 5L, 3L), sorted.items.map { it.id })
        assertEquals(
            listOf(SortGroup.Month(2025, 4), SortGroup.Month(2025, 3), SortGroup.Month(null, null)),
            sorted.runs.map { it.group },
        )
        sorted.assertOneRunPerGroup()
    }

    @Test
    fun dateOldestFirstIsTheReverseButUnknownDatesStayLast() {
        val videos = listOf(
            video(1, takenMs = ms("2025-03-10T12:00:00Z")),
            video(2, addedSeconds = ms("2025-04-01T12:00:00Z") / 1000),
            video(3),
            video(4, takenMs = ms("2025-03-10T12:00:00Z")),
            video(5),
        )
        val sorted = sortVideos(videos, SortField.Date, ascending = true)
        assertEquals(listOf(1L, 4L, 2L, 3L, 5L), sorted.items.map { it.id })
        assertTrue(sorted.runs.last().group.isUnknown)
    }

    @Test
    fun songsSortByDateAddedWithUnknownLast() {
        val tracks = listOf(
            track(1, addedSeconds = 1_700_000_000L),
            track(2),
            track(3, addedSeconds = 1_750_000_000L),
        )
        assertEquals(listOf(3L, 1L, 2L), sortTracks(tracks, SortField.Date, ascending = false).items.map { it.id })
        assertEquals(listOf(1L, 3L, 2L), sortTracks(tracks, SortField.Date, ascending = true).items.map { it.id })
    }

    // --- Names ---

    @Test
    fun namesSortAToZAndZToAWithTheIdBreakingTies() {
        val videos = listOf(video(1, "banana"), video(2, "Apple"), video(3, "cherry"), video(4, "apple"), video(5, "Apple"))
        val az = sortVideos(videos, SortField.Name, ascending = true)
        // The collator puts lower case first; two equal names keep MediaStore id order.
        assertEquals(listOf(4L, 2L, 5L, 1L, 3L), az.items.map { it.id })
        val za = sortVideos(videos, SortField.Name, ascending = false)
        assertEquals(az.items.reversed(), za.items)
        assertEquals(listOf("C", "B", "A"), za.runs.map { (it.group as SortGroup.Letter).letter })
    }

    @Test
    fun accentedNamesFoldIntoTheirLetterSoEachLetterIsOneRun() {
        // Éclair and Eclair are one E run: the header key "letter_E" appears once.
        val videos = listOf(video(1, "Zebra"), video(2, "Éclair"), video(3, "Eclair"), video(4, "eggs"))
        val sorted = sortVideos(videos, SortField.Name, ascending = true)
        assertEquals(listOf("Eclair", "Éclair", "eggs", "Zebra"), sorted.titles())
        assertEquals(listOf("E", "Z"), sorted.runs.map { (it.group as SortGroup.Letter).letter })
        sorted.assertOneRunPerGroup()
    }

    @Test
    fun aGroupComesFirstEvenWhereTheCollatorWouldSplitIt() {
        // The collator reads Æ as "AE", between "Adam" and "Apple", but Æ is its own letter:
        // sorting by name alone would give the A header twice. Group first keeps one A run.
        val videos = listOf(video(1, "Apple"), video(2, "Æon"), video(3, "Adam"), video(4, "Bee"))
        val sorted = sortVideos(videos, SortField.Name, ascending = true)
        assertEquals(listOf("Adam", "Apple", "Æon", "Bee"), sorted.titles())
        assertEquals(listOf("A", "Æ", "B"), sorted.runs.map { (it.group as SortGroup.Letter).letter })
        sorted.assertOneRunPerGroup()
    }

    @Test
    fun theDuplicateHeaderKeyCrashCannotHappenUnderANameSort() {
        val titles = listOf("Zebra", "Éclair", "Eclair", "Æon", "Adam", "Apple", "2Pac", "eggs", "Ölund", "Oak", "தமிழ்", "Ébène")
        for (ascending in listOf(true, false)) {
            val sorted = sortVideos(titles.mapIndexed { i, t -> video(i + 1L, t) }, SortField.Name, ascending)
            val keys = VideoTimeline.layout(sorted).entries.map { it.key }
            assertEquals("lazy-list keys must be unique", keys.size, keys.toSet().size)
            sorted.assertOneRunPerGroup()
        }
    }

    @Test
    fun digitsAndSymbolsGoUnderHashFirstAndLastInZToA() {
        val videos = listOf(video(1, "Zebra"), video(2, "2Pac"), video(3, "!!!"), video(4, "Abba"), video(5, "10 Years"))
        val az = sortVideos(videos, SortField.Name, ascending = true)
        assertEquals(SortLetters.HASH, (az.runs.first().group as SortGroup.Letter).letter)
        assertEquals(3, az.runs.first().count)
        assertEquals(listOf("Abba", "Zebra"), az.titles().takeLast(2))
        val za = sortVideos(videos, SortField.Name, ascending = false)
        assertEquals(SortLetters.HASH, (za.runs.last().group as SortGroup.Letter).letter)
    }

    @Test
    fun tamilLettersKeepTheirOwnGroups() {
        val tracks = listOf(track(1, "தமிழ்"), track(2, "அம்மா"), track(3, "ஔவை"), track(4, "தாய்"), track(5, "Song"))
        val sorted = sortTracks(tracks, SortField.Name, ascending = true)
        val letters = sorted.runs.map { (it.group as SortGroup.Letter).letter }
        assertTrue(letters.containsAll(listOf("த", "அ", "ஔ", "S")))
        // தமிழ் and தாய் share த; the vowel sign is not a letter of its own.
        assertEquals(2, sorted.runs.single { (it.group as SortGroup.Letter).letter == "த" }.count)
        assertTrue(letters.indexOf("அ") < letters.indexOf("த"))
        sorted.assertOneRunPerGroup()
    }

    @Test
    fun theFirstLetterSkipsPunctuationAndFoldsAccentsOnly() {
        assertEquals("E", SortLetters.of("Éclair"))
        assertEquals("N", SortLetters.of("ñu"))
        assertEquals("S", SortLetters.of("ßtraße"))
        assertEquals("T", SortLetters.of("'Til Tuesday"))
        assertEquals("H", SortLetters.of("  (hello)"))
        assertEquals("Æ", SortLetters.of("æon"))
        assertEquals("த", SortLetters.of("தமிழ்"))
        // AU is a Tamil letter of its own, not O plus a mark.
        assertEquals("ஔ", SortLetters.of("ஔவை"))
        assertEquals(SortLetters.HASH, SortLetters.of("2Pac"))
        assertEquals(SortLetters.HASH, SortLetters.of("!!!"))
        assertEquals(SortLetters.HASH, SortLetters.of(""))
    }

    @Test
    fun unknownTitlesSortLastInBothDirections() {
        // The placeholder in any case is unknown, not a title under U.
        val videos = listOf(video(1, "Unknown title"), video(2, "Beta"), video(3, " "), video(4, "Alpha"), video(5, "Unknown Title"))
        assertEquals(listOf(4L, 2L, 1L, 3L, 5L), sortVideos(videos, SortField.Name, ascending = true).items.map { it.id })
        assertEquals(listOf(2L, 4L, 5L, 3L, 1L), sortVideos(videos, SortField.Name, ascending = false).items.map { it.id })
        val tracks = listOf(track(1, "Unknown title"), track(2, "Beta"), track(3, "UNKNOWN TITLE"), track(4, "Alpha"))
        assertEquals(listOf(4L, 2L, 1L, 3L), sortTracks(tracks, SortField.Name, ascending = true).items.map { it.id })
    }

    @Test
    fun theUnknownArtistPlaceholderSortsLastNotUnderU() {
        val tracks = listOf(
            track(1, "One", artist = "Unknown artist"),
            track(2, "Two", artist = "Zed"),
            track(3, "Three", artist = "<unknown>"),
            track(4, "Four", artist = "Ulla"),
            track(5, "Five", artist = ""),
            // Windows Media Player writes the placeholder with a capital A.
            track(6, "Six", artist = "Unknown Artist"),
        )
        val az = sortTracks(tracks, SortField.Artist, ascending = true)
        assertEquals(listOf("Four", "Two"), az.titles().take(2))
        assertEquals(setOf(1L, 3L, 5L, 6L), az.items.drop(2).map { it.id }.toSet())
        assertTrue(az.runs.last().group.isUnknown)
        val za = sortTracks(tracks, SortField.Artist, ascending = false)
        assertEquals(listOf("Two", "Four"), za.titles().take(2))
        assertTrue(za.runs.last().group.isUnknown)
        // The same for an album placeholder that is not "unknown album" in any case.
        val albums = listOf(track(1, "One", album = "Zed"), track(2, "Two", album = "NO ALBUM"), track(3, "Three", album = "Abbey"))
        val unknownAlbum = UnknownNames(title = "Unknown title", album = "No album")
        val byAlbum = LibrarySorter.tracks(albums, SortSpec(SortField.Album, ascending = true), collator, utc, unknownAlbum)
        assertEquals(listOf(3L, 1L, 2L), byAlbum.items.map { it.id })
        assertTrue(byAlbum.runs.last().group.isUnknown)
    }

    @Test
    fun anArtistsSongsReadByTitleAndAlbumsSortTheSameWay() {
        val tracks = listOf(
            track(1, "Zulu", artist = "Abba", album = "Gold"),
            track(2, "Alpha", artist = "Abba", album = "Arrival"),
            track(3, "Mid", artist = "Queen", album = "Unknown album"),
        )
        assertEquals(listOf("Alpha", "Zulu", "Mid"), sortTracks(tracks, SortField.Artist, true).titles())
        assertEquals(listOf("Alpha", "Zulu", "Mid"), sortTracks(tracks, SortField.Album, true).titles())
        assertTrue(sortTracks(tracks, SortField.Album, false).runs.last().group.isUnknown)
    }

    // --- Size and length ---

    @Test
    fun sizesSortBothWaysWithUnknownLast() {
        val videos = listOf(video(1, size = 5_000L), video(2), video(3, size = 2_000_000_000L), video(4, size = 50_000_000L))
        assertEquals(listOf(3L, 4L, 1L, 2L), sortVideos(videos, SortField.Size, ascending = false).items.map { it.id })
        assertEquals(listOf(1L, 4L, 3L, 2L), sortVideos(videos, SortField.Size, ascending = true).items.map { it.id })
    }

    @Test
    fun aFileShownAsTenMegabytesIsInTheTenToHundredBand() {
        // 9.97 MB reads "10 MB" on the row, so it is banded as 10 MB, not "Under 10 MB".
        assertEquals("10 MB", VideoFormat.size(9_970_000L))
        assertEquals(10_000_000L, VideoFormat.shownBytes(9_970_000L))
        assertEquals(1, SortBands.band(VideoFormat.shownBytes(9_970_000L), SortBands.VideoSize))
        // 9.94 MB reads "9.9 MB" and stays under 10 MB.
        assertEquals("9.9 MB", VideoFormat.size(9_940_000L))
        assertEquals(0, SortBands.band(VideoFormat.shownBytes(9_940_000L), SortBands.VideoSize))
        val sorted = sortVideos(listOf(video(1, size = 9_970_000L), video(2, size = 9_940_000L)), SortField.Size, true)
        assertEquals(listOf(SortGroup.Band(0), SortGroup.Band(1)), sorted.runs.map { it.group })
    }

    @Test
    fun videoSizeBandEdges() {
        fun band(bytes: Long) = SortBands.band(VideoFormat.shownBytes(bytes), SortBands.VideoSize)
        assertEquals(0, band(1L))
        assertEquals(1, band(10_000_000L))
        assertEquals(1, band(99_400_000L))
        // 99.5 MB reads "100 MB".
        assertEquals(2, band(99_500_000L))
        assertEquals(2, band(999_000_000L))
        assertEquals(3, band(1_000_000_000L))
        assertEquals(3, band(3_940_000_000L))
        // 3.95 GB reads "4.0 GB".
        assertEquals(4, band(3_950_000_000L))
        assertEquals(4, band(40_000_000_000L))
    }

    @Test
    fun videoLengthBandEdgesAreExactBecauseRowsFloorTheirSeconds() {
        fun band(ms: Long) = SortBands.band(ms, SortBands.VideoLength)
        assertEquals("0:59", VideoFormat.duration(59_999L))
        assertEquals(0, band(59_999L))
        assertEquals(1, band(60_000L))
        assertEquals(1, band(299_999L))
        assertEquals(2, band(300_000L))
        assertEquals(3, band(1_200_000L))
        assertEquals(4, band(3_600_000L))
        assertEquals(5, band(7_200_000L))
        assertEquals(6, SortBands.VideoLength.size)
    }

    @Test
    fun songsHaveTheirOwnBands() {
        assertEquals(listOf(0, 1, 2, 3, 4), listOf(4_940_000L, 5_000_000L, 10_000_000L, 20_000_000L, 50_000_000L).map {
            SortBands.band(VideoFormat.shownBytes(it), SortBands.MusicSize)
        })
        // 4.97 MB reads "5.0 MB": 5-10 MB.
        assertEquals(1, SortBands.band(VideoFormat.shownBytes(4_970_000L), SortBands.MusicSize))
        assertEquals(listOf(0, 1, 2, 3, 4), listOf(119_999L, 120_000L, 240_000L, 360_000L, 600_000L).map {
            SortBands.band(it, SortBands.MusicLength)
        })
    }

    @Test
    fun aVideosLengthIsTheOneItsRowShows() {
        // No MediaStore length: libVLC's saved length (the row's) puts it in place.
        val videos = listOf(video(1, duration = 120_000L), video(2), video(3, duration = 30_000L))
        val saved = mapOf("content://v/2" to 600_000L)
        val sorted = LibrarySorter.videos(videos, SortSpec(SortField.Length, false), collator, utc, unknown.title) {
            it.durationMs ?: saved[it.contentUri]
        }
        assertEquals(listOf(2L, 1L, 3L), sorted.items.map { it.id })
        assertEquals(listOf(2, 1, 0), sorted.runs.map { (it.group as SortGroup.Band).index })
    }

    @Test
    fun songLengthAndSizeSortWithUnknownLast() {
        val tracks = listOf(track(1, duration = 0L), track(2, duration = 300_000L), track(3, duration = 100_000L))
        assertEquals(listOf(2L, 3L, 1L), sortTracks(tracks, SortField.Length, false).items.map { it.id })
        assertEquals(listOf(3L, 2L, 1L), sortTracks(tracks, SortField.Length, true).items.map { it.id })
        val sized = listOf(track(1, size = null), track(2, size = 8_000_000L), track(3, size = 3_000_000L))
        assertEquals(listOf(2L, 3L, 1L), sortTracks(sized, SortField.Size, false).items.map { it.id })
        assertEquals(listOf(3L, 2L, 1L), sortTracks(sized, SortField.Size, true).items.map { it.id })
    }

    // --- Layout ---

    @Test
    fun aNameSortedLayoutHasOneLetterHeaderPerRun() {
        val sorted = sortVideos(listOf(video(1, "Beta"), video(2, "alpha"), video(3, "Apex")), SortField.Name, true)
        val layout = VideoTimeline.layout(sorted)
        assertEquals(listOf("letter_A", 2L, 3L, "letter_B", 1L), layout.entries.map { it.key })
        val header = layout.entries[3] as VideoListEntry.Header
        assertEquals(SortGroup.Letter("B"), header.section.group)
        assertEquals(3, header.section.startIndex)
        assertEquals(1, header.section.count)
    }
}
