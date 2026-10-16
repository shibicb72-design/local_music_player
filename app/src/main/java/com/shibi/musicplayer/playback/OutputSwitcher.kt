package com.shibi.musicplayer.playback

import android.content.Context
import android.os.Build
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.SystemOutputSwitcherDialogController
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter

object OutputSwitcher {
    enum class Strategy {
        SYSTEM_OUTPUT_SWITCHER,
        ROUTE_CHOOSER_DIALOG,
    }

    fun strategyFor(sdkInt: Int): Strategy =
        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Strategy.SYSTEM_OUTPUT_SWITCHER
        } else {
            Strategy.ROUTE_CHOOSER_DIALOG
        }

    fun show(context: Context) {
        when (strategyFor(Build.VERSION.SDK_INT)) {
            Strategy.SYSTEM_OUTPUT_SWITCHER -> {
                if (!SystemOutputSwitcherDialogController.showDialog(context)) {
                    showFallback(context)
                }
            }

            Strategy.ROUTE_CHOOSER_DIALOG -> showFallback(context)
        }
    }

    fun currentOutputName(context: Context): String? =
        MediaRouter.getInstance(context).selectedRoute?.name

    private fun showFallback(context: Context) {
        val selector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()
        MediaRouteChooserDialog(context).apply {
            routeSelector = selector
        }.show()
    }
}
