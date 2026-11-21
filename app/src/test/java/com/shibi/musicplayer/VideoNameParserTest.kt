package com.shibi.musicplayer

import com.shibi.musicplayer.domain.VideoIdentity
import com.shibi.musicplayer.domain.VideoIdentity.CameraSource
import com.shibi.musicplayer.domain.VideoIdentity.Episode
import com.shibi.musicplayer.domain.VideoIdentity.Movie
import com.shibi.musicplayer.domain.VideoIdentity.Other
import com.shibi.musicplayer.domain.VideoIdentity.Personal
import com.shibi.musicplayer.domain.VideoNameParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Year

class VideoNameParserTest {
    private fun parse(fileName: String, folder: String? = null): VideoIdentity =
        VideoNameParser.parse(fileName, folder, currentYear = 2026)

    private val bigBangFolder =
        "The Big Bang Theory Complete TV Series (Kaley Cuoco) 167GB 1080p H.264 (moviesbyrizzo TV uploads)"

    /** Real names from the library: (fileName, folder, expected). */
    private val fixtures = listOf(
        Triple(
            "Justice.League.Dark.Apokolips.War.2020.2160p.4K.BluRay.x265.10bit.AAC5.1-[YTS.MX].mkv",
            "Justice League Dark Apokolips War (2020) [2160p] [4K] [BluRay] [5.1] [YTS.MX]",
            Movie("Justice League Dark Apokolips War", 2020),
        ),
        Triple(
            "Justice.League.The.Flashpoint.Paradox.2013.1080p.BluRay.x264-[YTS.AM].mp4",
            null,
            Movie("Justice League The Flashpoint Paradox", 2013),
        ),
        Triple(
            "The Big Bang Theory (Kaley Cuoco) S01E01 1080p H.264 (moviesbyrizzo upload).mp4",
            bigBangFolder,
            Episode("The Big Bang Theory", 1, 1, null, null),
        ),
        Triple(
            "The Big Bang Theory (Bonus) S01E0 unaired pilot episode 480p Xvid (moviesbyrizzo TV series uploads).avi",
            bigBangFolder,
            Episode("The Big Bang Theory", 1, 0, null, "unaired pilot episode"),
        ),
        Triple("DJI_20250630052640_0036_D.MP4", "250630 Munich", Personal(CameraSource.DJI)),
        Triple("20261004_194422.mp4", "YouTube Videos", Personal(CameraSource.PHONE_TIMESTAMP)),
        Triple("NPA reel.mp4", "OH edited videos", Other("NPA reel")),
        Triple("0414.mp4", "0414", Other("0414")),
        Triple("0414(1).mp4", "0414(1)", Other("0414")),
        Triple(
            "movie.mkv",
            "Justice League The Flashpoint Paradox (2013) [BluRay] [1080p] [YTS.AM]",
            Movie("Justice League The Flashpoint Paradox", 2013),
        ),
    )

    @Test
    fun libraryFixtures_parseExactly() {
        for ((fileName, folder, expected) in fixtures) {
            assertEquals(fileName, expected, parse(fileName, folder))
        }
    }

    @Test
    fun movie_yearInBracketsOrLastToken() {
        assertEquals(Movie("Inception", 2010), parse("Inception (2010).mkv"))
        assertEquals(Movie("Inception", 2010), parse("Inception.2010.mkv"))
        assertEquals(Movie("Inception", 2010), parse("Inception [2010].mkv"))
    }

    @Test
    fun movie_numberInTitleIsKeptAndYearAfterItWins() {
        assertEquals(Movie("Blade Runner 2049", 2017), parse("Blade Runner 2049 (2017).mkv"))
        assertEquals(Movie("2001 A Space Odyssey", 1968), parse("2001.A.Space.Odyssey.1968.1080p.mkv"))
    }

    @Test
    fun movie_textBeforeYearIsNeverCutAtJunkWords() {
        assertEquals(Movie("Charlotte's Web", 2006), parse("Charlotte's Web (2006).mkv"))
        assertEquals(Movie("The Dark Knight IMAX", 2008), parse("The.Dark.Knight.IMAX.2008.1080p.mkv"))
    }

    @Test
    fun movie_hyphenatedTitlesKeepTheirHyphen() {
        assertEquals(Movie("Spider-Man", 2002), parse("Spider-Man.2002.mkv"))
        assertEquals(Movie("Ant-Man", 2015), parse("Ant-Man (2015).mkv"))
    }

