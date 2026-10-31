package com.shibi.musicplayer.domain

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    val albumArtUri: String?,
    /** MediaStore DATE_ADDED in seconds, 0 when missing. */
    val dateAddedSeconds: Long = 0L,
    /** MediaStore SIZE in bytes, null when missing or 0. */
    val sizeBytes: Long? = null,
)
