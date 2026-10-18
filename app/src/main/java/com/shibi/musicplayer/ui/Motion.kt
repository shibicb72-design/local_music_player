package com.shibi.musicplayer.ui

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.shibi.musicplayer.domain.Bounds
import kotlin.math.roundToInt

/** True when the system "Remove animations" setting (animator scale 0) is on. */
@Composable
internal fun rememberReduceMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** About 300 ms, no bounce: fullscreen and pane motion. */
internal val VideoMotionSpec: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 380f)

/** The video box or pane gliding to a new height; snap with Remove animations on. */
@Composable
internal fun rememberGlide(target: Float): State<Float> =
    animateFloatAsState(
        targetValue = target,
        animationSpec = if (rememberReduceMotion()) snap() else VideoMotionSpec,
        label = "glide",
    )

/**
 * Sizes the element to [heightPx] every layout pass, so the glide value is read in the
 * layout phase and never in composition: the child is constrained to exactly that height.
 */
internal fun Modifier.animatedHeight(heightPx: () -> Float): Modifier = layout { m, c ->
    val h = heightPx().roundToInt().coerceAtLeast(0)
    val p = m.measure(c.copy(minHeight = h, maxHeight = h))
    layout(p.width, h) { p.place(0, 0) }
}

/** Where the player box (inline box or two-pane pane) is in the window; fullscreen grows from it and PiP uses it as the source hint. */
@Stable
internal class VideoBounds {
    var rect: Bounds? by mutableStateOf(null)
}

internal val LocalVideoBounds = staticCompositionLocalOf { VideoBounds() }

internal fun Modifier.reportVideoBounds(bounds: VideoBounds): Modifier = onGloballyPositioned { coords ->
    val r = coords.boundsInWindow()
    if (r.width > 0f && r.height > 0f) bounds.rect = Bounds(r.left, r.top, r.width, r.height)
}

/** Controls appear: a short fade with a small rise; nothing with Remove animations on. */
@Composable internal fun controlsEnter(): EnterTransition =
    if (rememberReduceMotion()) EnterTransition.None else fadeIn(tween(180)) + slideInVertically(tween(220)) { full -> full / 24 }

/** Controls leave: the reverse. */
@Composable internal fun controlsExit(): ExitTransition =
    if (rememberReduceMotion()) ExitTransition.None else fadeOut(tween(160)) + slideOutVertically(tween(200)) { full -> full / 24 }

/** Row highlight colour change; instant with Remove animations on. */
@Composable internal fun highlightSpec(): AnimationSpec<Color> =
    if (rememberReduceMotion()) snap() else tween(220)
