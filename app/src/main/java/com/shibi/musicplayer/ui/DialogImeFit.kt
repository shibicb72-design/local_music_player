package com.shibi.musicplayer.ui

import android.os.Build
import android.view.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Makes the enclosing dialog window fit above the keyboard (the non-deprecated form of
 * adjustResize): the centered dialog re-centers in the space the IME leaves free.
 * Call it inside the dialog content. No-op below API 30 and outside a dialog.
 */
@Composable
internal fun FitDialogWindowAboveIme() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val attributes = window.attributes
            val wanted = attributes.fitInsetsTypes or WindowInsets.Type.ime()
            if (wanted != attributes.fitInsetsTypes) {
                window.attributes = attributes.apply { fitInsetsTypes = wanted }
            }
        }
    }
}
