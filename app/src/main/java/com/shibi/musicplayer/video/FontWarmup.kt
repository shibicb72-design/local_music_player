package com.shibi.musicplayer.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Plays a 2 KB clip with a subtitle once after install so libVLC's fontconfig writes its cache
 * before the first real video. Never throws.
 */
internal object FontWarmup {

    private const val AssetName = "font_warmup.mp4"
    private const val StartDelayMs = 1_500L
    private const val Side = 64
    private const val TimeoutSeconds = 30L
    private val started = AtomicBoolean(false)

    fun startIfNeeded(context: Context) {
        if (FontConfigSetup.isRobolectric()) return
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        if (FontConfigSetup.cacheReady(appContext)) return
        Thread(
            { runCatching { warmUp(appContext) } },
            "font-warmup",
        ).apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY + 1
        }.start()
    }

    private fun warmUp(context: Context) {
        Thread.sleep(StartDelayMs)
        val startedAt = SystemClock.elapsedRealtime()
        FontConfigSetup.prepare(context)
        val file = File(context.cacheDir, "font_warmup.mp4")
        if (!file.isFile) {
            context.assets.open(AssetName).use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        val vlc = LibVLC(context, VlcVideoEngine.libVlcOptions(SubtitleTextSize.Default))
        val player = MediaPlayer(vlc)
        val videoTexture = SurfaceTexture(false)
        val subtitleTexture = SurfaceTexture(false)
        val videoSurface = Surface(videoTexture)
        val subtitleSurface = Surface(subtitleTexture)
        try {
            subtitleTexture.setDefaultBufferSize(Side, Side)
            val latch = CountDownLatch(1)
            player.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Vout -> if (event.voutCount > 0) latch.countDown()
                    MediaPlayer.Event.EndReached,
                    MediaPlayer.Event.EncounteredError,
                    MediaPlayer.Event.Stopped,
                    -> latch.countDown()
                }
            }
            val vout = player.vlcVout
            vout.setWindowSize(Side, Side)
            VoutAttach.attachRawSurfaces(vout, videoSurface, subtitleSurface, null)
            val media = Media(vlc, file.absolutePath)
            media.addOption(":no-audio")
            player.media = media
            media.release()
            player.play()
            latch.await(TimeoutSeconds, TimeUnit.SECONDS)
        } finally {
            runCatching { player.setEventListener(null) }
            runCatching { player.stop() }
            runCatching { player.vlcVout.detachViews() }
            runCatching { player.release() }
            runCatching { vlc.release() }
            runCatching { videoSurface.release() }
            runCatching { subtitleSurface.release() }
            runCatching { videoTexture.release() }
            runCatching { subtitleTexture.release() }
            val ms = SystemClock.elapsedRealtime() - startedAt
            Log.i("Theatrum", "font warm-up $ms ms, cache ready=${FontConfigSetup.cacheReady(context)}")
        }
    }
}
