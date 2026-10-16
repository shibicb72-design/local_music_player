package com.shibi.musicplayer.domain

object LibrarySearch {
    fun filter(tracks: List<Track>, query: String): List<Track> {
        val needle = query.trim()
        if (needle.isEmpty()) return tracks
        return tracks.filter { track ->
            TrackDisplay.title(track.title).contains(needle, ignoreCase = true) ||
                track.title.contains(needle, ignoreCase = true) ||
                (TrackDisplay.artist(track.artist)?.contains(needle, ignoreCase = true) == true)
        }
    }
}
