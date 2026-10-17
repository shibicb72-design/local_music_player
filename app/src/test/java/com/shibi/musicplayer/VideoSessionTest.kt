package com.shibi.musicplayer

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.diagnostics.ErrorLogger
import com.shibi.musicplayer.domain.BackgroundEvent
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAudioEvent
import com.shibi.musicplayer.video.MusicControl
import com.shibi.musicplayer.video.VideoAudioFocus
import com.shibi.musicplayer.video.VideoEngine
import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoError
import com.shibi.musicplayer.video.VideoProgressHooks
import com.shibi.musicplayer.video.VideoSessionControls
import com.shibi.musicplayer.video.VideoSessionLink
import com.shibi.musicplayer.video.VideoSessionMapping
import com.shibi.musicplayer.video.VideoSessionSnapshot
import com.shibi.musicplayer.video.VideoSessionStatus
import com.shibi.musicplayer.video.VideoStatus
import com.shibi.musicplayer.video.VideoUiState
import com.shibi.musicplayer.video.VideoViewModel
import com.shibi.musicplayer.video.VlcSessionPlayer
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * V8: the video's Media3 session through [VlcSessionPlayer], driven by the view model and
 * [FakeVideoEngine] (the service only hosts it), plus the screen-lock hooks.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VideoSessionTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val engines = mutableListOf<FakeVideoEngine>()
    private val link = RecordingSessionLink()
    private val logged = mutableListOf<String>()
    private val viewModel = VideoViewModel(
        application = app,
        engineFactory = { FakeVideoEngine().also { engines += it } },
        audioFocus = NoFocus,
        progress = VideoProgressHooks.None,
        errorLogger = ErrorLogger { step, _, _, _ -> logged += step },
        sessionLink = link,
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun player(): VlcSessionPlayer = checkNotNull(link.player) { "no session" }

    private fun openPlaying(positionMs: Long = 0L): FakeVideoEngine {
        viewModel.open(beach)
        val engine = engines.single()
        engine.emit(VideoEngineEvent.Playing)
        if (positionMs > 0L) engine.emit(VideoEngineEvent.Time(positionMs))
        idle()
        return engine
    }

    // --- The pure mapping ---

    @Test
    fun theSessionIsUpOnlyWhileAVideoIsLoadedAndNotFinished() {
        assertNull(VideoSessionMapping.snapshotOf(VideoUiState()))
        val loaded = VideoUiState(video = beach, durationMs = 754_000L)
        val expected = mapOf(
            VideoStatus.Buffering to VideoSessionStatus.Buffering,
            VideoStatus.Idle to VideoSessionStatus.Buffering,
            VideoStatus.Playing to VideoSessionStatus.Playing,
            VideoStatus.Paused to VideoSessionStatus.Paused,
            VideoStatus.Ended to null,
            VideoStatus.Error to null,
        )
        expected.forEach { (status, session) ->
            assertEquals(status.name, session, VideoSessionMapping.snapshotOf(loaded.copy(status = status))?.status)
        }
    }

    @Test
    fun theSnapshotLeavesThePositionOut() {
        val a = VideoUiState(video = beach, status = VideoStatus.Playing, positionMs = 1_000L, durationMs = 754_000L)
        val b = a.copy(positionMs = 61_000L)
        assertEquals(VideoSessionMapping.snapshotOf(a), VideoSessionMapping.snapshotOf(b))
        assertEquals(
            VideoSessionSnapshot(1L, "Beach day", beach.contentUri, 754_000L, VideoSessionStatus.Playing),
            VideoSessionMapping.snapshotOf(a),
        )
    }

    @Test
    fun theCommandsArePlayPauseSeekAndStopWithNoPlaylistSpeedOrVolume() {
        val commands = VideoSessionMapping.AvailableCommands
        listOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
        ).forEach { assertTrue("command $it", commands.contains(it)) }
        listOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SET_SPEED_AND_PITCH,
            Player.COMMAND_SET_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            Player.COMMAND_CHANGE_MEDIA_ITEMS,
        ).forEach { assertFalse("command $it", commands.contains(it)) }
    }

    // --- The adapter, through the view model and the fake engine ---

    @Test
    fun noSessionUntilAVideoLoads() {
        idle()
        assertNull(link.player)
        assertTrue(link.shown.isEmpty())
    }

    @Test
    fun aLoadingVideoShowsItsTitleArtworkAndLength() {
        viewModel.open(beach)
        idle()
        val player = player()
        assertEquals(Player.STATE_BUFFERING, player.playbackState)
        assertTrue(player.playWhenReady)
        val metadata = player.mediaMetadata
        assertEquals("Beach day", metadata.title.toString())
        assertEquals(beach.contentUri, metadata.artworkUri.toString())
        assertEquals(MediaMetadata.MEDIA_TYPE_VIDEO, metadata.mediaType)
        assertEquals(754_000L, player.duration)
        assertEquals(1, player.mediaItemCount)
        assertTrue(player.isCurrentMediaItemSeekable)
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_FORWARD))
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertEquals(10_000L, player.seekBackIncrement)
        assertEquals(10_000L, player.seekForwardIncrement)
    }

    @Test
    fun playingAndPausedMapToReadyWithPlayWhenReady() {
        val engine = openPlaying()
        assertEquals(Player.STATE_READY, player().playbackState)
        assertTrue(player().isPlaying)
        engine.emit(VideoEngineEvent.Paused)
        idle()
        assertEquals(Player.STATE_READY, player().playbackState)
        assertFalse(player().playWhenReady)
        assertFalse(player().isPlaying)
    }

    @Test
    fun thePositionIsReadLiveWithoutANewSnapshot() {
        val engine = openPlaying(positionMs = 42_000L)
        val sent = link.shown.size
        assertEquals(42_000L, player().currentPosition)
        engine.emit(VideoEngineEvent.Time(43_000L))
        engine.emit(VideoEngineEvent.Time(44_000L))
        idle()
        assertEquals(44_000L, player().currentPosition)
        assertEquals("a time tick re-sent the session", sent, link.shown.size)
    }

    @Test
    fun sessionPlayAndPauseReachTheEngine() {
        val engine = openPlaying()
        player().pause()
        idle()
        assertEquals("pause", engine.calls.last())
        // The button shows the request at once, before libVLC reports it.
        assertFalse(player().playWhenReady)
        engine.emit(VideoEngineEvent.Paused)
        idle()
        player().play()
        idle()
        assertEquals("play", engine.calls.last())
        assertTrue(player().playWhenReady)
    }

    @Test
    fun aPlayTheEngineNeverConfirmsFallsBackToItsState() {
        val engine = openPlaying()
        engine.emit(VideoEngineEvent.Paused)
        idle()
        player().play()
        idle()
        assertTrue(player().playWhenReady)
        // libVLC never reports Playing (the call was ignored): the notification shows paused again.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertFalse(player().playWhenReady)
    }

    @Test
    fun earbudAndNotificationSeeksMoveTenSeconds() {
        val engine = openPlaying(positionMs = 42_000L)
        player().seekForward()
        idle()
        assertEquals("seek:52000", engine.calls.last())
        player().seekBack()
        idle()
        assertEquals("seek:42000", engine.calls.last())
        player().seekTo(5_000L)
        idle()
        assertEquals("seek:5000", engine.calls.last())
        // Never before the start.
        player().seekBack()
        idle()
        assertEquals("seek:0", engine.calls.last())
    }

    @Test
    fun aStopFromAControllerClosesTheVideo() {
        val engine = openPlaying()
        player().stop()
        idle()
        assertTrue(engine.released)
        assertFalse(viewModel.uiState.value.isActive)
        assertNull("the session is gone", link.player)
    }

    @Test
    fun closingTheVideoEndsTheSessionAndItsService() {
        openPlaying()
        viewModel.close()
        idle()
        assertNull(link.player)
        assertTrue(link.ends > 0)
    }

    @Test
    fun theEndOrAFailureEndsTheSessionAndPlayAgainBringsItBack() {
        val engine = openPlaying()
        engine.emit(VideoEngineEvent.Ended)
        idle()
        assertNull("the end left the notification up", link.player)
        // The box's "Play again".
        viewModel.play()
        engine.emit(VideoEngineEvent.Playing)
        idle()
        assertNotNull(link.player)
        assertTrue(player().isPlaying)
        engine.emit(VideoEngineEvent.Failed(VideoError.Playback))
        idle()
        assertNull("a failure left the notification up", link.player)
    }

    // --- Never two sounds ---

    @Test
    fun playFromTheSessionKeepsTheMusicPaused() {
        val music = FakeMusic(playing = true)
        viewModel.setMusicControl(music)
        val engine = openPlaying()
        engine.emit(VideoEngineEvent.Paused)
        idle()
        player().play()
        idle()
        assertEquals("the video's play paused the music first", 1, music.pauses)
        assertEquals("play", engine.calls.last())
        // Music already paused: nothing to do.
        music.playing = false
        player().pause()
        player().play()
        idle()
        assertEquals(1, music.pauses)
    }

    // --- Screen lock and unlock ---

    @Test
    fun lockingWhilePlayingKeepsTheSoundAndDropsThePicture() {
        val engine = openPlaying(positionMs = 42_000L)
        engine.attach(engine.createView(app))
        viewModel.onAppBackground(BackgroundEvent.ScreenOff)
        assertFalse("the lock paused the video", "pause" in engine.calls)
        assertFalse(engine.pictureEnabled)
        assertNull("the view is still attached", engine.attachedView)
        assertEquals(VideoStatus.Playing, viewModel.uiState.value.status)
        assertTrue("the session went away on lock", player().isPlaying)
    }

    @Test
    fun unlockingBringsThePictureBackWhereTheSoundIs() {
        val engine = openPlaying(positionMs = 42_000L)
        val view = engine.createView(app)
        engine.attach(view)
        viewModel.onAppBackground(BackgroundEvent.ScreenOff)
        // Ten minutes of sound later.
        engine.emit(VideoEngineEvent.Time(642_000L))
        idle()
        engine.calls.clear()
        viewModel.onAppForeground()
        assertEquals(listOf("picture:true", "attach", "seek:642000"), engine.calls)
        assertEquals(view, engine.attachedView)
    }

    @Test
    fun leavingTheAppWhilePlayingPausesIt() {
        val engine = openPlaying()
        viewModel.onAppBackground(BackgroundEvent.LeftApp)
        assertTrue("pause" in engine.calls)
        assertFalse(engine.pictureEnabled)
    }

    @Test
    fun aPausedVideoStaysPausedAndCanBePlayedFromTheLockScreen() {
        val engine = openPlaying()
        engine.emit(VideoEngineEvent.Paused)
        idle()
        engine.calls.clear()
        viewModel.onAppBackground(BackgroundEvent.ScreenOff)
        assertEquals(listOf("picture:false"), engine.calls)
        player().play()
        idle()
        assertEquals(listOf("picture:false", "play"), engine.calls)
    }

    @Test
    fun theFilePickerKeepsTheVideoPlaying() {
        val engine = openPlaying()
        viewModel.setFilePickerShown(true)
        assertTrue(viewModel.isPickingFile)
        viewModel.onAppBackground(BackgroundEvent.PickingFile)
        assertFalse("the picker paused the video", "pause" in engine.calls)
        assertFalse(engine.pictureEnabled)
        viewModel.setFilePickerShown(false)
        viewModel.onAppForeground()
        assertFalse(viewModel.isPickingFile)
        assertTrue(engine.pictureEnabled)
    }

    @Test
    fun aPickerThatNeverOpenedLeavesHomeAsLeaving() {
        viewModel.setFilePickerShown(true)
        // No picker app: the sheet takes the mark back.
        viewModel.setFilePickerShown(false)
        assertFalse(viewModel.isPickingFile)
    }

    @Test
    fun theEndWhileLockedRemovesTheNotificationAndUnlockDoesNotSeek() {
        val engine = openPlaying(positionMs = 42_000L)
        viewModel.onAppBackground(BackgroundEvent.ScreenOff)
        engine.emit(VideoEngineEvent.Ended)
        idle()
        assertNull(link.player)
        engine.calls.clear()
        viewModel.onAppForeground()
        assertEquals(listOf("picture:true"), engine.calls)
    }

    @Test
    fun anotherVideoReusesTheSession() {
        openPlaying()
        val ends = link.ends
        viewModel.open(dunes)
        idle()
        assertEquals("the notification flickered off", ends, link.ends)
        assertEquals("2", player().currentMediaItem?.mediaId)
        assertEquals("Dunes", player().mediaMetadata.title.toString())
        assertEquals(Player.STATE_BUFFERING, player().playbackState)
    }

    @Test
    fun aRotationOrNoVideoNeverTouchesAnything() {
        viewModel.onAppBackground(BackgroundEvent.ScreenOff)
        viewModel.onAppForeground()
        assertTrue(engines.isEmpty())
        val engine = openPlaying()
        // Back in view without having left it (the first start): nothing to restore.
        viewModel.onAppForeground()
        assertFalse(engine.calls.any { it.startsWith("picture") || it.startsWith("seek") })
    }

    @Test
    fun closingWhileLockedLeavesTheNextVideoWithAPicture() {
        openPlaying()
        viewModel.onAppBackground(BackgroundEvent.ScreenOff)
        // Music started from the lock screen closes the video.
        viewModel.close()
        viewModel.onAppForeground()
        viewModel.open(beach)
        assertTrue(engines.last().pictureEnabled)
        assertFalse(engines.last().calls.any { it.startsWith("picture") })
    }

    @Test
    fun anErrorWhileLockedIsLoggedAsSuch() {
        val engine = openPlaying()
        viewModel.onAppBackground(BackgroundEvent.ScreenOff)
        engine.emit(VideoEngineEvent.Failed(VideoError.Playback))
        idle()
        assertEquals(listOf("Play video (inline box, screen locked): libVLC error"), logged)
        // The short message is waiting for the return.
        assertEquals(VideoError.Playback, viewModel.uiState.value.error)
    }

    /** Builds the real session player on the main looper, as the service would. */
    private class RecordingSessionLink : VideoSessionLink {
        val shown = mutableListOf<VideoSessionSnapshot>()
        var ends = 0
        var player: VlcSessionPlayer? = null

        override fun show(snapshot: VideoSessionSnapshot, controls: VideoSessionControls, engine: VideoEngine) {
            shown += snapshot
            val sessionPlayer = player ?: VlcSessionPlayer(Looper.getMainLooper(), controls).also { player = it }
            sessionPlayer.show(snapshot)
        }

        override fun end() {
            ends++
            player?.release()
            player = null
        }
    }

    private class FakeMusic(var playing: Boolean) : MusicControl {
        var pauses = 0
        override val isPlaying: Boolean get() = playing
        override fun pause() {
            pauses++
            playing = false
        }
    }

    private object NoFocus : VideoAudioFocus {
        override fun acquire(onEvent: (VideoAudioEvent) -> Unit) = true
        override fun release() = Unit
    }

    private companion object {
        val beach = Video(
            id = 1L,
            title = "Beach day",
            displayName = "Beach day.mp4",
            durationMs = 754_000L,
            sizeBytes = 1_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            dateAddedSeconds = 0L,
            folder = null,
            contentUri = "content://media/external/video/media/1",
        )
        val dunes = beach.copy(id = 2L, title = "Dunes", displayName = "Dunes.mkv", contentUri = "content://media/external/video/media/2")
    }
}
