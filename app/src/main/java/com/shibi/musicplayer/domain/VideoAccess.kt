package com.shibi.musicplayer.domain

/**
 * How much of the video library the app may read. Re-check on every resume, never cache:
 * on API 34+ the user can switch between "all" and "selected" in system settings.
 */
enum class VideoAccess {
    Full,

    /** API 34+: only the videos the user picked (READ_MEDIA_VISUAL_USER_SELECTED). */
    Partial,
    Denied,
    ;

    companion object {
        const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
        const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"

        private const val API_TIRAMISU = 33
        private const val API_UPSIDE_DOWN_CAKE = 34

        fun classify(sdkInt: Int, granted: Set<String>): VideoAccess = when {
            sdkInt >= API_UPSIDE_DOWN_CAKE -> when {
                READ_MEDIA_VIDEO in granted -> Full
                READ_MEDIA_VISUAL_USER_SELECTED in granted -> Partial
                else -> Denied
            }
            sdkInt >= API_TIRAMISU -> if (READ_MEDIA_VIDEO in granted) Full else Denied
            else -> if (READ_EXTERNAL_STORAGE in granted) Full else Denied
        }

        /** The permissions to ask for together in one dialog; also the set [classify] reads. */
        fun permissionsToRequest(sdkInt: Int): List<String> = when {
            sdkInt >= API_UPSIDE_DOWN_CAKE -> listOf(READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)
            sdkInt >= API_TIRAMISU -> listOf(READ_MEDIA_VIDEO)
            else -> listOf(READ_EXTERNAL_STORAGE)
        }
    }
}
