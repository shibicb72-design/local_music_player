package com.shibi.musicplayer.domain

/** Which third of the screen a tap landed in. */
enum class TapSide { Back, Forward, Center }

fun sideOf(x: Float, width: Float): TapSide {
    if (width <= 0f) return TapSide.Center
    return when {
        x < width / 3f -> TapSide.Back
        x > width * 2f / 3f -> TapSide.Forward
        else -> TapSide.Center
    }
}

/**
 * [side] and [pendingAtMs]: a side tap waiting to see whether a second one follows.
 * [totalMs] and [lastTapAtMs]: the running chain of skips on [side].
 */
data class DoubleTapSkipState(
    val side: TapSide? = null,
    val totalMs: Long = 0L,
    val lastTapAtMs: Long = 0L,
    val pendingAtMs: Long? = null,
)

sealed interface TapAction {
    /** Show or hide the controls. */
    data object ToggleControls : TapAction

    /** A side tap that may still become a double tap; nothing to do yet. */
    data object Pending : TapAction

    /** Skip by [stepMs] (negative = back); [totalMs] is the chain total for the label. */
    data class Skip(val stepMs: Long, val totalMs: Long) : TapAction
}

private const val StepMs = FullscreenGestures.DoubleTapSeekMs

/** How long after the last skip another tap on the same side still adds a step. */
const val ChainWindowMs = 700L

private fun stepFor(side: TapSide): Long = if (side == TapSide.Back) -StepMs else StepMs

fun onTap(
    state: DoubleTapSkipState,
    side: TapSide,
    nowMs: Long,
    doubleTapTimeoutMs: Long = 300L,
    chainWindowMs: Long = ChainWindowMs,
): Pair<DoubleTapSkipState, TapAction> {
    if (side == TapSide.Center) {
        return DoubleTapSkipState() to TapAction.ToggleControls
    }
    val chainActive = state.side == side && state.totalMs > 0L && nowMs - state.lastTapAtMs <= chainWindowMs
    if (chainActive) {
        val total = state.totalMs + StepMs
        return DoubleTapSkipState(side = side, totalMs = total, lastTapAtMs = nowMs) to
            TapAction.Skip(stepFor(side), total)
    }
    val pending = state.pendingAtMs
    if (pending != null && state.side == side && nowMs - pending <= doubleTapTimeoutMs) {
        return DoubleTapSkipState(side = side, totalMs = StepMs, lastTapAtMs = nowMs) to
            TapAction.Skip(stepFor(side), StepMs)
    }
    return DoubleTapSkipState(side = side, pendingAtMs = nowMs) to TapAction.Pending
}

/**
 * Called from a timer. A pending tap older than [doubleTapTimeoutMs] was a single tap on a
 * side, so the controls toggle; a chain older than [chainWindowMs] ends. Null action: nothing due.
 */
fun onTimeout(
    state: DoubleTapSkipState,
    nowMs: Long,
    doubleTapTimeoutMs: Long = 300L,
    chainWindowMs: Long = ChainWindowMs,
): Pair<DoubleTapSkipState, TapAction?> {
    val pending = state.pendingAtMs
    if (pending != null) {
        return if (nowMs - pending >= doubleTapTimeoutMs) {
            DoubleTapSkipState() to TapAction.ToggleControls
        } else {
            state to null
        }
    }
    if (state.totalMs > 0L && nowMs - state.lastTapAtMs > chainWindowMs) {
        return DoubleTapSkipState() to null
    }
    return state to null
}
