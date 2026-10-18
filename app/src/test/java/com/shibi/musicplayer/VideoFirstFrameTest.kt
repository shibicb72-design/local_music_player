package com.shibi.musicplayer

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.diagnostics.ErrorLogger
import com.shibi.musicplayer.domain.VideoAudioEvent
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.video.PlaybackStats
import com.shibi.musicplayer.video.VideoAudioFocus
import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoProgressHooks
import com.shibi.musicplayer.video.VideoViewModel
import java.time.Duration
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** V18: the Playback check line starts with the tap-to-first-frame time. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VideoFirstFrameTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val engines = mutableListOf<FakeVideoEngine>()
    private val logged = mutableListOf<String>()
    private var now = 10_000L
    private val viewModel = VideoViewModel(
        application = app,
        engineFactory = { FakeVideoEngine().also { engines += it } },
        audioFocus = NoFocus,
        progress = VideoProgressHooks.None,
        errorLogger = ErrorLogger { step, _, _, _ -> logged += step },
        clock = { now },
    )

    @Test
    fun theCheckLineCarriesTheTimeFromTapToFirstFrame() {
        viewModel.open(clip)
        val engine = engines.single()
        engine.stats = PlaybackStats(decodedVideo = 0, displayedPictures = 0, lostPictures = 0)
        now += 750L
        engine.emit(VideoEngineEvent.FirstFrame(firstInProcess = true))
        engine.emit(VideoEngineEvent.Playing)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertTrue(logged.toString(), logged.any { "first frame 750 ms (first video since the app started)" in it })
    }

    private object NoFocus : VideoAudioFocus {
        override fun acquire(onEvent: (VideoAudioEvent) -> Unit) = true
        override fun release() = Unit
    }

    private companion object {
        val clip = Video(
            id = 1L,
            title = "Clip",
            displayName = "Clip.mp4",
            durationMs = 754_000L,
            sizeBytes = 1_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            dateAddedSeconds = 0L,
            folder = null,
            contentUri = "content://media/external/video/media/1",
        )
    }
}
