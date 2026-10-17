package com.shibi.musicplayer

import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.SectionRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertNull(SectionRoutes.sectionFor(null))
        assertNull(SectionRoutes.sectionFor("unknown"))
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
