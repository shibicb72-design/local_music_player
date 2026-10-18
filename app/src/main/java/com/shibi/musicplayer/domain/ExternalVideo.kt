package com.shibi.musicplayer.domain

/**
 * The outcome of reading an "open with" intent aimed at Theatrum. Other apps (Files,
 * My Files, Gallery) hand a video over through ACTION_VIEW, and this pure result says whether
 * the request is one Theatrum can play.
 */
sealed interface ExternalVideoRequest {
    /** The request points at a video Theatrum may play. */
    data class Accepted(val uri: String) : ExternalVideoRequest

    /**
     * The request is not playable. [reason] is a short English sentence for the error log;
     * it never contains the full address, so no private path is written to the log.
     */
    data class Rejected(val reason: String) : ExternalVideoRequest
}

/**
 * Pure parsing of an "open with" intent for Theatrum. Other apps (Files, My Files, Gallery)
 * open a video through ACTION_VIEW; only content and file addresses are played, and nothing
 * from the incoming intent is launched or forwarded (android-intent-security).
 */
object ExternalVideo {
    /** The action an "open with" request uses. */
    const val ACTION_VIEW = "android.intent.action.VIEW"

    private const val SCHEME_CONTENT = "content"
    private const val SCHEME_FILE = "file"
    private const val VIDEO_PREFIX = "video/"

    /**
     * Reads the action, data address and declared type of an incoming intent. Returns null
     * when the action is not ACTION_VIEW, meaning it is not an open-with request at all (a
     * normal launch or the playback notification action). Otherwise returns [Accepted] or a
     * [Rejected] reason. The address is only classified, never opened, launched or forwarded.
     */
    fun parse(action: String?, uri: String?, mimeType: String?): ExternalVideoRequest? {
        if (action != ACTION_VIEW) return null
        if (uri.isNullOrBlank()) return ExternalVideoRequest.Rejected("No file in the request")
        val scheme = uri.substringBefore(':', missingDelimiterValue = "").lowercase()
        if (scheme != SCHEME_CONTENT && scheme != SCHEME_FILE) {
            return ExternalVideoRequest.Rejected("Unsupported address type")
        }
        if (!mimeType.isNullOrBlank() && !mimeType.lowercase().startsWith(VIDEO_PREFIX)) {
            return ExternalVideoRequest.Rejected("Not a video file")
        }
        return ExternalVideoRequest.Accepted(uri)
    }

    /**
     * Builds a [Video] row for an accepted external address, so the existing library and player
     * code can open it like a MediaStore entry. The id is a stable negative value derived from
     * the address, so it never clashes with the positive MediaStore ids. MediaStore-only fields
     * (duration, dimensions, folder) are null and the date added is 0, because the intent does
     * not carry them.
     */
    fun videoFor(uri: String, displayName: String?, sizeBytes: Long?, mimeType: String?): Video {
        val name = displayName?.takeIf { it.isNotBlank() } ?: lastSegment(uri)
        val cleanName = name.ifBlank { "Video" }
        return Video(
            id = -((uri.hashCode().toLong() and 0x7fffffffL) + 1L),
            title = Video.titleFrom(null, cleanName) ?: "Video",
            displayName = cleanName,
            durationMs = null,
            sizeBytes = sizeBytes?.takeIf { it > 0L },
            width = null,
            height = null,
            mimeType = mimeType?.takeIf { it.isNotBlank() },
            dateAddedSeconds = 0L,
            folder = null,
            contentUri = uri,
        )
    }

    /** The file name part of a content or file address: after the last slash, before any query. */
    private fun lastSegment(uri: String): String =
        uri.substringAfterLast('/').substringBefore('?')
}
