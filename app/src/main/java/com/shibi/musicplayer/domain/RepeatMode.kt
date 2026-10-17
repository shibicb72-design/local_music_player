package com.shibi.musicplayer.domain

enum class RepeatMode {
    OFF,
    ONCE,
    THRICE,
    INFINITE,
    ;

    fun next(): RepeatMode = when (this) {
        OFF -> ONCE
        ONCE -> THRICE
        THRICE -> INFINITE
        INFINITE -> OFF
    }

    /** Extra plays after the in-progress one. Null means forever. */
    fun extraPlays(): Int? = when (this) {
        OFF -> 0
        ONCE -> 1
        THRICE -> 3
        INFINITE -> null
    }
}

data class RepeatState(
    val mode: RepeatMode = RepeatMode.OFF,
    val remainingExtras: Int = 0,
) {
    val repeatsCurrentItem: Boolean
        get() = mode == RepeatMode.INFINITE || remainingExtras > 0
}

/**
 * Counted repeats live here because Media3 only has off / one / all.
 * [onSameItemCompleted] is called on `MEDIA_ITEM_TRANSITION_REASON_REPEAT`.
 */
object RepeatCounter {
    fun start(mode: RepeatMode): RepeatState = RepeatState(
        mode = mode,
        remainingExtras = mode.extraPlays() ?: 0,
    )

    fun cycle(state: RepeatState): RepeatState = start(state.mode.next())

    fun onSameItemCompleted(state: RepeatState): RepeatState {
        if (state.mode == RepeatMode.INFINITE) return state
        if (state.mode == RepeatMode.OFF || state.remainingExtras <= 1) {
            return RepeatState()
        }
        return state.copy(remainingExtras = state.remainingExtras - 1)
    }

    fun onCurrentItemChanged(state: RepeatState): RepeatState {
        return when (state.mode) {
            RepeatMode.OFF, RepeatMode.INFINITE -> state
            RepeatMode.ONCE, RepeatMode.THRICE -> start(state.mode)
        }
    }
}
