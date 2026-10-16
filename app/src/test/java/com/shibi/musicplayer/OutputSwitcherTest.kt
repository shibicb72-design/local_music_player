package com.shibi.musicplayer

import com.shibi.musicplayer.playback.OutputSwitcher
import com.shibi.musicplayer.playback.OutputSwitcher.Strategy
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputSwitcherTest {
    @Test
    fun strategyFor_oldSdkUsesRouteChooserDialog() {
        assertEquals(Strategy.ROUTE_CHOOSER_DIALOG, OutputSwitcher.strategyFor(26))
        assertEquals(Strategy.ROUTE_CHOOSER_DIALOG, OutputSwitcher.strategyFor(33))
    }

    @Test
    fun strategyFor_newSdkUsesSystemOutputSwitcher() {
        assertEquals(Strategy.SYSTEM_OUTPUT_SWITCHER, OutputSwitcher.strategyFor(34))
        assertEquals(Strategy.SYSTEM_OUTPUT_SWITCHER, OutputSwitcher.strategyFor(36))
    }

    @Test
    fun strategyFor_switchesAtUpsideDownCake() {
        assertEquals(Strategy.ROUTE_CHOOSER_DIALOG, OutputSwitcher.strategyFor(33))
        assertEquals(Strategy.SYSTEM_OUTPUT_SWITCHER, OutputSwitcher.strategyFor(34))
    }
}
