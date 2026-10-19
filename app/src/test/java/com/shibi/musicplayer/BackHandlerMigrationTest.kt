package com.shibi.musicplayer

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.ui.AppBackHandler
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackHandlerMigrationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun enabledHandlerGetsBack() {
        val calls = mutableListOf<String>()
        compose.setContent { AppBackHandler(enabled = true) { calls += "a" } }
        compose.waitForIdle()
        pressBack()
        assertEquals(listOf("a"), calls)
    }

    @Test
    fun disabledHandlerDoesNotGetBack() {
        val calls = mutableListOf<String>()
        compose.setContent { AppBackHandler(enabled = false) { calls += "a" } }
        compose.waitForIdle()
        pressBack()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun newerNestedHandlerWins() {
        val calls = mutableListOf<String>()
        compose.setContent {
            AppBackHandler { calls += "outer" }
            AppBackHandler { calls += "inner" }
        }
        compose.waitForIdle()
        pressBack()
        assertEquals(listOf("inner"), calls)
    }
}
