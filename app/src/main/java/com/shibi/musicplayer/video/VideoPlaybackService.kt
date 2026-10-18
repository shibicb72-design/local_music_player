package com.shibi.musicplayer.video

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.shibi.musicplayer.MainActivity
import com.shibi.musicplayer.R
import com.shibi.musicplayer.diagnostics.ErrorLog
import com.shibi.musicplayer.diagnostics.ErrorLogger
import com.shibi.musicplayer.library.VideoThumbnailFetcher
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * How the video view model reaches its session. The app binds [VideoPlaybackService]
 * ([ServiceVideoSessionLink]); tests record the calls or use [None].
 */
interface VideoSessionLink {
    /** A video is loaded and not finished: start (or update) its session and notification. */
    fun show(snapshot: VideoSessionSnapshot, controls: VideoSessionControls, engine: VideoEngine)

    /** Closed, ended or failed: the session, its notification and the service go. Idempotent. */
    fun end()

    object None : VideoSessionLink {
        override fun show(snapshot: VideoSessionSnapshot, controls: VideoSessionControls, engine: VideoEngine) = Unit
        override fun end() = Unit
    }
}

/**
 * The video's foreground `mediaPlayback` service (V8). While a video is loaded it holds the
 * libVLC engine for the session: a [VlcSessionPlayer] in its own [MediaSession] (earbud and
 * watch buttons, Media3's notification and lock-screen controls with ±10 s), a partial wake
 * lock while it plays, and the foreground state that keeps the process alive when the
 * screen is locked, so the sound goes on. Music's `PlaybackService` is a separate service
 * and is not touched.
 *
 * Only [VideoViewModel] binds it, through a local binder (it is not exported, and it has no
 * `MediaSessionService` intent filter on purpose: Media3 picks the first service with that
 * filter for a session's media-button receiver, and that must stay the music service). The
 * service runs only while a video is loaded: closing the video, its end or a failure calls
 * [end], which releases the session, removes the notification and stops the service.
 */
@OptIn(UnstableApi::class)
class VideoPlaybackService : MediaSessionService() {

    /** Same process only; the service is not exported. */
    inner class LocalBinder : Binder() {
        val service: VideoPlaybackService get() = this@VideoPlaybackService
    }