    @Test
    fun movie_resolutionAfterYearIsNotAnEpisode() {
        assertEquals(Movie("Some Movie", 2019), parse("Some.Movie.2019.1920x1080.mkv"))
    }

    @Test
    fun movie_weakJunkAfterYearQualifiesIt() {
        assertEquals(Movie("Some Movie", 2019), parse("Some.Movie.2019.PROPER.DTS.mkv"))
    }

    @Test
    fun movie_yearInsideTitleWithoutJunkAfterIsNotAYear() {
        assertEquals(Other("1999 Revisited"), parse("1999 Revisited.mp4"))
    }

    @Test
    fun movie_yearsAfterNextYearAreNotYears() {
        assertEquals(Movie("Future", 2027), parse("Future.2027.mkv"))
        assertEquals(Other("Future 2028"), parse("Future.2028.mkv"))
        assertEquals(Other("Old 1919"), parse("Old.1919.mkv"))
    }

    @Test
    fun movie_bareYearHasNoTitleSoItIsOther() {
        assertEquals(Other("2012"), parse("2012.mkv"))
    }

    @Test
    fun movie_knownFalsePositive_trailingYearMakesAMovie() {
        assertEquals(Movie("Vacation", 2019), parse("Vacation 2019.mp4"))
    }

    @Test
    fun movieWithoutYear_needsAStrongJunkToken() {
        assertEquals(Movie("Some Film", null), parse("Some.Film.1080p.WEB-DL.x264-GRP.mkv"))
        assertEquals(Movie("Another Film", null), parse("Another_Film_BluRay.mkv"))
        assertEquals(Movie("Blu Film", null), parse("Blu Film Blu-ray.mkv"))
    }

    @Test
    fun movieWithoutYear_editionWordAloneIsNotJunk() {
        assertEquals(Other("The Proper Way"), parse("The Proper Way.mp4"))
        assertEquals(Other("Extended Family"), parse("Extended.Family.mp4"))
    }

    @Test
    fun movieWithoutYear_onlyJunkFallsBackToTheStem() {
        assertEquals(Movie("1080p", null), parse("1080p-GRP.mkv"))
    }

    @Test
    fun episode_multiEpisodeMarker() {
        assertEquals(Episode("Show Name", 2, 10, 11, null), parse("Show.Name.S02E10E11.720p.mkv"))
        assertEquals(Episode("Show Name", 2, 10, 11, null), parse("Show.Name.S02E10-E11.720p.mkv"))
    }

    @Test
    fun episode_descendingMultiEpisodeDropsTheEnd() {
        assertEquals(Episode("Show", 1, 5, null, null), parse("Show.S01E05E03.mkv"))
    }

    @Test
    fun episode_separatedSeasonAndEpisode() {
        assertEquals(Episode("Show", 1, 2, null, "Pilot"), parse("Show.S01.E02.Pilot.mkv"))
    }

    @Test
    fun episode_crossMarkerWithTitle() {
        assertEquals(Episode("Show Name", 3, 7, null, "Title"), parse("Show Name 3x07 Title.mkv"))
    }

    @Test
    fun episode_seasonEpisodeWords() {
        assertEquals(Episode("Show Name", 1, 2, null, null), parse("Show Name - Season 1 Episode 2.mp4"))
    }

    @Test
    fun episode_titleIsCutAtStrongJunkAndGroupIsDropped() {
        assertEquals(
            Episode("The Office", 2, 1, null, "The Dundies"),
            parse("The.Office.S02E01.The.Dundies.1080p.WEB-DL.x264-GRP.mkv"),
        )
    }

    @Test
    fun episode_emptyShowUsesTheFolder() {
        assertEquals(Episode("Lost", 1, 3, null, null), parse("S01E03.mkv", "Lost"))
    }

    @Test
    fun episode_seasonFolderIsNotAShowName() {
        assertEquals(Episode("S01E03", 1, 3, null, null), parse("S01E03.mkv", "Season 1"))
        assertEquals(Episode("S01E03", 1, 3, null, null), parse("S01E03.mkv", "S1"))
    }

    @Test
    fun episode_markersNeedNonAlphanumericNeighbours() {
        assertEquals(Movie("Clip", null), parse("Clip.1920x1080.mkv"))
        assertEquals(Other("Seasons01E02x"), parse("Seasons01E02x.mkv"))
    }

