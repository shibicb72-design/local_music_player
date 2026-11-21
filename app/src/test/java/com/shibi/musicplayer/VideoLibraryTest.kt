package com.shibi.musicplayer

import com.shibi.musicplayer.domain.TvEpisode
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
import com.shibi.musicplayer.domain.filterOf
import com.shibi.musicplayer.domain.shows
import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    private fun lost(): TvShow =
        shows((1..3).map { episode(it.toLong(), "Lost", 1, it) }, collator).single()

    private fun episode(id: Long, show: String, season: Int, number: Int, title: String? = null, last: Int? = null) =
        video(id, "$show S${season}E$number.mkv") to Episode(show, season, number, last, title)

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
