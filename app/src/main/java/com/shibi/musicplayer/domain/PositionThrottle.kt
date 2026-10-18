package com.shibi.musicplayer.domain

/** The UI's clock: libVLC reports the position many times a second; the UI needs about 4 updates a second. */
object PositionThrottle {
    const val StepMs = 250L

    /** Publish when anything but the position changed, or the position moved at least [StepMs] either way (a seek or a restart jumps at once). */
    fun shouldPublish(publishedMs: Long, newMs: Long, otherFieldsChanged: Boolean): Boolean =
        otherFieldsChanged || kotlin.math.abs(newMs - publishedMs) >= StepMs
}
