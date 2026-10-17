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
    const val Licenses = "settings/licenses"

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

    /**
     * Sections on the carousel. Two-pane already shows Now Playing in the right
     * pane, so the expanded ring leaves it out; compact keeps all eight.
     */
    fun sectionsFor(expanded: Boolean): List<NavSection> =
        if (expanded) ExpandedSections else NavSection.entries

    /**
     * The selection to show for [expanded]. A section that is still on the ring
     * stays; otherwise (Now Playing at the moment of unfolding) it falls back to
     * [previous] when that is on the ring, else Songs.
     */
    fun selectionFor(
        expanded: Boolean,
        selected: NavSection,
        previous: NavSection?,
    ): NavSection {
        val sections = sectionsFor(expanded)
        if (selected in sections) return selected
        return previous?.takeIf { it in sections } ?: NavSection.Songs
    }

    private val ExpandedSections = NavSection.entries.filter { it != NavSection.NowPlaying }

    fun sectionFor(route: String?): NavSection? = when (route) {
        Songs -> NavSection.Songs
        Playlists, PlaylistDetail -> NavSection.Playlists
        Player -> NavSection.NowPlaying
        Devices -> NavSection.Devices
        Favorites -> NavSection.Favorites
        Recent -> NavSection.Recent
        Search -> NavSection.Search
        Settings, Backgrounds, Licenses -> NavSection.Settings
        else -> null
    }
}
