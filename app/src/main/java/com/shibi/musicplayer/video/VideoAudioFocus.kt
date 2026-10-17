package com.shibi.musicplayer.video

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.audio.AudioFocusRequestCompat
import androidx.media3.common.audio.AudioManagerCompat
import androidx.media3.common.util.UnstableApi
import com.shibi.musicplayer.domain.VideoAudioEvent

/**
 * libVLC does not manage audio focus, so the video asks for it while it plays and listens
 * for headphones being unplugged. A seam, so view model tests run without an AudioManager.
 */
internal interface VideoAudioFocus {
    /** Requests focus and starts listening; true if focus was granted. Idempotent. */
    fun acquire(onEvent: (VideoAudioEvent) -> Unit): Boolean

    /** Abandons focus and stops listening. Idempotent. */
    fun release()
}

/** `AUDIOFOCUS_GAIN` with usage MEDIA and content MOVIE, plus `ACTION_AUDIO_BECOMING_NOISY`. */
@OptIn(UnstableApi::class)
internal class AndroidVideoAudioFocus(context: Context) : VideoAudioFocus {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequestCompat? = null
    private var noisyReceiver: BroadcastReceiver? = null

    override fun acquire(onEvent: (VideoAudioEvent) -> Unit): Boolean {
        val focus = request ?: AudioFocusRequestCompat.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change -> focusEvent(change)?.let(onEvent) }
            .build()
            .also { request = it }
        if (noisyReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                        onEvent(VideoAudioEvent.BecomingNoisy)
                    }
                }
            }
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyReceiver = receiver
        }
        val manager = audioManager ?: return false
        return AudioManagerCompat.requestAudioFocus(manager, focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun release() {
        val focus = request
        val manager = audioManager
        if (focus != null && manager != null) AudioManagerCompat.abandonAudioFocusRequest(manager, focus)
        request = null
        noisyReceiver?.let { receiver -> runCatching { appContext.unregisterReceiver(receiver) } }
        noisyReceiver = null
    }

    private fun focusEvent(change: Int): VideoAudioEvent? = when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> VideoAudioEvent.Gain
        AudioManager.AUDIOFOCUS_LOSS -> VideoAudioEvent.Loss
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> VideoAudioEvent.LossTransient
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> VideoAudioEvent.LossTransientCanDuck
        else -> null
    }
}
