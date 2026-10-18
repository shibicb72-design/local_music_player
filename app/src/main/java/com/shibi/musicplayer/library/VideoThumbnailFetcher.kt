package com.shibi.musicplayer.library

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.graphics.scale
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Coil model for a video frame thumbnail; a distinct type so music artwork never reaches this fetcher. */
data class VideoThumbnail(val uri: Uri)

/**
 * Loads a small frame for a `content://media/.../video/...` URI: the system's cached thumbnail on
 * API 29+, a frame near 1 s from [MediaMetadataRetriever] on 26-28. Any failure (WMV the system
 * cannot decode, a deleted file) throws, so the request ends in an error and the row can draw
 * its own fallback tile.
 */
class VideoThumbnailFetcher(
    private val data: VideoThumbnail,
    private val options: Options,
) : Fetcher {
    @OptIn(ExperimentalCoilApi::class)
    override suspend fun fetch(): FetchResult {
        val bitmap = load(options.context, data.uri)
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<VideoThumbnail> {
        override fun create(data: VideoThumbnail, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!isMediaStoreVideo(data.uri)) return null
            return VideoThumbnailFetcher(data, options)
        }
    }

    companion object {
        private const val THUMB_WIDTH = 320
        private const val THUMB_HEIGHT = 180
        private const val FRAME_TIME_US = 1_000_000L

        /** The row's thumbnail, also the video notification's artwork (V8). Blocking; throws if there is none. */
        fun load(context: Context, uri: Uri): Bitmap =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(THUMB_WIDTH, THUMB_HEIGHT), null)
            } else {
                frameFromRetriever(context, uri)
            }

        private fun frameFromRetriever(context: Context, uri: Uri): Bitmap {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val frame = retriever.getFrameAtTime(FRAME_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: throw IOException("MediaMetadataRetriever returned no frame")
                return frame.coverScaled()
            } finally {
                retriever.release()
            }
        }

        /** Shrinks a full-size frame so it still covers 320×180; the row crops it. */
        private fun Bitmap.coverScaled(): Bitmap {
            val factor = max(THUMB_WIDTH.toFloat() / width, THUMB_HEIGHT.toFloat() / height)
            if (factor >= 1f) return this
            return scale((width * factor).roundToInt().coerceAtLeast(1), (height * factor).roundToInt().coerceAtLeast(1))
        }

        /** `content://media/<volume>/video/...`, the only URIs MediaStore can thumbnail as video. */
        fun isMediaStoreVideo(uri: Uri): Boolean {
            val segments = uri.pathSegments
            return uri.scheme == "content" &&
                uri.authority == MediaStore.AUTHORITY &&
                segments.size >= 2 &&
                segments[1] == "video"
        }
    }
}

/**
 * The request a video row should load (V3). The fetcher is attached per request, so the
 * app-wide image loader, and how music artwork loads, stay unchanged.
 */
fun videoThumbnailRequest(context: Context, contentUri: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(VideoThumbnail(contentUri.toUri()))
        .fetcherFactory(VideoThumbnailFetcher.Factory())
        .memoryCacheKey("video-thumbnail:$contentUri")
        .build()
