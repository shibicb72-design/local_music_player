package com.shibi.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.shibi.musicplayer.MainActivity
import com.shibi.musicplayer.R

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var audioRecordingCallback: AudioManager.AudioRecordingCallback? = null
    private val micAutoPauseLogic = MicAutoPauseLogic()
    private var micAutoPauseGuard = false

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .also { provider -> provider.setSmallIcon(R.drawable.ic_notification) },
        )

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setDeviceVolumeControlEnabled(true)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
            }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity)
            .setCallback(
                object : MediaSession.Callback {
                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                    ): MediaSession.ConnectionResult =
                        MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                            .setAvailableSessionCommands(
                                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                    .buildUpon()
                                    .add(OutputCommands.SET_OUTPUT)
                                    .build(),
                            )
                            .build()

                    override fun onCustomCommand(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        customCommand: SessionCommand,
                        args: Bundle,
                    ): ListenableFuture<SessionResult> {
                        if (customCommand.customAction == OutputCommands.ACTION_SET_OUTPUT) {
                            val id = args.getInt(OutputCommands.EXTRA_DEVICE_ID, -1)
                            val device = if (id == -1) {
                                null
                            } else {
                                getSystemService(AudioManager::class.java)
                                    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                                    .firstOrNull { it.id == id }
                            }
                            player.setPreferredAudioDevice(device)
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        return super.onCustomCommand(session, controller, customCommand, args)
                    }
                },
            )
            .build()

        player.addListener(
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!micAutoPauseGuard && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                        micAutoPauseLogic.onUserPlayPauseIntent()
                    }
                }
            },
        )

        val callback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                val activePlayer = mediaSession?.player ?: return
                when (micAutoPauseLogic.onRecordingChanged(configs.isNotEmpty(), activePlayer.isPlaying)) {
                    MicAutoPauseLogic.Action.PAUSE -> applyMicAutoPauseGuard { activePlayer.pause() }
                    MicAutoPauseLogic.Action.RESUME -> applyMicAutoPauseGuard { activePlayer.play() }
                    MicAutoPauseLogic.Action.NONE -> Unit
                }
            }
        }
        audioRecordingCallback = callback
        getSystemService(AudioManager::class.java)
            .registerAudioRecordingCallback(callback, Handler(Looper.getMainLooper()))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        audioRecordingCallback?.let { callback ->
            getSystemService(AudioManager::class.java).unregisterAudioRecordingCallback(callback)
        }
        audioRecordingCallback = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private inline fun applyMicAutoPauseGuard(action: () -> Unit) {
        micAutoPauseGuard = true
        try {
            action()
        } finally {
            micAutoPauseGuard = false
        }
    }

    private companion object {
        const val SESSION_ID = "shibi_music_player"
        const val CHANNEL_ID = "playback"
    }
}