    private val binder = LocalBinder()
    private var player: VlcSessionPlayer? = null
    private var session: MediaSession? = null
    private var engine: VideoEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var artwork: VideoArtworkLoader? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(ChannelId)
                .setChannelName(R.string.video_channel_name)
                // Music's notification has Media3's default id; two ids, two notifications.
                .setNotificationId(NotificationId)
                .build()
                .also { provider -> provider.setSmallIcon(R.drawable.ic_notification) },
        )
        setListener(
            object : MediaSessionService.Listener {
                override fun onForegroundServiceStartNotAllowedException() {
                    reportStartFailure(
                        this@VideoPlaybackService,
                        ErrorLog.get(this@VideoPlaybackService),
                        IllegalStateException("Android did not allow the video's foreground service to start"),
                    )
                }
            },
        )
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ActionLocalBind) binder else super.onBind(intent)

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Never come back by itself after the process dies: nothing would be loaded.
        if (session == null) stopSelf(startId)
        return START_NOT_STICKY
    }

    /** The view model's video; the first call builds the session. */
    fun show(snapshot: VideoSessionSnapshot, controls: VideoSessionControls, engine: VideoEngine) {
        this.engine = engine
        val sessionPlayer = player ?: VlcSessionPlayer(mainLooper, controls).also { built ->
            player = built
            val loader = VideoArtworkLoader(this).also { artwork = it }
            val created = MediaSession.Builder(this, built)
                .setId(SessionId)
                .setSessionActivity(openVideoIntent())
                .setBitmapLoader(loader)
                .setMediaButtonPreferences(seekButtons())
                .build()
            session = created
            addSession(created)
        }
        sessionPlayer.show(snapshot)
        // Not while buffering: a stalled open must not keep the CPU up.
        holdWakeLock(snapshot.status == VideoSessionStatus.Playing)
    }

    /** Closed, ended or failed: no session, no notification, no service. */
    fun end() {
        releaseSession()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Swiped out of Recents: the user left, so the video pauses; the activity's view model closes it next. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        engine?.pause()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseSession()
        super.onDestroy()
    }

    private fun releaseSession() {
        holdWakeLock(false)
        session?.let { current ->
            removeSession(current)
            current.release()
        }
        session = null
        player?.release()
        player = null
        artwork?.release()
        artwork = null
        engine = null
    }

    /** The CPU stays up for libVLC's decoder while it plays with the screen off. */
    private fun holdWakeLock(playing: Boolean) {
        if (!playing && wakeLock == null) return
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WakeLockTag)
            ?.apply { setReferenceCounted(false) }
            ?.also { wakeLock = it }
            ?: return
        if (playing) lock.acquire(WakeLockTimeoutMs) else if (lock.isHeld) lock.release()
    }

    /** The notification and the lock screen open the app where the video is. */
    private fun openVideoIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        OpenVideoRequestCode,
        Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ActionOpenVideo)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** ±10 s in the notification's back and forward slots (no previous or next for one video). */
    private fun seekButtons(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setDisplayName(getString(R.string.video_seek_back_10))
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setDisplayName(getString(R.string.video_seek_forward_10))
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
    )

    internal companion object {
        const val ActionLocalBind = "com.shibi.musicplayer.video.BIND_LOCAL"
        const val SessionId = "theatrum_video"
        const val ChannelId = "video_playback"
        const val NotificationId = 2_001
        const val OpenVideoRequestCode = 2
        const val WakeLockTag = "Theatrum:video"

        /** Re-armed on every play; long enough for any film, and never forever. */
        val WakeLockTimeoutMs: Long = TimeUnit.HOURS.toMillis(6)

        /** A short message, and the details in Settings > Debugging / Error log. */
        fun reportStartFailure(context: Context, errorLogger: ErrorLogger, error: Throwable) {
            val shown = context.getString(R.string.video_session_error)
            errorLogger.log(step = "Start the video's background playback", error = error, shown = shown, media = null)
            Toast.makeText(context.applicationContext, shown, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Binds [VideoPlaybackService] from the application context (so it outlives the activity's
 * stop) and hands it the video. Binding is asynchronous: the latest snapshot waits here
 * until the service connects. A failed bind logs once per video and the video plays on,
 * just without background audio or session controls.
 */
internal class ServiceVideoSessionLink(
    context: Context,
    private val errorLogger: ErrorLogger,
) : VideoSessionLink {

    private val app = context.applicationContext
    private var service: VideoPlaybackService? = null
    private var bound = false
    private var failed = false
    private var pending: Pending? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val connected = (binder as? VideoPlaybackService.LocalBinder)?.service ?: return
            service = connected
            pending?.let { latest -> connected.show(latest.snapshot, latest.controls, latest.engine) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    override fun show(snapshot: VideoSessionSnapshot, controls: VideoSessionControls, engine: VideoEngine) {
        pending = Pending(snapshot, controls, engine)
        service?.let { connected ->
            connected.show(snapshot, controls, engine)
            return
        }
        if (bound || failed) return
        val intent = Intent(app, VideoPlaybackService::class.java).setAction(VideoPlaybackService.ActionLocalBind)
        val error = try {
            bound = app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (bound) null else IllegalStateException("bindService returned false for VideoPlaybackService")
        } catch (security: SecurityException) {
            security
        }
        if (error != null) {
            failed = true
            VideoPlaybackService.reportStartFailure(app, errorLogger, error)
        }
    }

    override fun end() {
        pending = null
        failed = false
        service?.end()
        service = null
        if (bound) {
            runCatching { app.unbindService(connection) }
            bound = false
        }
    }

    private class Pending(
        val snapshot: VideoSessionSnapshot,
        val controls: VideoSessionControls,
        val engine: VideoEngine,
    )
}

/** The video's thumbnail as the session artwork (MediaStore's cached frame, as in the list). */
@OptIn(UnstableApi::class)
internal class VideoArtworkLoader(context: Context) : BitmapLoader {

    private val app = context.applicationContext
    private val executor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: throw IOException("Not an image")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = executor.submit<Bitmap> {
        VideoThumbnailFetcher.load(app, uri)
    }

    fun release() {
        executor.shutdown()
    }
}
