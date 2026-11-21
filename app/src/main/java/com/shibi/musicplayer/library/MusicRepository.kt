package com.shibi.musicplayer.library

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.Track
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class MusicRepository(private val context: Context) {
    private val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    /** Emits whenever the audio collection changes (a delete, a download). Bursts are conflated. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(collection, true, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    fun loadTracks(): List<Track> {
        val hasGenre = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = listOfNotNull(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.GENRE.takeIf { hasGenre },
            // Before API 29 there is no RELATIVE_PATH: the folder is the parent of DATA.
            if (hasRelativePath) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA,
        ).toTypedArray()
        // All on-device music. A specific folder filter will be added later.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0" +
            " AND ${MediaStore.Audio.Media.MIME_TYPE} LIKE ?"
        val selectionArgs = arrayOf("audio/%")
        // The library order (Favorites, Search); the Songs list sorts its own copy (LibrarySorter).
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val tracks = mutableListOf<Track>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            // Only the sorts read these; a provider without them leaves the values unknown.
            val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val albumArtistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            val trackColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val fileNameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val genreColumn = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            val folderColumn = cursor.getColumnIndex(
                if (hasRelativePath) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA,
            )
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumId = cursor.getLong(albumIdColumn)
                // TRACK holds the disc as thousands: 2005 is disc 2, track 5.
                val trackTag = cursor.positiveLong(trackColumn)
                val folderTag = cursor.text(folderColumn)
                tracks += Track(
                    id = id,
                    title = cursor.getString(titleColumn).toDisplayName(R.string.unknown_title),
                    artist = cursor.getString(artistColumn).toArtistName(),
                    album = cursor.getString(albumColumn).toDisplayName(R.string.unknown_album),
                    durationMs = cursor.getLong(durationColumn).coerceAtLeast(0L),
                    contentUri = ContentUris.withAppendedId(collection, id).toString(),
                    albumArtUri = albumArtUri(albumId),
                    dateAddedSeconds = cursor.positiveLong(dateAddedColumn) ?: 0L,
                    sizeBytes = cursor.positiveLong(sizeColumn),
                    albumArtist = cursor.text(albumArtistColumn),
                    trackNumber = trackTag?.rem(1000L)?.toInt()?.takeIf { it > 0 },
                    discNumber = trackTag?.div(1000L)?.toInt()?.takeIf { it > 0 },
                    year = cursor.positiveLong(yearColumn)?.toInt(),
                    genre = cursor.text(genreColumn),
                    folder = if (hasRelativePath) folderTag?.trimEnd('/')?.ifEmpty { null } else folderTag?.let(::parentOfData),
                    fileName = cursor.text(fileNameColumn),
                )
            }
        }
        return tracks
    }

    private fun albumArtUri(albumId: Long): String? {
        if (albumId <= 0L) return null
        return ContentUris.withAppendedId(ALBUM_ART_URI, albumId).toString()
    }

    private fun String?.toDisplayName(fallbackRes: Int): String {
        val value = this?.trim().orEmpty()
        if (value.isEmpty() || value == MediaStore.UNKNOWN_STRING) {
            return context.getString(fallbackRes)
        }
        return value
    }

    private fun String?.toArtistName(): String = toDisplayName(R.string.unknown_artist)

    /** The column's value when it is there and positive; null for a missing column, NULL or 0. */
    private fun Cursor.positiveLong(column: Int): Long? {
        if (column < 0 || isNull(column)) return null
        return getLong(column).takeIf { it > 0L }
    }

    /** The text in [column] when it is there and not blank; null for a missing column, NULL or blank. */
    private fun Cursor.text(column: Int): String? {
        if (column < 0 || isNull(column)) return null
        return getString(column)?.trim()?.ifEmpty { null }
    }

    /** `/storage/emulated/0/Music/A/b.mp3` is `Music/A`; null for a file at the volume root. */
    private fun parentOfData(data: String): String? {
        val relative = data.replace(VOLUME_ROOT, "").trimStart('/')
        val slash = relative.lastIndexOf('/')
        return if (slash > 0) relative.substring(0, slash) else null
    }

    private companion object {
        val VOLUME_ROOT = Regex("^/(?:storage/(?:emulated/\\d+|[^/]+)|sdcard)/")
        val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
