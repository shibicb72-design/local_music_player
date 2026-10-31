package com.shibi.musicplayer

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.AspectMode
import com.shibi.musicplayer.domain.BackgroundEvent
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAudioEvent
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.ui.messageRes
import com.shibi.musicplayer.video.InMemoryVideoPrefs
import com.shibi.musicplayer.video.VideoAudioFocus
import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoError
import com.shibi.musicplayer.video.VideoProgressHooks
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoViewModel
import java.time.Duration
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

    /** The save cadence's clock; it only moves when a test moves it. */
    private var now = 1_000_000L
    private val viewModel = VideoViewModel(
        application = app,
        engineFactory = { FakeVideoEngine().also { engines += it } },
        audioFocus = focus,
        progress = progress,
        clock = { now },
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
        clock = { now },
    )

    @Test
    fun aPartlyWatchedVideoOpensWhereItWasLeftAndShowsTheChipForFourSeconds() {
        progress.stored[beach.contentUri] = VideoProgress(positionMs = 754_000L / 2, durationMs = 754_000L)
        viewModel.open(beach)
        val engine = engines.single()
        assertEquals(listOf("load:${beach.contentUri}@377000"), engine.calls)
        assertEquals(377_000L, viewModel.uiState.value.positionMs)
        // Not while it buffers: the chip comes with the picture.
        assertNull(viewModel.uiState.value.resumedAtMs)

        engine.emit(VideoEngineEvent.Playing)
        idle()
        assertEquals(377_000L, viewModel.uiState.value.resumedAtMs)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3_900L))
        assertEquals(377_000L, viewModel.uiState.value.resumedAtMs)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200L))
        assertNull(viewModel.uiState.value.resumedAtMs)
    }

    @Test
    fun theFirstFiveSecondsAndTheLastFivePercentStartAtZero() {
        progress.stored[beach.contentUri] = VideoProgress(positionMs = 5_000L, durationMs = 754_000L)
        progress.stored[portrait.contentUri] = VideoProgress(positionMs = 720_000L, durationMs = 754_000L)
        viewModel.open(beach)
        engines.single().emit(VideoEngineEvent.Playing)
        idle()
        assertNull(viewModel.uiState.value.resumedAtMs)
        viewModel.open(portrait)
        engines.single().emit(VideoEngineEvent.Playing)
        idle()
        assertNull(viewModel.uiState.value.resumedAtMs)
        val loads = engines.single().calls.filter { it.startsWith("load:") }
        assertEquals(listOf("load:${beach.contentUri}", "load:${portrait.contentUri}"), loads)
    }

    @Test
    fun theSpeedIsSetOnTheEngineAndResetsForTheNextVideo() {
        viewModel.open(beach)
        val engine = engines.single()
        assertEquals(1f, viewModel.uiState.value.speed, 0f)
        assertEquals("opening at 1x makes no native call", 0, engine.rateCalls)

        viewModel.setSpeed(1.5f)
        assertEquals(1.5f, engine.rate, 0f)
        assertEquals(1.5f, viewModel.uiState.value.speed, 0f)

        viewModel.open(portrait)
        assertEquals(1f, viewModel.uiState.value.speed, 0f)
        assertEquals(1f, engine.rate, 0f)
        assertEquals(2, engine.rateCalls)

        // Already at 1x: the next open does not touch the engine's rate again.
        viewModel.open(beach)
        assertEquals(2, engine.rateCalls)

        viewModel.setSpeed(1.4f)
        assertEquals(1.5f, viewModel.uiState.value.speed, 0f)
    }

    @Test
    fun startOverSeeksToZeroClearsTheSavedPlaceAndHidesTheChip() {
        progress.stored[beach.contentUri] = VideoProgress(positionMs = 600_000L, durationMs = 754_000L)
        viewModel.open(beach)
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        idle()
        assertEquals(600_000L, viewModel.uiState.value.resumedAtMs)

        viewModel.startOver()
        idle()
        assertEquals("seek:0", engine.calls.last())
        assertEquals(listOf("startOver:1"), progress.events)
        assertNull(viewModel.uiState.value.resumedAtMs)
        assertEquals(0L, viewModel.uiState.value.positionMs)
    }

    @Test
    fun savesEveryFiveSecondsWhilePlayingOnly() {
        viewModel.open(beach)
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        idle()

        now += 4_999L
        engine.emit(VideoEngineEvent.Time(4_999L))
        idle()
        assertTrue(progress.events.isEmpty())

        now += 1L
        engine.emit(VideoEngineEvent.Time(5_000L))
        idle()
        assertEquals(listOf("checkpoint:1@5000"), progress.events)

        // The next save is 5 s after that one, not after the open.
        now += 3_000L
        engine.emit(VideoEngineEvent.Time(8_000L))
        idle()
        assertEquals(1, progress.events.size)
        now += 2_000L
        engine.emit(VideoEngineEvent.Time(10_000L))
        idle()
        assertEquals(listOf("checkpoint:1@5000", "checkpoint:1@10000"), progress.events)

        // Paused (from anywhere): the clock runs on, nothing is saved by the cadence.
        engine.emit(VideoEngineEvent.Paused)
        now += 60_000L
        engine.emit(VideoEngineEvent.Time(10_000L))
        idle()
        assertEquals(2, progress.events.size)
    }

    @Test
    fun lockingTheScreenAndLeavingTheAppAreSavePoints() {
        viewModel.open(beach)
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Time(42_000L))
        idle()
        viewModel.onAppBackground(BackgroundEvent.ScreenOff)
        assertEquals(listOf("checkpoint:1@42000"), progress.events)
        viewModel.onAppForeground()
        engine.emit(VideoEngineEvent.Time(43_000L))
        idle()
        viewModel.onAppBackground(BackgroundEvent.LeftApp)
        // Leaving pauses, and the pause saves.
        assertEquals("checkpoint:1@43000", progress.events.last())
    }

    @Test
    fun nothingIsSavedOverTheOldPlaceBeforeTheVideoPlays() {
        progress.stored[beach.contentUri] = VideoProgress(positionMs = 300_000L, durationMs = 754_000L)
        viewModel.open(beach)
        // Closed while it was still opening: the saved 5:00 stays.
        viewModel.close()
        assertTrue(progress.events.isEmpty())
    }

    @Test
    fun aMissingMediaStoreDurationUsesTheSavedLength() {
        val mkv = beach.copy(id = 9L, durationMs = null, contentUri = "content://media/external/video/media/9")
        progress.stored[mkv.contentUri] = VideoProgress(positionMs = 0L, durationMs = 5_400_000L)
        viewModel.open(mkv)
        assertEquals(5_400_000L, viewModel.uiState.value.durationMs)
        assertEquals("load:${mkv.contentUri}", engines.single().calls.single())
    }

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
    fun seekByMovesThePositionAndStaysInsideTheVideo() {
        viewModel.open(beach)
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Time(60_000L))
        idle()

        // Backwards past the start clamps to 0.
        viewModel.seekBy(-70_000L)
        idle()
        assertEquals("seek:0", engine.calls.last())
        assertEquals(0L, viewModel.uiState.value.positionMs)

        // Forwards past the end clamps to the duration.
        viewModel.seekBy(800_000L)
        idle()
        assertEquals("seek:754000", engine.calls.last())
        assertEquals(754_000L, viewModel.uiState.value.positionMs)
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

    /**
     * Two-pane: the end leaves fullscreen (MusicApp pops the route) and the pane shows the same
     * video, still loaded and at its end; its Play again starts over from 0.
     */
    @Test
    fun aVideoThatEndsInFullscreenStaysLoadedAndPlayAgainStartsOverFromZero() {
        viewModel.open(beach)
        val engine = engines.single()
        viewModel.setFullscreen(true)
        engine.emit(VideoEngineEvent.Length(754_000L))
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Time(700_000L))
        engine.emit(VideoEngineEvent.Ended)
        idle()
        viewModel.setFullscreen(false)
        with(viewModel.uiState.value) {
            assertSame("the ended video is still the selected one", beach, video)
            assertTrue(isActive)
            assertEquals(VideoStatus.Ended, status)
            assertEquals(754_000L, positionMs)
        }
        assertSame(engine, viewModel.engine.value)
        assertFalse("the end released the engine", engine.released)
        assertEquals("the pane is back at fit", "aspect:Fit", engine.calls.last())

        viewModel.togglePlayPause()
        idle()
        assertEquals(listOf("seek:0", "play"), engine.calls.takeLast(2))
        assertEquals(0L, viewModel.uiState.value.positionMs)
        assertEquals("Play again asks for focus again", 2, focus.acquired)
        engine.emit(VideoEngineEvent.Playing)
        idle()
        assertEquals(VideoStatus.Playing, viewModel.uiState.value.status)
        assertEquals("the same load plays again", 1, engine.calls.count { it.startsWith("load:") })
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

    @Test
    fun positionReachesTheUiAboutFourTimesASecond() {
        viewModel.open(beach)
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        idle()

        engine.emit(VideoEngineEvent.Time(1_000L))
        idle()
        assertEquals(1_000L, viewModel.uiState.value.positionMs)

        engine.emit(VideoEngineEvent.Time(1_100L))
        idle()
        assertEquals(1_000L, viewModel.uiState.value.positionMs)

        engine.emit(VideoEngineEvent.Time(1_200L))
        idle()
        assertEquals(1_000L, viewModel.uiState.value.positionMs)

        engine.emit(VideoEngineEvent.Time(1_260L))
        idle()
        assertEquals(1_260L, viewModel.uiState.value.positionMs)

        engine.emit(VideoEngineEvent.Time(9_000L))
        idle()
        assertEquals(9_000L, viewModel.uiState.value.positionMs)
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
        val stored = mutableMapOf<String, VideoProgress>()

        override fun saved(video: Video): VideoProgress? = stored[video.contentUri]

        override fun onCheckpoint(video: Video, positionMs: Long, durationMs: Long) {
            events += "checkpoint:${video.id}@$positionMs"
        }

        override fun onFinished(video: Video, durationMs: Long) {
            events += "finished:${video.id}"
        }

        override fun onStartOver(video: Video, durationMs: Long) {
            events += "startOver:${video.id}"
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
