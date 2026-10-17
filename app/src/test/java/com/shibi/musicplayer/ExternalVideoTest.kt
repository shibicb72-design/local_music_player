package com.shibi.musicplayer

import com.shibi.musicplayer.domain.ExternalVideo
import com.shibi.musicplayer.domain.ExternalVideoRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalVideoTest {

    private fun rejected(reason: String) = ExternalVideoRequest.Rejected(reason)

    private fun accepted(uri: String) = ExternalVideoRequest.Accepted(uri)

    @Test
    fun aContentVideoAddressIsAccepted() {
        assertEquals(
            accepted("content://media/external/video/media/42"),
            ExternalVideo.parse(ExternalVideo.ACTION_VIEW, "content://media/external/video/media/42", "video/mp4"),
        )
    }

    @Test
    fun aFileAddressWithNoDeclaredTypeIsAccepted() {
        assertEquals(
            accepted("file:///sdcard/Movies/clip.mp4"),
            ExternalVideo.parse(ExternalVideo.ACTION_VIEW, "file:///sdcard/Movies/clip.mp4", null),
        )
    }

    @Test
    fun anUpperCaseSchemeIsAccepted() {
        assertEquals(
            accepted("CONTENT://media/external/video/media/7"),
            ExternalVideo.parse(ExternalVideo.ACTION_VIEW, "CONTENT://media/external/video/media/7", "video/mp4"),
        )
    }

    @Test
    fun anHttpAddressIsRejected() {
        assertEquals(
            rejected("Unsupported address type"),
            ExternalVideo.parse(ExternalVideo.ACTION_VIEW, "https://example.com/clip.mp4", "video/mp4"),
        )
    }

    @Test
    fun aNestedIntentAddressIsRejected() {
        assertEquals(
            rejected("Unsupported address type"),
            ExternalVideo.parse(ExternalVideo.ACTION_VIEW, "intent:#Intent;end", "video/mp4"),
        )
    }

    @Test
    fun aMissingAddressIsRejected() {
        assertEquals(
            rejected("No file in the request"),
            ExternalVideo.parse(ExternalVideo.ACTION_VIEW, null, "video/mp4"),
        )
        assertEquals(
            rejected("No file in the request"),
            ExternalVideo.parse(ExternalVideo.ACTION_VIEW, "", "video/mp4"),
        )
    }

    @Test
    fun anImageTypeIsRejected() {
        assertEquals(
            rejected("Not a video file"),
            ExternalVideo.parse(ExternalVideo.ACTION_VIEW, "content://media/external/images/media/1", "image/jpeg"),
        )
    }

    @Test
    fun aDifferentActionIsNotAnOpenWithRequest() {
        assertNull(ExternalVideo.parse("android.intent.action.MAIN", "content://media/external/video/media/1", "video/mp4"))
        assertNull(ExternalVideo.parse(null, "content://media/external/video/media/1", "video/mp4"))
    }

    @Test
    fun videoForKeepsTheSuppliedDisplayName() {
        val video = ExternalVideo.videoFor("content://media/external/video/media/42", "Holiday Clip", 1_024L, "video/mp4")
        assertEquals("Holiday Clip", video.displayName)
        assertEquals("Holiday Clip", video.title)
    }

    @Test
    fun videoForFallsBackToTheLastSegmentWithoutTheQuery() {
        val video = ExternalVideo.videoFor("content://x/y/clip.mkv?token=abc", null, null, null)
        assertEquals("clip.mkv", video.displayName)
        assertEquals("clip", video.title)
    }

    @Test
    fun videoForBuildsAStableNegativeId() {
        val first = ExternalVideo.videoFor("content://x/y/clip.mkv", "Clip", null, null)
        val second = ExternalVideo.videoFor("content://x/y/clip.mkv", "Other name", 10L, "video/mp4")
        assertTrue(first.id < 0L)
        assertEquals(first.id, second.id)
    }

    @Test
    fun videoForTreatsAZeroSizeAsUnknown() {
        val video = ExternalVideo.videoFor("content://x/y/clip.mkv", "Clip", 0L, null)
        assertNull(video.sizeBytes)
    }

    @Test
    fun videoForKeepsTheAddressAsTheContentUri() {
        val uri = "content://media/external/video/media/42"
        assertEquals(uri, ExternalVideo.videoFor(uri, null, null, null).contentUri)
    }
}
