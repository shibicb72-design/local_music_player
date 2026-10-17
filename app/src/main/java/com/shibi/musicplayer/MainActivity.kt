package com.shibi.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.musicplayer.background.BackgroundViewModel
import com.shibi.musicplayer.playlist.PlaylistViewModel
import com.shibi.musicplayer.ui.MusicApp
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import com.shibi.musicplayer.ui.theme.backdropIsLight
import com.shibi.musicplayer.ui.theme.systemBarStyleFor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cold-start default (dark theme, Default backdrop); SystemBarIcons takes over from here.
        val style = systemBarStyleFor(lightBackdrop = false)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        setContent {
            val playlistViewModel: PlaylistViewModel = viewModel()
            val darkTheme = playlistViewModel.darkTheme.collectAsStateWithLifecycle()
            SystemBarIcons(darkTheme = darkTheme.value)
            MusicPlayerTheme(darkTheme = darkTheme.value) {
                MusicApp(playlistViewModel = playlistViewModel)
            }
        }
    }

    /**
     * Re-applies edge-to-edge whenever the painted backdrop flips between light
     * and dark, so status and navigation icons follow the app, not the system.
     */
    @Composable
    private fun SystemBarIcons(
        darkTheme: Boolean,
        backgroundViewModel: BackgroundViewModel = viewModel(),
    ) {
        val background by backgroundViewModel.selection.collectAsStateWithLifecycle()
        val customColors by backgroundViewModel.customColors.collectAsStateWithLifecycle()
        val lightBackdrop = backdropIsLight(darkTheme, background, customColors)
        // Fine-tune sliders emit colours every frame; touch the window only on a flip.
        val applied = remember { BooleanArray(1) { false } }
        SideEffect {
            if (applied[0] != lightBackdrop) {
                applied[0] = lightBackdrop
                val style = systemBarStyleFor(lightBackdrop)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
        }
    }
}
