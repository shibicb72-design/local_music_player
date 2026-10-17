package com.shibi.musicplayer

import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.SectionRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionRoutesTest {

    /**
     * The carousel selection is re-derived from the current route. A section
     * without its own route (Devices used to be a sheet) was reset to the
     * route's section as soon as the sheet closed, so the ring spun back.
     */
    @Test
    fun everySectionRoundTripsThroughItsRoute() {
        NavSection.entries.forEach { section ->
            assertEquals(section, SectionRoutes.sectionFor(SectionRoutes.routeFor(section)))
        }
    }

    @Test
    fun devicesIsARealDestination() {
        assertEquals(SectionRoutes.Devices, SectionRoutes.routeFor(NavSection.Devices))
        assertEquals(NavSection.Devices, SectionRoutes.sectionFor(SectionRoutes.Devices))
    }

    @Test
    fun everySectionHasADistinctRoute() {
        val routes = NavSection.entries.map(SectionRoutes::routeFor)
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun childRoutesKeepTheirParentSection() {
        assertEquals(NavSection.Playlists, SectionRoutes.sectionFor(SectionRoutes.PlaylistDetail))
        assertEquals(NavSection.Settings, SectionRoutes.sectionFor(SectionRoutes.Backgrounds))
        assertEquals(NavSection.Settings, SectionRoutes.sectionFor(SectionRoutes.Licenses))
        assertNull(SectionRoutes.sectionFor(null))
        assertNull(SectionRoutes.sectionFor("unknown"))
    }

    @Test
    fun compactCarouselKeepsAllNineSections() {
        assertEquals(NavSection.entries, SectionRoutes.sectionsFor(expanded = false))
        assertEquals(9, SectionRoutes.sectionsFor(expanded = false).size)
    }

    /** Appended last, so at rest Videos is the left neighbour of Songs on both rings. */
    @Test
    fun videosOnBothRings() {
        val compact = SectionRoutes.sectionsFor(expanded = false)
        val expanded = SectionRoutes.sectionsFor(expanded = true)
        assertEquals(NavSection.Videos, compact.last())
        assertEquals(NavSection.Videos, expanded.last())
        assertEquals(NavSection.Songs, compact.first())
        assertEquals(8, expanded.size)
        assertEquals("videos", SectionRoutes.routeFor(NavSection.Videos))
        assertEquals(NavSection.Videos, SectionRoutes.sectionFor("videos"))
        assertEquals(NavSection.Videos, SectionRoutes.sectionFor("video/fullscreen"))
        assertEquals(
            NavSection.Videos,
            SectionRoutes.selectionFor(expanded = true, selected = NavSection.Videos, previous = NavSection.Songs),
        )
    }

    /** Two-pane already shows Now Playing on the right, so the ring drops it. */
    @Test
    fun expandedCarouselDropsNowPlayingOnly() {
        val expanded = SectionRoutes.sectionsFor(expanded = true)
        assertFalse(NavSection.NowPlaying in expanded)
        assertEquals(NavSection.entries.size - 1, expanded.size)
        assertEquals(NavSection.entries.filter { it != NavSection.NowPlaying }, expanded)
    }

    @Test
    fun unfoldingOnNowPlayingFallsBackToThePreviousSection() {
        assertEquals(
            NavSection.Favorites,
            SectionRoutes.selectionFor(
                expanded = true,
                selected = NavSection.NowPlaying,
                previous = NavSection.Favorites,
            ),
        )
    }

    @Test
    fun unfoldingOnNowPlayingWithoutAPreviousSectionFallsBackToSongs() {
        assertEquals(
            NavSection.Songs,
            SectionRoutes.selectionFor(expanded = true, selected = NavSection.NowPlaying, previous = null),
        )
        assertEquals(
            NavSection.Songs,
            SectionRoutes.selectionFor(
                expanded = true,
                selected = NavSection.NowPlaying,
                previous = NavSection.NowPlaying,
            ),
        )
    }

    @Test
    fun unfoldingKeepsAnySectionThatIsStillOnTheRing() {
        NavSection.entries.filter { it != NavSection.NowPlaying }.forEach { section ->
            assertEquals(
                section,
                SectionRoutes.selectionFor(expanded = true, selected = section, previous = NavSection.Songs),
            )
        }
    }

    @Test
    fun foldingRestoresNowPlayingWithoutMovingTheSelection() {
        assertTrue(NavSection.NowPlaying in SectionRoutes.sectionsFor(expanded = false))
        NavSection.entries.forEach { section ->
            assertEquals(
                section,
                SectionRoutes.selectionFor(expanded = false, selected = section, previous = NavSection.Recent),
            )
        }
    }

    @Test
    fun onlyThePhoneMeansTheEmptyState() {
        val phone = com.shibi.musicplayer.domain.AudioOutput(
            id = 1,
            name = "This phone",
            kind = com.shibi.musicplayer.domain.OutputKind.PHONE_SPEAKER,
        )
        val buds = phone.copy(id = 2, name = "Buds", kind = com.shibi.musicplayer.domain.OutputKind.BLUETOOTH)
        assertEquals(true, com.shibi.musicplayer.domain.AudioOutputs.onlyThisPhone(emptyList()))
        assertEquals(true, com.shibi.musicplayer.domain.AudioOutputs.onlyThisPhone(listOf(phone)))
        assertEquals(false, com.shibi.musicplayer.domain.AudioOutputs.onlyThisPhone(listOf(phone, buds)))
    }
}
