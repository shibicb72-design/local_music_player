package com.shibi.musicplayer

import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.ui.videoSubtitle
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSubtitleTest {

    private val base = Video(
        id = 1L,
        title = "Beach day",
        displayName = "Beach day.mp4",
        durationMs = 754_000L,
        sizeBytes = 1_412_000_000L,
        width = 1920,
        height = 1080,
        mimeType = "video/mp4",
        dateAddedSeconds = 0L,
        folder = "Camera",
        contentUri = "content://media/external/video/media/1",
    )

    @Test
    fun sizeQualityAndFolderInThatOrder() {
        assertEquals("1.4 GB · 1080p · Camera", videoSubtitle(base))
    }

    @Test
    fun missingValuesAreLeftOutNotDashed() {
        assertEquals("1080p · Camera", videoSubtitle(base.copy(sizeBytes = null)))
        assertEquals("1.4 GB · Camera", videoSubtitle(base.copy(width = null, height = 0)))
        assertEquals("1.4 GB · 1080p", videoSubtitle(base.copy(folder = null)))
        assertEquals("", videoSubtitle(base.copy(sizeBytes = 0L, width = null, folder = null)))
    }
}
