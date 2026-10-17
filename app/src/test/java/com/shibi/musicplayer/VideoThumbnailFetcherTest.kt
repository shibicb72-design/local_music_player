package com.shibi.musicplayer

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.request.Options
import com.shibi.musicplayer.library.VideoThumbnail
import com.shibi.musicplayer.library.VideoThumbnailFetcher
import com.shibi.musicplayer.library.videoThumbnailRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VideoThumbnailFetcherTest {
    private val context = RuntimeEnvironment.getApplication()
    private val imageLoader = ImageLoader(context)
    private val factory = VideoThumbnailFetcher.Factory()

    @Test
    fun acceptsMediaStoreVideoUrisOnly() {
        assertTrue(VideoThumbnailFetcher.isMediaStoreVideo(Uri.parse("content://media/external/video/media/7")))
        assertTrue(VideoThumbnailFetcher.isMediaStoreVideo(Uri.parse("content://media/external_primary/video/media/7")))
        assertFalse(VideoThumbnailFetcher.isMediaStoreVideo(Uri.parse("content://media/external/audio/media/7")))
        assertFalse(VideoThumbnailFetcher.isMediaStoreVideo(Uri.parse("content://media/external/audio/albumart/3")))
        assertFalse(VideoThumbnailFetcher.isMediaStoreVideo(Uri.parse("content://downloads/video/7")))
        assertFalse(VideoThumbnailFetcher.isMediaStoreVideo(Uri.parse("file:///sdcard/Movies/a.mp4")))
    }

    @Test
    fun factoryDeclinesNonVideoUrisSoMusicArtworkIsNeverRoutedHere() {
        val audio = VideoThumbnail(Uri.parse("content://media/external/audio/media/7"))
        val video = VideoThumbnail(Uri.parse("content://media/external/video/media/7"))

        assertNull(factory.create(audio, Options(context), imageLoader))
        assertNotNull(factory.create(video, Options(context), imageLoader))
    }

    @Test
    fun fetchFailsWhenTheSystemHasNoThumbnailSoTheCallerCanFallBack() {
        val fetcher = factory.create(
            VideoThumbnail(Uri.parse("content://media/external/video/media/404")),
            Options(context),
            imageLoader,
        )!!

        val result = runCatching { runBlocking { fetcher.fetch() } }

        assertTrue(result.isFailure)
    }

    @Test
    fun requestCarriesTheThumbnailModelAndAStableMemoryKey() {
        val uri = "content://media/external/video/media/7"
        val request = videoThumbnailRequest(context, uri)

        assertEquals(VideoThumbnail(Uri.parse(uri)), request.data)
        assertEquals("video-thumbnail:$uri", request.memoryCacheKey)
    }
}
