package com.shibi.musicplayer.library

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.Video
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class VideoRepository(private val context: Context) {
    val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    fun loadVideos(): List<Video> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_TAKEN,
        )
        // Newest first; the view model's in-memory sort (VideoTimeline) is the source of truth.
        val sortOrder = "${MediaStore.Video.Media.DATE_TAKEN} DESC, ${MediaStore.Video.Media.DATE_ADDED} DESC"

        val videos = mutableListOf<Video>()
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val folderColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val takenColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
            val unknownTitle = context.getString(R.string.unknown_title)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(nameColumn)?.trim().orEmpty()
                videos += Video(
                    id = id,
                    title = Video.titleFrom(cursor.getString(titleColumn), displayName) ?: unknownTitle,
                    displayName = displayName,
                    durationMs = cursor.positiveLong(durationColumn),
                    sizeBytes = cursor.positiveLong(sizeColumn),
                    width = cursor.positiveLong(widthColumn)?.toInt(),
                    height = cursor.positiveLong(heightColumn)?.toInt(),
                    mimeType = cursor.textOrNull(mimeColumn),
                    dateAddedSeconds = cursor.positiveLong(dateColumn) ?: 0L,
                    folder = cursor.textOrNull(folderColumn),
                    contentUri = ContentUris.withAppendedId(collection, id).toString(),
                    dateTakenMs = if (takenColumn >= 0) cursor.positiveLong(takenColumn) ?: 0L else 0L,
                )
            }
        }
        return videos
    }

    /**
     * Emits whenever anything under the video collection changes (a finished download, a delete,
     * the user picking more videos). Bursts are conflated; the collector should debounce.
     */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(collection, true, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    private fun Cursor.positiveLong(column: Int): Long? {
        if (isNull(column)) return null
        return getLong(column).takeIf { it > 0L }
    }

    private fun Cursor.textOrNull(column: Int): String? {
        val value = getString(column)?.trim().orEmpty()
        return value.takeUnless { it.isEmpty() || it == MediaStore.UNKNOWN_STRING }
    }
}
