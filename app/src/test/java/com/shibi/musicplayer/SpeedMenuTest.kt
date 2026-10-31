package com.shibi.musicplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.PlaybackSpeed
import com.shibi.musicplayer.ui.SpeedButton
import com.shibi.musicplayer.ui.SpeedButtonTag
import com.shibi.musicplayer.ui.speedOptionTag
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SpeedMenuTest {

    @get:Rule
    val compose = createComposeRule()

    private val picked = mutableListOf<Float>()

    private fun setUp() {
        compose.setContent {
            MusicPlayerTheme {
                var speed by remember { mutableStateOf(1f) }
                SpeedButton(
                    speed = speed,
                    onSetSpeed = {
                        picked += it
                        speed = it
                    },
                )
            }
        }
    }

    @Test
    fun theMenuListsTheSixSpeedsWithTheCurrentOneChecked() {
        setUp()
        compose.onNodeWithTag(SpeedButtonTag).performClick()

        PlaybackSpeed.Options.forEach { option ->
            compose.onNodeWithTag(speedOptionTag(option)).assertTextEquals(PlaybackSpeed.label(option))
        }
        assertEquals(6, PlaybackSpeed.Options.size)
        val check = hasContentDescription("Current speed")
        compose.onAllNodes(check, useUnmergedTree = true).assertCountEquals(1)
        compose.onNode(check and hasAnyAncestor(hasTestTag(speedOptionTag(1f))), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun pickingASpeedReportsItAndRelabelsTheButton() {
        setUp()
        compose.onNodeWithTag(SpeedButtonTag).assertTextEquals("1×")
        compose.onNodeWithTag(SpeedButtonTag).performClick()
        compose.onNodeWithTag(speedOptionTag(1.5f)).performClick()

        assertEquals(listOf(1.5f), picked)
        compose.onNodeWithTag(SpeedButtonTag).assertTextEquals("1.5×")
    }
}
