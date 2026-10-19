package com.shibi.musicplayer.domain

/**
 * One on-device video from MediaStore. Values MediaStore could not read (common for MKV/WMV)
 * are null, never 0, so [VideoFormat] can show a dash for them.
 */
data class Video(
    val id: Long,
    val title: String,
    val displayName: String,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val mimeType: String?,
    val dateAddedSeconds: Long,
    val folder: String?,
    val contentUri: String,
    /** MediaStore DATE_TAKEN (usually milliseconds), 0 when missing. */
    val dateTakenMs: Long = 0L,
) {
    companion object {
        /** MediaStore's placeholder for an unknown text column (`MediaStore.UNKNOWN_STRING`). */
        private const val UNKNOWN = "<unknown>"

        /**
         * The row title: MediaStore's TITLE, else the file name without its extension, else null
         * (the caller supplies the localized "Unknown title").
         */
        fun titleFrom(title: String?, displayName: String?): String? {
            val cleanTitle = title?.trim().orEmpty()
            if (cleanTitle.isNotEmpty() && cleanTitle != UNKNOWN) return cleanTitle
            val name = displayName?.trim().orEmpty()
            if (name.isEmpty()) return null
            val stem = name.substringBeforeLast('.', missingDelimiterValue = name)
            return stem.ifEmpty { name }
        }
    }
}
