package com.shibi.musicplayer

import com.shibi.musicplayer.video.EngineOptions
import com.shibi.musicplayer.video.MediaTrackType
import com.shibi.musicplayer.video.RawMediaTrack
import com.shibi.musicplayer.video.RawTrackDescription
import com.shibi.musicplayer.video.SubtitleFiles
import com.shibi.musicplayer.video.SubtitleTextSize
import com.shibi.musicplayer.video.TrackDelay
import com.shibi.musicplayer.video.TrackMapping
import com.shibi.musicplayer.video.TrackSheetState
import com.shibi.musicplayer.video.TrackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMappingTest {

    /** What libVLC 3 reports for an MKV with two dubs and two subtitle tracks. */
    private val movie = TrackSnapshot(
        audio = listOf(
            RawTrackDescription(-1, "Disable"),
            RawTrackDescription(1, "Track 1 - [English]"),
            RawTrackDescription(2, "Track 2 - [Japanese]"),
        ),
        subtitles = listOf(
            RawTrackDescription(-1, "Disable"),
            RawTrackDescription(3, "Track 1 - [English]"),
            RawTrackDescription(4, null),
        ),
        mediaTracks = listOf(
            RawMediaTrack(id = 0, codec = "h264", type = MediaTrackType.Video, info = "1920x1080"),
            RawMediaTrack(id = 1, language = "eng", codec = "a52 ", type = MediaTrackType.Audio, info = "6ch 48000 Hz"),
            RawMediaTrack(id = 2, language = "jpn", codec = "mp4a", type = MediaTrackType.Audio),
            RawMediaTrack(id = 3, language = "eng", codec = "ssa ", type = MediaTrackType.Text),
            RawMediaTrack(id = 4, language = "fre", codec = "subt", type = MediaTrackType.Text),
        ),
        selectedAudioId = 2,
        selectedSubtitleId = -1,
    )

    @Test
    fun audioRowsKeepLibVlcIdsAndNamesAndDropTheDisableEntry() {
        val audio = TrackMapping.audioOptions(movie)
        assertEquals(listOf(1, 2), audio.map { it.id })
        assertEquals(listOf("Track 1 - [English]", "Track 2 - [Japanese]"), audio.map { it.label })
        assertTrue(audio.none { it.isOff })
    }

    @Test
    fun theSelectedFlagFollowsLibVlcsCurrentTrack() {
        assertEquals(listOf(false, true), TrackMapping.audioOptions(movie).map { it.selected })
        val subtitles = TrackMapping.subtitleOptions(movie.copy(selectedSubtitleId = 4))
        assertEquals(listOf(false, false, true), subtitles.map { it.selected })
        // Exactly one subtitle row is ever selected.
        assertEquals(1, TrackMapping.subtitleOptions(movie).count { it.selected })
    }

    @Test
    fun theDisableEntryBecomesOffFirstAndIsSelectedWhenNothingElseIs() {
        val subtitles = TrackMapping.subtitleOptions(movie)
        assertEquals(listOf(-1, 3, 4), subtitles.map { it.id })
        assertTrue(subtitles.first().isOff)
        assertTrue(subtitles.first().selected)
        assertNull(subtitles.first().label)

        // libVLC left "Disable" out (or the file has no subtitles at all): Off is still offered.
        val none = TrackMapping.subtitleOptions(TrackSnapshot(selectedSubtitleId = 7))
        assertEquals(1, none.size)
        assertTrue(none.single().isOff && none.single().selected)

        // A "Disable" name on another id is still the Disable entry.
        assertTrue(TrackMapping.isDisable(RawTrackDescription(0, " disable ")))
        assertFalse(TrackMapping.isDisable(RawTrackDescription(0, "Disabled commentary")))
    }

    @Test
    fun languagesAndCodecsAreAddedOnlyWhereTheNameDoesNotSayThem() {
        val audio = TrackMapping.audioOptions(movie)
        // "[English]" already says "eng", so only the codec is added.
        assertEquals("AC3", audio[0].detail)
        assertEquals("jpn · AAC", audio[1].detail)
        val subtitles = TrackMapping.subtitleOptions(movie)
        assertEquals("ASS", subtitles[1].detail)
        // No name: the sheet shows "Track N", and the language still tells them apart.
        assertNull(subtitles[2].label)
        assertEquals("fre · Text", subtitles[2].detail)
        // A label that already names the language and codec gets no repeat.
        assertNull(TrackMapping.detail("English eng AC3", RawMediaTrack(1, language = "eng", codec = "a52")))
        assertNull(TrackMapping.detail("Track 1", null))
        assertEquals("XYZ1", TrackMapping.codecName(" XYZ1 "))
    }

    @Test
    fun aLoadedFileShowsItsNameAndIsMarkedExternal() {
        val loaded = movie.copy(
            subtitles = movie.subtitles + RawTrackDescription(9, "Track 3"),
            selectedSubtitleId = 9,
            externalNames = mapOf(9 to "Concert.en.srt"),
        )
        val row = TrackMapping.subtitleOptions(loaded).last()
        assertEquals("Concert.en.srt", row.label)
        assertTrue(row.external && row.selected)
        assertFalse(TrackMapping.subtitleOptions(loaded)[1].external)
    }

    @Test
    fun duplicateIdsFromLibVlcShowOnce() {
        val twice = movie.copy(audio = movie.audio + RawTrackDescription(1, "Track 1 - [English]"))
        assertEquals(listOf(1, 2), TrackMapping.audioOptions(twice).map { it.id })
    }

    @Test
    fun theCodecSummaryNamesEveryStreamForTheErrorLog() {
        assertEquals(
            listOf(
                "video h264 1920x1080",
                "audio a52 6ch 48000 Hz eng",
                "audio mp4a jpn",
                "text ssa eng",
                "text subt fre",
            ),
            TrackMapping.codecSummary(movie.mediaTracks),
        )
    }

    @Test
    fun onlySubtitleExtensionsAreLoaded() {
        listOf("a.srt", "b.ASS", "c.ssa", "d.vtt", "e.sub", "f.idx", "g.ttml", "Movie.en.SRT")
            .forEach { assertTrue(it, SubtitleFiles.isSupported(it)) }
        listOf("a.txt", "b.mp3", "srt", "c.srt.zip", "", null, "d.", "x.mkv")
            .forEach { assertFalse("$it", SubtitleFiles.isSupported(it)) }
        assertEquals(".srt .ass .ssa .vtt .sub .idx .ttml", SubtitleFiles.extensionsLabel())
    }

    @Test
    fun subtitleDelayStepsByATenthOfASecond() {
        val delay = TrackDelay.Subtitle
        assertEquals(100L, delay.step(0L, 1))
        assertEquals(-100L, delay.step(0L, -1))
        // Many taps never drift.
        var value = 0L
        repeat(25) { value = delay.step(value, 1) }
        assertEquals(2_500L, value)
        // An odd value from libVLC snaps to the grid first.
        assertEquals(200L, delay.step(130L, 1))
        assertEquals("+2.5 s", delay.label(2_500L))
        assertEquals("-0.1 s", delay.label(-100L))
        assertEquals("0.0 s", delay.label(0L))
    }

    @Test
    fun audioDelayStepsByFiftyMilliseconds() {
        val delay = TrackDelay.Audio
        assertEquals(50L, delay.step(0L, 1))
        assertEquals(-150L, delay.step(-100L, -1))
        assertEquals("+0.05 s", delay.label(50L))
        assertEquals("-1.25 s", delay.label(-1_250L))
        assertEquals("0.00 s", delay.label(0L))
    }

    @Test
    fun delaysStopAtTheLimit() {
        val limit = TrackDelay.LimitMs
        assertEquals(limit, TrackDelay.Subtitle.step(limit, 1))
        assertFalse(TrackDelay.Subtitle.canStep(limit, 1))
        assertTrue(TrackDelay.Subtitle.canStep(limit, -1))
        assertEquals(-limit, TrackDelay.Audio.step(-limit, -1))
        assertEquals(0L, TrackDelay.Audio.step(0L, 0))
    }

    @Test
    fun textSizeKeepsLibVlcsDefaultForMediumAndRoundTrips() {
        assertNull(SubtitleTextSize.Medium.relativeFontSize)
        // libVLC divides the picture height by this, so Large is the smaller number.
        assertTrue(SubtitleTextSize.Large.relativeFontSize!! < SubtitleTextSize.Small.relativeFontSize!!)
        SubtitleTextSize.entries.forEach { assertEquals(it, SubtitleTextSize.decode(it.encode())) }
        assertEquals(SubtitleTextSize.Medium, SubtitleTextSize.decode("Huge"))
        assertEquals(SubtitleTextSize.Medium, SubtitleTextSize.decode(null))
    }

    @Test
    fun theSheetStateCombinesTracksAndSettings() {
        val state = TrackSheetState.of(
            movie.copy(audioDelayMs = -50L, subtitleDelayMs = 300L),
            EngineOptions(hardwareDecoding = false, subtitleTextSize = SubtitleTextSize.Large),
        )
        assertEquals(2, state.audio.size)
        assertEquals(3, state.subtitles.size)
        assertEquals(-50L, state.audioDelayMs)
        assertEquals(300L, state.subtitleDelayMs)
        assertFalse(state.hardwareDecoding)
        assertEquals(SubtitleTextSize.Large, state.textSize)
        // Before a video plays there are no tracks, only Off.
        assertTrue(TrackSheetState().subtitles.single().isOff)
    }
}
