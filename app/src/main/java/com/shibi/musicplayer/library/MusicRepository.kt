package com.shibi.musicplayer.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.Track

class MusicRepository(private val context: Context) {
    fun loadTracks(): List<Track> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
        )
        // All on-device music. A specific folder filter will be added later.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0" +
            " AND ${MediaStore.Audio.Media.MIME_TYPE} LIKE ?"
        val selectionArgs = arrayOf("audio/%")
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
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumId = cursor.getLong(albumIdColumn)
                tracks += Track(
                    id = id,
                    title = cursor.getString(titleColumn).toDisplayName(R.string.unknown_title),
                    artist = cursor.getString(artistColumn).toArtistName(),
                    album = cursor.getString(albumColumn).toDisplayName(R.string.unknown_album),
                    durationMs = cursor.getLong(durationColumn).coerceAtLeast(0L),
                    contentUri = ContentUris.withAppendedId(collection, id).toString(),
                    albumArtUri = albumArtUri(albumId),
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

    private companion object {
        val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
