package com.shibi.musicplayer.ui

/**
 * One route per carousel section. The selection is re-derived from the route
 * whenever it changes, so every section must own a destination; otherwise
 * the route sync drags the ring back to the previous section.
 */
internal object SectionRoutes {
    const val Songs = "songs"
    const val Playlists = "playlists"
    const val Player = "player"
    const val PlaylistDetail = "playlist/{playlistId}"
    const val Devices = "devices"
    const val Favorites = "favorites"
    const val Recent = "recent"
    const val Search = "search"
    const val Settings = "settings"
    const val Backgrounds = "settings/backgrounds"

    fun routeFor(section: NavSection): String = when (section) {
        NavSection.Songs -> Songs
        NavSection.Playlists -> Playlists
        NavSection.NowPlaying -> Player
        NavSection.Devices -> Devices
        NavSection.Favorites -> Favorites
        NavSection.Recent -> Recent
        NavSection.Search -> Search
        NavSection.Settings -> Settings
    }

    fun sectionFor(route: String?): NavSection? = when (route) {
        Songs -> NavSection.Songs
        Playlists, PlaylistDetail -> NavSection.Playlists
        Player -> NavSection.NowPlaying
        Devices -> NavSection.Devices
        Favorites -> NavSection.Favorites
        Recent -> NavSection.Recent
        Search -> NavSection.Search
        Settings, Backgrounds -> NavSection.Settings
        else -> null
    }
}
