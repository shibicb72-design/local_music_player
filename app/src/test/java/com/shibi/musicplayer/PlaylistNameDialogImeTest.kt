package com.shibi.musicplayer

import android.view.WindowInsets
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.ui.PlaylistNameDialog
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/** The name dialog's own window fits above the keyboard, so OK and Cancel are not covered. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlaylistNameDialogImeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dialogWindowFitsImeInsets() {
        compose.setContent {
            PlaylistNameDialog(
                title = "New playlist",
                confirmLabel = "Create",
                initialName = "",
                onConfirm = {},
                onDismiss = {},
            )
        }
        compose.waitForIdle()
        val window = ShadowDialog.getLatestDialog()?.window
        assertNotNull(window)
        assertTrue((window!!.attributes.fitInsetsTypes and WindowInsets.Type.ime()) != 0)
    }
}