    @Test
    fun personal_prefixCameraNames() {
        val cases = mapOf(
            "DJI_0001.MP4" to CameraSource.DJI,
            "DJI_20250630052640_0036_D.MP4" to CameraSource.DJI,
            "VID_20240101_123456.mp4" to CameraSource.PHONE_TIMESTAMP,
            "VID_20240101_123456_1.mp4" to CameraSource.PHONE_TIMESTAMP,
            "PXL_20240101_123456789.TS.mp4" to CameraSource.PIXEL,
            "REC_20240101_123456.mp4" to CameraSource.PHONE_TIMESTAMP,
            "20261004_194422.mp4" to CameraSource.PHONE_TIMESTAMP,
            "20261004_194422(1).mp4" to CameraSource.PHONE_TIMESTAMP,
            "Screen_Recording_20240101-123456_YouTube.mp4" to CameraSource.SCREEN_RECORDING,
            "screen-20240101-123456.mp4" to CameraSource.SCREEN_RECORDING,
            "VID-20240101-WA0001.mp4" to CameraSource.MESSAGING,
            "WhatsApp Video 2024-01-05 at 10.12.33.mp4" to CameraSource.MESSAGING,
            "Snapchat-123456789.mp4" to CameraSource.MESSAGING,
            "InShot_20240101_123456789.mp4" to CameraSource.OTHER_CAMERA,
            "dji_0002.mp4" to CameraSource.DJI,
        )
        for ((name, source) in cases) {
            assertEquals(name, Personal(source), parse(name, "Movies"))
        }
    }

    @Test
    fun personal_wholeStemCameraNames() {
        val cases = mapOf(
            "IMG_1234.MOV" to CameraSource.OTHER_CAMERA,
            "IMG_1234 (1).MOV" to CameraSource.OTHER_CAMERA,
            "MVI_0042.MOV" to CameraSource.OTHER_CAMERA,
            "DSC_0001.MOV" to CameraSource.OTHER_CAMERA,
            "DSC0001.MOV" to CameraSource.OTHER_CAMERA,
            "MAH00123.MP4" to CameraSource.OTHER_CAMERA,
            "C0001.MP4" to CameraSource.OTHER_CAMERA,
            "C0001(2).MP4" to CameraSource.OTHER_CAMERA,
            "GX010123.MP4" to CameraSource.GOPRO,
            "GH010123.MP4" to CameraSource.GOPRO,
            "GOPR0123.MP4" to CameraSource.GOPRO,
            "GP010123.MP4" to CameraSource.GOPRO,
        )
        for ((name, source) in cases) {
            assertEquals(name, Personal(source), parse(name, "Movies"))
        }
    }

    @Test
    fun personal_wholeStemNamesMustEndThere() {
        assertEquals(Other("Classic1999"), parse("Classic1999.mp4"))
        assertEquals(Other("Doc2019"), parse("Doc2019.mp4"))
        assertEquals(Other("IMG 1234 edit"), parse("IMG_1234 edit.mp4"))
        assertEquals(Other("C00012"), parse("C00012.mp4"))
    }

    @Test
    fun personal_cameraFolder() {
        assertEquals(Personal(CameraSource.OTHER_CAMERA), parse("My trip.mp4", "Camera"))
        assertEquals(Personal(CameraSource.OTHER_CAMERA), parse("clip.mp4", "dcim"))
        assertEquals(Personal(CameraSource.OTHER_CAMERA), parse("clip.mp4", "Screen recordings"))
        assertEquals(Personal(CameraSource.OTHER_CAMERA), parse("clip.mp4", "WhatsApp Video"))
    }

    @Test
    fun personal_cameraFolderDoesNotOverrideAMovie() {
        assertEquals(Movie("Inception", 2010), parse("Inception (2010).mkv", "Camera"))
    }

    @Test
    fun folderMovie_replacesATooShortTitle() {
        assertEquals(Movie("Real Movie", 2001), parse("ab.1080p.mkv", "Real Movie (2001)"))
    }

    @Test
    fun folderMovie_keepsALongEnoughTitle() {
        assertEquals(Movie("Abc", null), parse("Abc.1080p.mkv", "Real Movie (2001)"))
    }

    @Test
    fun folderMovie_folderIsNeverExtensionStripped() {
        assertEquals(Movie("Inception", 2010), parse("video.mp4", "Inception.2010"))
    }

    @Test
    fun other_cleansGroupsAndSeparators() {
        assertEquals(Other("My Holiday Clip"), parse("[rarbg] My_Holiday.Clip (Bonus).mp4"))
        assertEquals(Other("Spider-Man Fan Edit"), parse("Spider-Man Fan Edit.mp4"))
    }

