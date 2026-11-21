package com.shibi.musicplayer.domain

/** What a video is, as [VideoNameParser] reads it from its file and folder names. */
sealed interface VideoIdentity {
    data class Movie(val title: String, val year: Int?) : VideoIdentity

    /** [lastEpisode] is set only for a multi-episode file (`S02E10E11`), and is then above [episode]. */
    data class Episode(
        val show: String,
        val season: Int,
        val episode: Int,
        val lastEpisode: Int?,
        val episodeTitle: String?,
    ) : VideoIdentity {
        init {
            require(lastEpisode == null || lastEpisode > episode) {
                "lastEpisode $lastEpisode must be above episode $episode"
            }
        }
    }

    /** A camera, phone, screen or messaging-app clip. */
    data class Personal(val source: CameraSource) : VideoIdentity

    data class Other(val title: String) : VideoIdentity
}

/** Which kind of device or app named a [VideoIdentity.Personal] clip. */
enum class CameraSource {
    DJI,
    GOPRO,
    PIXEL,
    PHONE_TIMESTAMP,
    SCREEN_RECORDING,
    MESSAGING,
    OTHER_CAMERA,
}
