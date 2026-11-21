package com.shibi.musicplayer.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
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

private const val FilterOutMillis = 80
private const val FilterInMillis = 120
private const val PageMillis = 220
private const val PageUnderOutMillis = 90
private val PageDistance = 24.dp

/** One held value: shared list states must never be attached to two filter bodies. */
@Stable
internal class FadeThroughState<T>(initial: T, initialKey: Any? = initial) {
    var value by mutableStateOf(initial)
        private set
    var shownKey by mutableStateOf(initialKey)
        private set
    // The key composition last asked for: it leads shownKey from the key change until the swap.
    private var targetKey by mutableStateOf(initialKey)
    private var fadingOut by mutableStateOf(false)
    val alpha = Animatable(1f)

    /** True from the frame the key changes, not only once the effect starts a frame later. */
    val outgoing: Boolean get() = fadingOut || shownKey != targetKey

    /** Composition only: same-key data shows at once, and a snap swaps on this same frame. */
    fun update(target: T, key: Any?, snap: Boolean) {
        targetKey = key
        if (snap || shownKey == key) {
            value = target
            shownKey = key
        }
    }

    suspend fun show(target: T, key: Any? = target, snap: Boolean = false) = showLatest(key, snap) { target }

    /** [latest] is read at the swap, so data that landed during the fade-out is not lost. */
    suspend fun showLatest(key: Any?, snap: Boolean = false, latest: () -> T) {
        targetKey = key
        if (snap) {
            value = latest()
            shownKey = key
            fadingOut = false
            alpha.snapTo(1f)
            return
        }
        if (shownKey != key) {
            fadingOut = true
            alpha.animateTo(0f, tween(FilterOutMillis))
            value = latest()
            shownKey = key
        } else {
            value = latest()
        }
        fadingOut = false
        if (alpha.value != 1f) alpha.animateTo(1f, tween(FilterInMillis))
    }
}

@Composable
internal fun <T> rememberFadeThrough(
    target: T,
    key: Any? = target,
    animate: Boolean = true,
): FadeThroughState<T> {
    val state = remember { FadeThroughState(target, key) }
    val reduced = rememberReduceMotion()
    val latest by rememberUpdatedState(target)
    // Capture eligibility at the key change, before this frame can first show the chips.
    val animateKey = remember(key) { animate }
    val snap = reduced || !animateKey
    // Like rememberUpdatedState: publish same-key data, and snaps, before the body reads them.
    state.update(target, key, snap)
    LaunchedEffect(key, reduced) {
        state.showLatest(key, snap) { latest }
    }
    return state
}

/** Outgoing content is invisible to accessibility and consumes input before any child. */
private fun Modifier.blockOutgoing(block: Boolean): Modifier = if (!block) this else {
    clearAndSetSemantics {}.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }
}

internal fun Modifier.fadeThrough(state: FadeThroughState<*>): Modifier =
    graphicsLayer { alpha = state.alpha.value }.blockOutgoing(state.outgoing)

@Composable
internal fun <T> FadeThrough(
    state: FadeThroughState<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    Box(modifier.fadeThrough(state)) {
        key(state.shownKey) { content(state.value) }
    }
}

/** Retains an outgoing page's scroll and season even after navigation clears its open key. */
internal data class PageMotionTarget(
    val key: String?,
    val depth: Int,
    val listState: LazyListState,
    val season: MutableIntState? = null,
)

@Composable
internal fun PageSlide(
    target: PageMotionTarget,
    modifier: Modifier = Modifier,
    content: @Composable (PageMotionTarget) -> Unit,
) {
    val reduced = rememberReduceMotion()
    val distance = with(LocalDensity.current) { PageDistance.roundToPx() }
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentKey = { it.depth to it.key },
        transitionSpec = {
            val deeper = targetState.depth >= initialState.depth
            val enter = when {
                reduced -> EnterTransition.None
                deeper -> fadeIn(tween(PageMillis)) +
                    slideInHorizontally(tween(PageMillis, easing = LinearOutSlowInEasing)) { distance }
                else -> fadeIn(tween(PageMillis))
            }
            val exit = when {
                reduced -> ExitTransition.None
                deeper -> fadeOut(tween(PageUnderOutMillis))
                else -> fadeOut(tween(PageMillis)) +
                    slideOutHorizontally(tween(PageMillis, easing = LinearOutSlowInEasing)) { distance }
            }
            (enter togetherWith exit) using null
        },
        label = "pageSlide",
    ) { shown ->
        Box(Modifier.blockOutgoing(transition.targetState == EnterExitState.PostExit)) {
            content(shown)
        }
    }
}
