package com.shibi.musicplayer

import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFormatTest {
    private val dash = VideoFormat.MISSING

    @Test
    fun duration_missingOrZeroIsADash() {
        assertEquals(dash, VideoFormat.duration(null))
        assertEquals(dash, VideoFormat.duration(0L))
        assertEquals(dash, VideoFormat.duration(-5L))
    }

    @Test
    fun duration_underAnHourIsMinutesAndPaddedSeconds() {
        assertEquals("0:00", VideoFormat.duration(999L))
        assertEquals("0:42", VideoFormat.duration(42_000L))
        assertEquals("1:05", VideoFormat.duration(65_000L))
        assertEquals("59:59", VideoFormat.duration((59 * 60 + 59) * 1_000L))
    }

    @Test
    fun duration_floorsTheLastMillisecondBeforeAnHour() {
        assertEquals("59:59", VideoFormat.duration(3_599_999L))
        assertEquals("1:00:00", VideoFormat.duration(3_600_000L))
    }

    @Test
    fun duration_pastAnHourPadsMinutesAndSeconds() {
        assertEquals("1:02:03", VideoFormat.duration(3_723_000L))
        assertEquals("10:00:01", VideoFormat.duration(36_001_000L))
    }

    @Test
    fun size_missingOrZeroIsADash() {
        assertEquals(dash, VideoFormat.size(null))
        assertEquals(dash, VideoFormat.size(0L))
        assertEquals(dash, VideoFormat.size(-1L))
    }

    @Test
    fun size_usesDecimalUnitsWithOneDecimalBelowTen() {
        assertEquals("512 B", VideoFormat.size(512L))
        assertEquals("1.5 KB", VideoFormat.size(1_500L))
        assertEquals("812 MB", VideoFormat.size(812_300_000L))
        assertEquals("1.4 GB", VideoFormat.size(1_400_000_000L))
        assertEquals("1.0 GB", VideoFormat.size(1_000_000_000L))
        assertEquals("12 GB", VideoFormat.size(12_400_000_000L))
        assertEquals("2.1 TB", VideoFormat.size(2_100_000_000_000L))
    }

    @Test
    fun size_roundingUpToAThousandMovesToTheNextUnit() {
        assertEquals("999 MB", VideoFormat.size(999_400_000L))
        assertEquals("1.0 GB", VideoFormat.size(999_600_000L))
        assertEquals("1.0 KB", VideoFormat.size(999L + 1L))
        assertEquals("10 MB", VideoFormat.size(9_960_000L))
    }

    @Test
    fun resolution_missingOrZeroIsADash() {
        assertEquals(dash, VideoFormat.resolution(null, 1080))
        assertEquals(dash, VideoFormat.resolution(1920, null))
        assertEquals(dash, VideoFormat.resolution(0, 1080))
        assertEquals(dash, VideoFormat.resolution(1920, 0))
    }

    @Test
    fun resolution_fourKFromTheShortSideInLandscapeAndPortrait() {
        assertEquals("4K", VideoFormat.resolution(3840, 2160))
        assertEquals("4K", VideoFormat.resolution(2160, 3840))
        assertEquals("4K", VideoFormat.resolution(4096, 2160))
    }

    @Test
    fun resolution_namedTiersFromTheShortSide() {
        assertEquals("1440p", VideoFormat.resolution(2560, 1440))
        assertEquals("1440p", VideoFormat.resolution(1440, 3200))
        assertEquals("1080p", VideoFormat.resolution(1920, 1080))
        assertEquals("1080p", VideoFormat.resolution(1080, 1920))
        assertEquals("720p", VideoFormat.resolution(1280, 720))
        assertEquals("480p", VideoFormat.resolution(854, 480))
    }

    @Test
    fun resolution_oddSizesUseTheShortSideOrFallBackToWidthByHeight() {
        assertEquals("720p", VideoFormat.resolution(1920, 800))
        assertEquals("1080p", VideoFormat.resolution(2560, 1080))
        assertEquals("480p", VideoFormat.resolution(720, 576))
        assertEquals("640×360", VideoFormat.resolution(640, 360))
        assertEquals("360×640", VideoFormat.resolution(360, 640))
        assertEquals("1×1", VideoFormat.resolution(1, 1))
    }

    @Test
    fun titleFrom_prefersTheTitleThenTheFileNameWithoutExtension() {
        assertEquals("Holiday", Video.titleFrom(" Holiday ", "VID_1.mp4"))
        assertEquals("VID_1", Video.titleFrom(null, "VID_1.mp4"))
        assertEquals("VID_1", Video.titleFrom("  ", "VID_1.mp4"))
        assertEquals("VID_1", Video.titleFrom("<unknown>", "VID_1.mp4"))
        assertEquals("archive.tar", Video.titleFrom(null, "archive.tar.mkv"))
        assertEquals(".hidden", Video.titleFrom(null, ".hidden"))
        assertEquals("noext", Video.titleFrom(null, "noext"))
        assertEquals(null, Video.titleFrom(null, null))
        assertEquals(null, Video.titleFrom("", "  "))
    }
}
