package com.shibi.musicplayer

import com.shibi.musicplayer.domain.Classified
import com.shibi.musicplayer.domain.TvEpisode
import com.shibi.musicplayer.domain.TvFile
import com.shibi.musicplayer.domain.TvShow
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoClassifier
import com.shibi.musicplayer.domain.VideoCounts
import com.shibi.musicplayer.domain.VideoFilter
import com.shibi.musicplayer.domain.VideoIdentity.CameraSource
import com.shibi.musicplayer.domain.VideoIdentity.Episode
import com.shibi.musicplayer.domain.VideoIdentity.Movie
import com.shibi.musicplayer.domain.VideoIdentity.Other
import com.shibi.musicplayer.domain.VideoIdentity.Personal
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.domain.continueEpisode
import com.shibi.musicplayer.domain.countsOf
import com.shibi.musicplayer.domain.displayVideo
import com.shibi.musicplayer.domain.episodeCode
import com.shibi.musicplayer.domain.episodeLabel
import com.shibi.musicplayer.domain.minutesLeft
import com.shibi.musicplayer.domain.filterOf
import com.shibi.musicplayer.domain.shows
import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLibraryTest {
    private val collator = Collator.getInstance(Locale.US)

    @Test
    fun eachIdentityHasOneFilter() {
        assertEquals(VideoFilter.Movies, filterOf(Movie("Inception", 2010)))
        assertEquals(VideoFilter.Tv, filterOf(Episode("Lost", 1, 3, null, null)))
        assertEquals(VideoFilter.Personal, filterOf(Personal(CameraSource.GOPRO)))
        assertEquals(VideoFilter.Personal, filterOf(Other("NPA reel")))
    }

    @Test
    fun aFilterIsStoredByItsKeyAndJunkReadsAsAll() {
        VideoFilter.entries.forEach { assertEquals(it, VideoFilter.fromKey(it.key)) }
        assertEquals(listOf("all", "movies", "tv", "personal"), VideoFilter.entries.map { it.key })
        assertEquals(VideoFilter.All, VideoFilter.fromKey("shorts"))
        assertEquals(VideoFilter.All, VideoFilter.fromKey(null))
    }

    @Test
    fun countsCountFilesPerFilter() {
        val identities = listOf(
            Movie("Inception", 2010),
            Episode("Lost", 1, 1, null, null),
            Episode("Lost", 1, 2, null, null),
            Episode("Fargo", 1, 1, null, null),
            Personal(CameraSource.DJI),
            Other("reel"),
        )
        assertEquals(VideoCounts(all = 6, movies = 1, tv = 3, personal = 2), countsOf(identities))
        assertEquals(VideoCounts(), countsOf(emptyList()))
    }

    @Test
    fun aMovieShowsItsCleanedTitleAndAnythingElseIsUnchanged() {
        val raw = video(1L, "Inception.2010.1080p.mkv", title = "Inception.2010.1080p")
        assertEquals(raw.copy(title = "Inception"), displayVideo(raw, Movie("Inception", 2010)))
        assertSame(raw, displayVideo(raw, Episode("Lost", 1, 1, null, null)))
        assertSame(raw, displayVideo(raw, Personal(CameraSource.PIXEL)))
        assertSame(raw, displayVideo(raw, Other("x")))
    }

    @Test
    fun theClassifierParsesEachNameAndFolderOnce() {
        val calls = mutableListOf<Pair<String, String?>>()
        val classifier = VideoClassifier { name, folder ->
            calls += name to folder
            Other(name)
        }
        val a = video(1L, "a.mp4", folder = "Camera")
        val sameNameOtherFolder = video(2L, "a.mp4", folder = "Movies")
        val copyOfA = video(3L, "a.mp4", folder = "Camera")

        // A rescan gives new Video objects with the same names: nothing is parsed again.
        repeat(2) {
            assertEquals(Other("a.mp4"), classifier.identityOf(a.copy()))
            classifier.identityOf(sameNameOtherFolder.copy())
            classifier.identityOf(copyOfA.copy())
        }
        assertEquals(listOf("a.mp4" to "Camera", "a.mp4" to "Movies"), calls)
        // The cached entry, show key included, is the same object on every scan.
        assertSame(classifier.classify(a), classifier.classify(a.copy()))
    }

    @Test
    fun theClassifierKeepsOnlyTheCurrentScan() {
        var parses = 0
        val classifier = VideoClassifier { name, _ ->
            parses++
            Other(name)
        }
        val a = video(1L, "a.mp4")
        val b = video(2L, "b.mp4")
        classifier.identityOf(a)
        classifier.identityOf(b)
        classifier.retainOnly(listOf(b))
        classifier.identityOf(b)
        assertEquals(2, parses)
        // a left the library: it is parsed again if it comes back.
        classifier.identityOf(a)
        assertEquals(3, parses)
    }

    @Test
    fun anEpisodeCarriesItsShowKey() {
        assertEquals("office", Classified(Episode("The Office", 1, 1, null, null)).showKey)
        assertNull(Classified(Movie("Inception", 2010)).showKey)
        assertNull(TvFile.of(video(1L, "x.mkv"), Classified(Movie("Inception", 2010))))
    }

    @Test
    fun classifyingAndGroupingTwentyThousandFilesIsFast() {
        fun library(size: Int) = (0 until size).map { i ->
            val name = when (i % 4) {
                0 -> "Movie Number $i (${1950 + i % 70}).mkv"
                1 -> "Show ${'a' + i % 300 % 26}${'a' + i % 300 / 26}.S%02dE%02d.Episode.Title.1080p.mkv".format(i % 9 + 1, i % 24 + 1)
                2 -> "DJI_20250630${(100000 + i).toString().takeLast(6)}_0036_D.MP4"
                else -> "clip number $i.mp4"
            }
            video(i.toLong(), name, folder = "Folder ${i % 50}")
        }
        fun classifyAndGroup(videos: List<Video>): List<TvShow> {
            val classifier = VideoClassifier()
            val files = videos.mapNotNull { TvFile.of(it, classifier.classify(it)) }
            return shows(files, collator)
        }
        repeat(3) { classifyAndGroup(library(2_000)) }

        val videos = library(20_000)
        val start = System.nanoTime()
        val result = classifyAndGroup(videos)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("classify-and-group 20000 files: $ms ms")
        assertTrue(result.isNotEmpty())
        assertTrue("took $ms ms", ms < 2_000)
    }

    @Test
    fun theDefaultClassifierUsesTheNameParser() {
        assertEquals(Movie("Inception", 2010), VideoClassifier().identityOf(video(1L, "Inception (2010).mkv")))
    }

    @Test
    fun spellingsOfOneShowMergeUnderTheMostCommonName() {
        val result = shows(
            listOf(
                episode(1L, "the office", 1, 2),
                episode(2L, "The Office", 1, 1),
                episode(3L, "The Office", 2, 1),
                episode(4L, "Lost", 1, 1),
            ),
            collator,
        )
        // Keyed by ArtistNames.sortKey, so "The Office" sorts as "office", after "lost".
        assertEquals(listOf("lost", "office"), result.map { it.key })
        assertEquals(listOf("Lost", "The Office"), result.map { it.name })
        assertEquals(3, result[1].episodeCount)
    }

    @Test
    fun aTiedNameTakesTheSpellingThatSortsFirst() {
        val result = shows(listOf(episode(1L, "the office", 1, 1), episode(2L, "The Office", 1, 2)), collator)
        assertEquals(listOf("the office"), result.map { it.name })
    }

    @Test
    fun seasonsAscendAndEpisodesGoByNumberThenTitleThenId() {
        val result = shows(
            listOf(
                episode(10L, "Lost", 2, 1),
                episode(11L, "Lost", 1, 3),
                episode(12L, "Lost", 1, 1, title = "Pilot B"),
                episode(13L, "Lost", 1, 1, title = "Pilot A"),
                // Two files of one episode: both kept, by id.
                episode(15L, "Lost", 1, 2),
                episode(14L, "Lost", 1, 2),
            ),
            collator,
        ).single()
        assertEquals(listOf(1, 2), result.seasons.map { it.number })
        assertEquals(listOf(13L, 12L, 14L, 15L, 11L), result.seasons[0].episodes.map { it.video.id })
        assertEquals(listOf(13L, 12L, 14L, 15L, 11L, 10L), result.episodes.map { it.video.id })
        assertEquals(6, result.episodeCount)
    }

    @Test
    fun aMultiEpisodeFileKeepsItsRange() {
        val result = shows(listOf(episode(1L, "Show", 2, 10, last = 11)), collator).single()
        val only = result.seasons.single().episodes.single()
        assertEquals(TvEpisode(only.video, season = 2, episode = 10, lastEpisode = 11, title = null), only)
    }

    @Test
    fun continueIsTheEpisodeInProgressEvenAFewSecondsIn() {
        val show = lost()
        val progress = mapOf(
            uri(1L) to VideoProgress(0L, 2_000_000L, updatedAtMs = 100L),
            uri(2L) to VideoProgress(3_000L, 2_000_000L, updatedAtMs = 200L),
        )
        assertEquals(2L, continueEpisode(show, progress)?.video?.id)
    }

    @Test
    fun aWatchedEpisodeContinuesWithTheNextOne() {
        val progress = mapOf(uri(1L) to VideoProgress(0L, 2_000_000L, updatedAtMs = 100L))
        assertEquals(2L, continueEpisode(lost(), progress)?.video?.id)
    }

    @Test
    fun theLastEpisodeWatchedHasNothingToContinue() {
        val progress = mapOf(uri(3L) to VideoProgress(0L, 2_000_000L, updatedAtMs = 100L))
        assertNull(continueEpisode(lost(), progress))
    }

    @Test
    fun noProgressOrNoTimeHasNothingToContinue() {
        assertNull(continueEpisode(lost(), emptyMap()))
        // Rows from before the time was exposed carry 0: they do not count.
        assertNull(continueEpisode(lost(), mapOf(uri(2L) to VideoProgress(600_000L, 2_000_000L))))
    }

    @Test
    fun theLatestSaveWins() {
        val progress = mapOf(
            uri(3L) to VideoProgress(600_000L, 2_000_000L, updatedAtMs = 100L),
            uri(1L) to VideoProgress(600_000L, 2_000_000L, updatedAtMs = 300L),
            uri(2L) to VideoProgress(0L, 2_000_000L, updatedAtMs = 200L),
        )
        assertEquals(1L, continueEpisode(lost(), progress)?.video?.id)
    }

    @Test
    fun aSaveInTheLastFivePercentCountsAsWatched() {
        // 97.5% in: not a place to resume, so the next episode.
        val progress = mapOf(uri(1L) to VideoProgress(1_950_000L, 2_000_000L, updatedAtMs = 100L))
        assertEquals(2L, continueEpisode(lost(), progress)?.video?.id)
    }

    @Test
    fun withNoSavedLengthTheFileLengthDecidesWatched() {
        // libVLC saved no length; MediaStore's 2_000_000 ms puts 1_950_000 in the last 5%.
        val progress = mapOf(uri(1L) to VideoProgress(1_950_000L, 0L, updatedAtMs = 100L))
        assertEquals(2L, continueEpisode(lost(), progress)?.video?.id)
    }

    @Test
    fun aSecondCopyOfTheWatchedEpisodeIsSkipped() {
        val show = shows(listOf(episode(1L, "Lost", 1, 1), episode(2L, "Lost", 1, 1), episode(3L, "Lost", 1, 2)), collator).single()
        val progress = mapOf(uri(1L) to VideoProgress(0L, 2_000_000L, updatedAtMs = 100L))
        assertEquals(3L, continueEpisode(show, progress)?.video?.id)
    }

    @Test
    fun aMultiEpisodeFileSkipsTheEpisodesItHolds() {
        val show = shows(
            listOf(episode(1L, "Show", 2, 10, last = 11), episode(2L, "Show", 2, 11), episode(3L, "Show", 2, 12)),
            collator,
        ).single()
        val progress = mapOf(uri(1L) to VideoProgress(0L, 2_000_000L, updatedAtMs = 100L))
        assertEquals(3L, continueEpisode(show, progress)?.video?.id)
    }

    @Test
    fun theLastEpisodeOfASeasonContinuesWithTheNextSeason() {
        val show = shows(listOf(episode(1L, "Lost", 1, 3), episode(2L, "Lost", 2, 1), episode(3L, "Lost", 1, 2)), collator).single()
        val progress = mapOf(uri(1L) to VideoProgress(0L, 2_000_000L, updatedAtMs = 100L))
        assertEquals(2L, continueEpisode(show, progress)?.video?.id)
    }

    // --- Episode labels and minutes left ---

    @Test
    fun anEpisodeLabelLeadsWithItsNumberThenItsTitle() {
        val pilot = TvEpisode(video(1L, "Lost S01E03.mkv"), season = 1, episode = 3, lastEpisode = null, title = "Pilot")
        assertEquals("E03 · Pilot", episodeLabel(pilot, "Episode 3"))
        assertEquals("S01E03", episodeCode(pilot))
    }

    @Test
    fun aMultiEpisodeFileNamesItsRange() {
        val double = TvEpisode(video(1L, "Lost S02E10E11.mkv"), season = 2, episode = 10, lastEpisode = 11, title = "Finale")
        assertEquals("E10–E11 · Finale", episodeLabel(double, "Episodes 10–11"))
        assertEquals("S02E10–E11", episodeCode(double))
    }

    @Test
    fun anUntitledMultiEpisodeFileTakesTheRangeFallback() {
        val double = TvEpisode(video(1L, "Lost S02E10E11.mkv"), season = 2, episode = 10, lastEpisode = 11, title = null)
        assertEquals("E10–E11 · Episodes 10–11", episodeLabel(double, "Episodes 10–11"))
    }

    @Test
    fun anUntitledEpisodeTakesTheFallbackForItsNumber() {
        val plain = TvEpisode(video(1L, "Lost S01E07.mkv"), season = 1, episode = 7, lastEpisode = null, title = null)
        assertEquals("E07 · Episode 7", episodeLabel(plain, "Episode 7"))
    }

    @Test
    fun aSpecialIsSeasonZero() {
        val special = TvEpisode(video(1L, "Lost S00E01.mkv"), season = 0, episode = 1, lastEpisode = null, title = null)
        assertEquals("S00E01", episodeCode(special))
        assertEquals("E01 · Episode 1", episodeLabel(special, "Episode 1"))
    }

    @Test
    fun minutesLeftRoundUp() {
        // 2,000 s long, stopped at 1,339 s: 661 s (11 min 1 s) left reads 12.
        val video = video(1L, "Lost S01E01.mkv")
        assertEquals(12, minutesLeft(video, VideoProgress(1_339_000L, 2_000_000L, updatedAtMs = 10L)))
        // Exactly 10 minutes left stays 10.
        assertEquals(10, minutesLeft(video, VideoProgress(1_400_000L, 2_000_000L, updatedAtMs = 10L)))
    }

    @Test
    fun minutesLeftUseTheSavedLengthWhenMediaStoreHasNone() {
        val video = video(1L, "Lost S01E01.mkv").copy(durationMs = null)
        assertEquals(5, minutesLeft(video, VideoProgress(600_000L, 900_000L, updatedAtMs = 10L)))
    }

    @Test
    fun minutesLeftAreUnknownWithoutALength() {
        val video = video(1L, "Lost S01E01.mkv").copy(durationMs = null)
        assertNull(minutesLeft(video, VideoProgress(600_000L, 0L, updatedAtMs = 10L)))
    }

    @Test
    fun minutesLeftAreUnknownBeforeTheVideoStartedOrWithNoSave() {
        val video = video(1L, "Lost S01E01.mkv")
        assertNull(minutesLeft(video, VideoProgress(3_000L, 2_000_000L, updatedAtMs = 10L)))
        assertNull(minutesLeft(video, null))
    }

    private fun lost(): TvShow =
        shows((1..3).map { episode(it.toLong(), "Lost", 1, it) }, collator).single()

    private fun episode(id: Long, show: String, season: Int, number: Int, title: String? = null, last: Int? = null): TvFile =
        TvFile.of(video(id, "$show S${season}E$number.mkv"), Classified(Episode(show, season, number, last, title)))!!

    private fun uri(id: Long) = "content://media/external/video/media/$id"

    private fun video(id: Long, displayName: String, title: String = displayName, folder: String? = null) = Video(
        id = id,
        title = title,
        displayName = displayName,
        durationMs = 2_000_000L,
        sizeBytes = null,
        width = null,
        height = null,
        mimeType = "video/x-matroska",
        dateAddedSeconds = id,
        folder = folder,
        contentUri = uri(id),
    )
}
