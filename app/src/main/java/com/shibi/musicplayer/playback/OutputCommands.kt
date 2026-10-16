package com.shibi.musicplayer.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

object OutputCommands {
    const val ACTION_SET_OUTPUT = "com.shibi.musicplayer.SET_OUTPUT"
    const val EXTRA_DEVICE_ID = "device_id"
    val SET_OUTPUT = SessionCommand(ACTION_SET_OUTPUT, Bundle.EMPTY)
}
