package com.shibi.musicplayer.ui.background

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.isActive

internal const val StaticScenePhase = 0.18f
private const val SceneCycleMs = 9_000

@Composable
internal fun rememberBackgroundMotion(forceStatic: Boolean = false): Boolean {
    if (forceStatic || LocalInspectionMode.current) return false
    val context = LocalContext.current
    val durationScale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    if (durationScale <= 0f) return false
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by lifecycleOwner.lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.STARTED)
}

/**
 * One looping value in `0f..1f` for a whole backdrop. Pauses by snapping to
 * [StaticScenePhase] so screenshots and reduce-motion stay on a still frame.
 */
@Composable
internal fun rememberScenePhase(play: Boolean): Float {
    val phase = remember { Animatable(StaticScenePhase) }
    LaunchedEffect(play) {
        if (!play) {
            phase.snapTo(StaticScenePhase)
            return@LaunchedEffect
        }
        while (isActive) {
            phase.snapTo(0f)
            phase.animateTo(
                1f,
                tween(durationMillis = SceneCycleMs, easing = LinearEasing),
            )
        }
    }
    return phase.value
}
