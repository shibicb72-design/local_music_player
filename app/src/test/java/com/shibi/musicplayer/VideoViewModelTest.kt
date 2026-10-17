package com.shibi.musicplayer

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.AspectMode
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAudioEvent
import com.shibi.musicplayer.ui.messageRes
import com.shibi.musicplayer.video.InMemoryVideoPrefs
import com.shibi.musicplayer.video.VideoAudioFocus
import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoError
import com.shibi.musicplayer.video.VideoProgressHooks
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VideoViewModelTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val engines = mutableListOf<FakeVideoEngine>()
    private val focus = FakeFocus()
    private val progress = RecordingProgress()
    private val viewModel = VideoViewModel(
        application = app,
        engineFactory = { FakeVideoEngine().also { engines += it } },
        audioFocus = focus,
        progress = progress,
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun noEngineUntilTheFirstVideo() {
        assertTrue(engines.isEmpty())
        assertNull(viewModel.engine.value)
        assertFalse(viewModel.uiState.value.isActive)
        // Pausing or closing with nothing open is harmless and still creates nothing.
        viewModel.pause()
        viewModel.close()
        assertTrue(engines.isEmpty())
        assertEquals(0, focus.acquired)
    }

    @Test
    fun appConstructorCreatesNoNativeEngine() {
        // The real constructor wires libVLC lazily; building it must not load anything.
        val real = VideoViewModel(app)
        assertNull(real.engine.value)
        assertFalse(real.uiState.value.isActive)
    }

    @Test
    fun fullscreenAppliesTheSavedAspectAndLeavingItFitsAgain() {
        val prefs = InMemoryVideoPrefs(aspect = AspectMode.Fill)
        val vm = withPrefs(prefs)
        idle()
        vm.open(beach)
        val engine = engines.single()
        assertFalse("the box keeps the engine's fit", engine.calls.any { it.startsWith("aspect:") })
        vm.setFullscreen(true)
        assertEquals("aspect:Fill", engine.calls.last())
        vm.setFullscreen(false)
        assertEquals("aspect:Fit", engine.calls.last())
    }

    @Test
    fun theAspectButtonCyclesAndSavesTheMode() {
        val prefs = InMemoryVideoPrefs(aspect = AspectMode.Original)
        val vm = withPrefs(prefs)
        idle()
        vm.open(beach)
        vm.setFullscreen(true)
        assertEquals(AspectMode.Fit, vm.cycleAspectMode())
        idle()
        assertEquals(AspectMode.Fit, prefs.aspectMode.value)
        assertEquals(AspectMode.Fill, vm.cycleAspectMode())
        idle()
        assertEquals(AspectMode.Fill, prefs.aspectMode.value)
        assertEquals(AspectMode.Fill, vm.aspectMode.value)
        assertEquals("aspect:Fill", engines.single().calls.last())
    }

    @Test
    fun theBrightnessSwipeIsOnByDefaultAndSaved() {
        val prefs = InMemoryVideoPrefs()
        val vm = withPrefs(prefs)
        idle()
        assertTrue(vm.brightnessSwipe.value)
        vm.setBrightnessSwipe(false)
        idle()
        assertFalse(prefs.brightnessSwipe.value)
        assertFalse(vm.brightnessSwipe.value)
    }

    private fun withPrefs(prefs: InMemoryVideoPrefs) = VideoViewModel(
        application = app,
        engineFactory = { FakeVideoEngine().also { engines += it } },
        audioFocus = focus,
        progress = progress,
        prefs = prefs,
    )

    @Test
    fun openLoadsAndTracksTheEngineState() {
        viewModel.open(beach)
        idle()
        val engine = engines.single()
        assertSame(engine, viewModel.engine.value)
        assertEquals(listOf("load:${beach.contentUri}"), engine.calls)
        assertEquals(1, focus.acquired)
        with(viewModel.uiState.value) {
            assertSame(beach, video)
            assertTrue(isActive)
            assertEquals(VideoStatus.Buffering, status)
            // MediaStore's size and duration show before the engine knows them.
            assertEquals(16f / 9f, aspect!!, 0.0001f)
            assertEquals(754_000L, durationMs)
        }

        engine.emit(VideoEngineEvent.Length(750_000L))
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Time(12_000L))
        engine.emit(VideoEngineEvent.Size(1440, 1080, 4f / 3f))
        idle()
        with(viewModel.uiState.value) {
            assertEquals(VideoStatus.Playing, status)
            assertTrue(isPlaying)
            assertEquals(12_000L, positionMs)
            assertEquals(750_000L, durationMs)
            assertEquals(4f / 3f, aspect!!, 0.0001f)
        }

        engine.emit(VideoEngineEvent.Paused)
        idle()
        assertEquals(VideoStatus.Paused, viewModel.uiState.value.status)
    }

    @Test
    fun toggleSeekAndPauseReachTheEngine() {
        viewModel.open(beach)
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        idle()
        viewModel.togglePlayPause()
        viewModel.seekTo(30_000L)
        engine.emit(VideoEngineEvent.Paused)
        idle()
        viewModel.togglePlayPause()
        assertEquals(listOf("load:${beach.contentUri}", "pause", "seek:30000", "play"), engine.calls)
        assertEquals(30_000L, viewModel.uiState.value.positionMs)
        // Pausing is a save point for V9.
        assertEquals(listOf("checkpoint:1@0"), progress.events)
    }

    @Test
    fun theSameVideoAgainResumesInsteadOfReloading() {
        viewModel.open(beach)
        engines.single().emit(VideoEngineEvent.Paused)
        idle()
        viewModel.open(beach)
        assertEquals(listOf("load:${beach.contentUri}", "play"), engines.single().calls)
    }

    @Test
    fun anotherVideoReusesTheEngineAndSavesTheFirst() {
        viewModel.open(beach)
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Time(5_000L))
        idle()
        viewModel.open(portrait)
        idle()
        assertEquals(1, engines.size)
        assertEquals("load:${portrait.contentUri}", engine.calls.last())
        assertSame(portrait, viewModel.uiState.value.video)
        assertEquals(listOf("checkpoint:1@5000"), progress.events)
        // Portrait MediaStore size until the engine reports.
        assertEquals(9f / 16f, viewModel.uiState.value.aspect!!, 0.0001f)
    }

    @Test
    fun closeReleasesTheEngineAndFocus() {
        viewModel.open(beach)
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        idle()
        viewModel.close()
        idle()
        assertTrue(engine.released)
        assertNull(viewModel.engine.value)
        assertFalse(viewModel.uiState.value.isActive)
        assertEquals(1, focus.released)
        assertEquals(listOf("checkpoint:1@0"), progress.events)

        // Late engine events after close never bring the box back.
        engine.emit(VideoEngineEvent.Playing)
        idle()
        assertFalse(viewModel.uiState.value.isActive)

        // The next video gets a fresh engine.
        viewModel.open(portrait)
        assertEquals(2, engines.size)
    }

    @Test
    fun errorStateShowsAReadableError() {
        viewModel.open(beach)
        engines.single().emit(VideoEngineEvent.Failed(VideoError.Playback))
        idle()
        val state = viewModel.uiState.value
        assertEquals(VideoStatus.Error, state.status)
        assertTrue(state.isActive)
        assertNotNull(state.error)
        val error = state.error!!
        assertEquals("This video can't be played.", app.getString(error.messageRes()))
        assertEquals(
            "Couldn't open this video. It may have been moved or deleted.",
            app.getString(VideoError.CannotOpen.messageRes()),
        )
        // A failed video is not saved, and tapping it again reloads it.
        viewModel.close()
        assertTrue(progress.events.isEmpty())
    }

    @Test
    fun aFailedVideoTappedAgainReloads() {
        viewModel.open(beach)
        engines.single().emit(VideoEngineEvent.Failed(VideoError.CannotOpen))
        idle()
        viewModel.open(beach)
        assertEquals(2, engines.single().calls.count { it.startsWith("load:") })
    }

    @Test
    fun endKeepsTheBoxReportsFinishedAndDropsFocus() {
        viewModel.open(beach)
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Length(754_000L))
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Ended)
        idle()
        val state = viewModel.uiState.value
        assertEquals(VideoStatus.Ended, state.status)
        assertTrue(state.isActive)
        assertEquals(754_000L, state.positionMs)
        assertEquals(listOf("finished:1"), progress.events)
        assertEquals(1, focus.released)
        // Play again asks for focus again.
        viewModel.togglePlayPause()
        assertEquals("play", engine.calls.last())
        assertEquals(2, focus.acquired)
    }

    @Test
    fun audioFocusLossPausesAndTransientLossResumes() {
        viewModel.open(beach)
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        idle()

        focus.send(VideoAudioEvent.LossTransient)
        assertEquals("pause", engine.calls.last())
        engine.emit(VideoEngineEvent.Paused)
        idle()
        focus.send(VideoAudioEvent.Gain)
        assertEquals("play", engine.calls.last())
        engine.emit(VideoEngineEvent.Playing)
        idle()

        focus.send(VideoAudioEvent.BecomingNoisy)
        assertEquals("pause", engine.calls.last())
        engine.emit(VideoEngineEvent.Paused)
        idle()
        focus.send(VideoAudioEvent.Gain)
        assertEquals("pause", engine.calls.last())

        viewModel.play()
        engine.emit(VideoEngineEvent.Playing)
        idle()
        focus.send(VideoAudioEvent.Loss)
        assertEquals("pause", engine.calls.last())
        // Permanent loss gives focus back; only the user's play asks again.
        assertEquals(1, focus.released)
    }

    private class FakeFocus : VideoAudioFocus {
        var acquired = 0
        var released = 0
        private var listener: ((VideoAudioEvent) -> Unit)? = null

        override fun acquire(onEvent: (VideoAudioEvent) -> Unit): Boolean {
            acquired++
            listener = onEvent
            return true
        }

        override fun release() {
            released++
        }

        fun send(event: VideoAudioEvent) {
            listener?.invoke(event)
        }
    }

    private class RecordingProgress : VideoProgressHooks {
        val events = mutableListOf<String>()

        override fun onCheckpoint(video: Video, positionMs: Long, durationMs: Long) {
            events += "checkpoint:${video.id}@$positionMs"
        }

        override fun onFinished(video: Video) {
            events += "finished:${video.id}"
        }
    }

    private companion object {
        fun video(id: Long, width: Int, height: Int) = Video(
            id = id,
            title = "Video $id",
            displayName = "video_$id.mp4",
            durationMs = 754_000L,
            sizeBytes = 1_000L,
            width = width,
            height = height,
            mimeType = "video/mp4",
            dateAddedSeconds = 0L,
            folder = null,
            contentUri = "content://media/external/video/media/$id",
        )

        val beach = video(1L, 1920, 1080)
        val portrait = video(2L, 1080, 1920)
    }
}