    @Test
    fun other_neverEmpty() {
        assertEquals(Other("[x]"), parse("[x].mp4"))
        assertEquals(Other("(1)"), parse("(1).mp4"))
    }

    @Test
    fun other_emptyNamesStillGetATitle() {
        assertEquals(Other(VideoNameParser.UNTITLED), parse(""))
        assertEquals(Other(".mp4"), parse(".mp4"))
    }

    @Test
    fun movie_nextTokenSkipsWhatCleaningDrops() {
        assertEquals(Movie("Inception", 2010), parse("Inception 2010 [YTS.MX].mkv"))
        assertEquals(Movie("Movie Name", 2019), parse("Movie.Name.2019-GRP.mkv"))
        assertEquals(Movie("Movie Name", 2010), parse("Movie Name 2010 (1).mp4"))
    }

    @Test
    fun movie_joinedJunkKeepsItsDot() {
        assertEquals(Movie("Some Film", null), parse("Some.Film.H.264.mkv"))
        assertEquals(Movie("Concert 5.1", 2019), parse("Concert.5.1.2019.mkv"))
    }

    @Test
    fun movieWithoutYear_cutNeverLeavesAnOpenBracket() {
        assertEquals(Movie("Some Film", null), parse("Some Film [1080p].mkv"))
        assertEquals(
            Episode("Show", 1, 1, null, "Pilot"),
            parse("Show S01E01 Pilot (Remastered 1080p).mkv"),
        )
    }

    @Test
    fun movie_dashOnlyNamesUseDashAsSeparator() {
        assertEquals(Movie("Movie Name", 2010), parse("Movie-Name-2010-1080p.mkv"))
        assertEquals(Movie("Spider-Man", 2002), parse("Spider-Man.2002.mkv"))
        assertEquals(Movie("Ant-Man", 2015), parse("Ant-Man (2015).mkv"))
    }

    @Test
    fun episode_anyNumberOfExtraEpisodesKeepsTheLast() {
        assertEquals(Episode("Show", 1, 1, 3, null), parse("Show.S01E01E02E03.mkv"))
    }

    @Test
    fun episode_folderShowIsCutAtJunkAndDropsCompleteAndSize() {
        assertEquals(Episode("The Big Bang Theory", 1, 3, null, null), parse("S01E03.mkv", bigBangFolder))
        assertEquals(Episode("Lost", 2, 1, null, null), parse("S02E01.mkv", "Lost Complete Series 4.5 GB"))
        assertEquals(Episode("Lost", 2, 1, null, null), parse("S02E01.mkv", "Lost.Complete"))
    }

    @Test
    fun episode_singleLetterAcronymStaysJoined() {
        assertEquals(Episode("S.W.A.T. 2017", 1, 1, null, null), parse("S.W.A.T.2017.S01E01.720p.mkv"))
        assertEquals(Other("S.W.A.T"), parse("S.W.A.T.mkv"))
    }

    @Test
    fun twoArgumentParseUsesTheClock() {
        val nextYear = Year.now().value + 1
        assertEquals(Movie("Future", nextYear), VideoNameParser.parse("Future.$nextYear.mkv", null))
    }

    @Test
    fun speed_20000MixedNamesUnderTwoSeconds() {
        val generated = (0 until 400).flatMap { i ->
            listOf(
                Pair("Generated.Movie.$i.${1950 + i % 70}.1080p.BluRay.x264-GRP$i.mkv", null),
                Pair("Show $i S0${i % 9 + 1}E${i % 30 + 1} Episode Title 720p.mkv", "Show $i"),
                Pair("VID_202401${10 + i % 20}_1234${10 + i % 50}.mp4", "Camera"),
                Pair("Holiday clip $i (1).mp4", "Trips"),
                Pair("clip$i.mp4", "Some Film (${1990 + i % 30}) [1080p]"),
            )
        }
        val pool = fixtures.map { Pair(it.first, it.second) } + generated
        val names = List(20_000) { pool[it % pool.size] }
        repeat(3) { names.take(2_000).forEach { (f, d) -> parse(f, d) } }

        val start = System.nanoTime()
        var personal = 0
        for ((f, d) in names) if (parse(f, d) is Personal) personal++
        val ms = (System.nanoTime() - start) / 1_000_000

        println("VideoNameParser: parsed ${names.size} names in $ms ms")
        assertTrue(personal > 0)
        assertTrue("took $ms ms", ms < 2_000)
    }
}
