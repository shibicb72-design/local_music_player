package com.shibi.musicplayer

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.diagnostics.ErrorLogger
import com.shibi.musicplayer.domain.MediaFacts
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAudioEvent
import com.shibi.musicplayer.video.EngineOptions
import com.shibi.musicplayer.video.ExternalTrackKind
import com.shibi.musicplayer.video.ExternalTrackSource
import com.shibi.musicplayer.video.InMemoryVideoPrefs
import com.shibi.musicplayer.video.PlayerSurface
import com.shibi.musicplayer.video.PreparedTrack
import com.shibi.musicplayer.video.RawMediaTrack
import com.shibi.musicplayer.video.RawTrackDescription
import com.shibi.musicplayer.video.MediaTrackType
import com.shibi.musicplayer.video.SubtitleTextSize
import com.shibi.musicplayer.video.TrackMessage
import com.shibi.musicplayer.video.TrackSnapshot
import com.shibi.musicplayer.video.UnsupportedTrackFileException
import com.shibi.musicplayer.video.VideoAudioFocus
import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoError
import com.shibi.musicplayer.video.VideoProgressHooks
import com.shibi.musicplayer.video.VideoViewModel
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VideoTracksViewModelTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val engines = mutableListOf<FakeVideoEngine>()
    private val logged = mutableListOf<Logged>()
    private val logger = ErrorLogger { step, error, shown, media -> logged += Logged(step, error, shown, media) }
    private val source = FakeSource()
    private val prefs = InMemoryVideoPrefs()
    private val viewModel = VideoViewModel(
        application = app,
        engineFactory = { FakeVideoEngine(initialTracks = movieTracks).also { engines += it } },
        audioFocus = NoFocus,
        progress = VideoProgressHooks.None,
        prefs = prefs,
        errorLogger = logger,
        externalTracks = source,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun openMovie(): FakeVideoEngine {
        idle()
        viewModel.open(movie)
        idle()
        return engines.single()
    }

    @Test
    fun theSheetShowsTheEnginesTracks() {
        openMovie()
        val sheet = viewModel.trackSheet.value
        assertEquals(listOf(1, 2), sheet.audio.map { it.id })
        assertEquals(listOf(-1, 3), sheet.subtitles.map { it.id })
        assertTrue(sheet.subtitles.first().selected)
    }

    @Test
    fun choosingTracksReachesTheEngine() {
        val engine = openMovie()
        viewModel.selectAudioTrack(2)
        viewModel.selectSubtitleTrack(3)
        viewModel.selectSubtitleTrack(-1)
        idle()
        assertEquals(listOf("audio:2", "subtitle:3", "subtitle:-1"), engine.calls.drop(1))
        assertTrue(viewModel.trackSheet.value.audio.single { it.id == 2 }.selected)
    }

    @Test
    fun delayStepperTapsMoveInWholeSteps() {
        val engine = openMovie()
        viewModel.stepSubtitleDelay(1)
        viewModel.stepSubtitleDelay(1)
        viewModel.stepAudioDelay(-1)
        idle()
        assertEquals(listOf("subtitleDelay:100", "subtitleDelay:200", "audioDelay:-50"), engine.calls.drop(1))
        assertEquals(200L, viewModel.trackSheet.value.subtitleDelayMs)
        assertEquals(-50L, viewModel.trackSheet.value.audioDelayMs)
    }

    @Test
    fun theSavedDecodingAndTextSizeGoToTheEngineBeforeTheVideoLoadsAndOnEveryChange() {
        val engine = openMovie()
        assertEquals(EngineOptions(), engine.configured.single())
        viewModel.setHardwareDecoding(false)
        idle()
        viewModel.setSubtitleTextSize(SubtitleTextSize.Large)
        idle()
        assertEquals(
            EngineOptions(hardwareDecoding = false, subtitleTextSize = SubtitleTextSize.Large),
            engine.configured.last(),
        )
        assertEquals(false, prefs.hardwareDecoding.value)
        assertEquals(SubtitleTextSize.Large, prefs.subtitleTextSize.value)
        assertEquals(false, viewModel.trackSheet.value.hardwareDecoding)
    }

    @Test
    fun aLoadedSubtitleFileIsAddedSelectedAndNamed() {
        val engine = openMovie()
        source.facts = MediaFacts(fileName = "Movie.en.srt", mimeType = "application/x-subrip", sizeBytes = 2_000L)
        viewModel.loadExternalTrack(ExternalTrackKind.Subtitle, "content://docs/document/7")
        idle()
        assertEquals("external:Subtitle:file:///cache/Movie.en.srt", engine.calls.last())
        val sheet = viewModel.trackSheet.value
        val loaded = sheet.subtitles.last()
        assertEquals("Movie.en.srt", loaded.label)
        assertTrue(loaded.external && loaded.selected)
        assertNull(sheet.message)
        assertNull(sheet.loading)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun aFileThatIsNotASubtitleIsRefusedWithAShortMessageAndLogged() {
        val engine = openMovie()
        source.facts = MediaFacts(fileName = "notes.txt", mimeType = "text/plain")
        viewModel.loadExternalTrack(ExternalTrackKind.Subtitle, "content://docs/document/8")
        idle()
        assertTrue(engine.calls.none { it.startsWith("external:") })
        assertEquals(0, source.prepared)
        assertEquals(TrackMessage.NotASubtitle, viewModel.trackSheet.value.message)
        val entry = logged.single()
        assertEquals("Load subtitle file for movie.mkv (inline box)", entry.step)
        assertTrue(entry.error is UnsupportedTrackFileException)
        assertEquals("That isn't a subtitle file (.srt .ass .ssa .vtt .sub .idx .ttml).", entry.shown)
        assertEquals("notes.txt", entry.media?.fileName)
        // Closing the sheet drops the message.
        viewModel.dismissTrackMessage()
        idle()
        assertNull(viewModel.trackSheet.value.message)
    }

    @Test
    fun anAudioFileLibVlcRefusesIsClosedReportedAndLogged() {
        val engine = openMovie()
        engine.acceptExternal = false
        viewModel.setPlayerSurface(PlayerSurface.Fullscreen)
        source.facts = MediaFacts(fileName = "dub.ac3", mimeType = "audio/ac3")
        viewModel.loadExternalTrack(ExternalTrackKind.Audio, "content://docs/document/9")
        idle()
        assertTrue(source.lastResource!!.closed)
        assertEquals(TrackMessage.AudioFailed, viewModel.trackSheet.value.message)
        val entry = logged.single()
        assertEquals("Load audio file for movie.mkv (fullscreen)", entry.step)
        assertTrue(entry.error!!.message!!.contains("addSlave"))
        assertEquals("Couldn't load that audio file.", entry.shown)
    }

    @Test
    fun aFileThatCannotBeReadIsLoggedWithItsException() {
        openMovie()
        source.facts = MediaFacts(fileName = "Movie.en.srt")
        source.failure = FileNotFoundException("open failed: EACCES")
        viewModel.loadExternalTrack(ExternalTrackKind.Subtitle, "content://docs/document/10")
        idle()
        assertEquals(TrackMessage.SubtitleFailed, viewModel.trackSheet.value.message)
        assertEquals("open failed: EACCES", logged.single().error?.message)
    }

    @Test
    fun aFileWhoseTrackNeverAppearsTimesOutWithAMessageAndALogEntry() {
        val engine = openMovie()
        engine.externalAddsTrack = false
        source.facts = MediaFacts(fileName = "Broken.srt")
        viewModel.loadExternalTrack(ExternalTrackKind.Subtitle, "content://docs/document/12")
        idle()
        assertEquals(ExternalTrackKind.Subtitle, viewModel.trackSheet.value.loading)
        assertTrue(logged.isEmpty())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(VideoViewModel.TrackAppearTimeoutMs + 100))
        assertEquals(TrackMessage.SubtitleFailed, viewModel.trackSheet.value.message)
        assertNull(viewModel.trackSheet.value.loading)
        assertTrue(logged.single().error!!.message!!.contains("within 4000 ms"))
    }

    @Test
    fun closingTheVideoMidLoadLeavesNoSpinnerMessageOrLog() {
        val engine = openMovie()
        engine.externalAddsTrack = false
        source.facts = MediaFacts(fileName = "Slow.srt")
        viewModel.loadExternalTrack(ExternalTrackKind.Subtitle, "content://docs/document/13")
        idle()
        viewModel.close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertNull(viewModel.trackSheet.value.loading)
        assertNull(viewModel.trackSheet.value.message)
        assertTrue(logged.isEmpty())
    }

    @Test
    fun aSecondPickKeepsItsOwnSpinner() {
        val engine = openMovie()
        engine.externalAddsTrack = false
        source.facts = MediaFacts(fileName = "First.srt")
        viewModel.loadExternalTrack(ExternalTrackKind.Subtitle, "content://docs/document/14")
        idle()
        source.facts = MediaFacts(fileName = "dub.ac3")
        viewModel.loadExternalTrack(ExternalTrackKind.Audio, "content://docs/document/15")
        idle()
        // The first (cancelled) load must not clear the second one's spinner.
        assertEquals(ExternalTrackKind.Audio, viewModel.trackSheet.value.loading)
        assertTrue("the cancelled load logs nothing", logged.isEmpty())
    }

    @Test
    fun aNewVideoClearsTheCopiesAndTheMessage() {
        openMovie()
        source.facts = MediaFacts(fileName = "x.txt")
        viewModel.loadExternalTrack(ExternalTrackKind.Subtitle, "content://docs/document/11")
        idle()
        val clearsBefore = source.cleared
        viewModel.open(movie.copy(id = 2L, contentUri = "content://media/external/video/media/2"))
        idle()
        assertEquals(clearsBefore + 1, source.cleared)
        assertNull(viewModel.trackSheet.value.message)
    }

    @Test
    fun aVideoThatCannotBeOpenedIsLoggedWithTheRealCauseAndTheFile() {
        val engine = openMovie()
        val cause = FileNotFoundException("open failed: ENOENT")
        engine.emit(VideoEngineEvent.Failed(VideoError.CannotOpen, cause))
        idle()
        val entry = logged.single()
        assertEquals("Open video (inline box)", entry.step)
        assertSame(cause, entry.error)
        assertEquals("Couldn't open this video. It may have been moved or deleted.", entry.shown)
        with(entry.media!!) {
            assertEquals("movie.mkv", fileName)
            assertEquals("video/x-matroska", mimeType)
            assertEquals(1920, width)
            assertEquals(1080, height)
            assertEquals(5_400_000L, durationMs)
            assertEquals(2_000_000_000L, sizeBytes)
            assertEquals(listOf("video h264 1920x1080", "audio a52 eng", "audio mp4a jpn", "text subt eng"), codecs)
        }
        // The same error state again is not a second entry.
        engine.emit(VideoEngineEvent.Time(1_000L))
        idle()
        assertEquals(1, logged.size)
    }

    @Test
    fun aLibVlcPlaybackErrorInThePaneIsLogged() {
        val engine = openMovie()
        viewModel.setPlayerSurface(PlayerSurface.VideoPane)
        engine.emit(VideoEngineEvent.Playing)
        engine.emit(VideoEngineEvent.Failed(VideoError.Playback, IOException("decoder")))
        idle()
        val entry = logged.single()
        assertEquals("Play video (video pane): libVLC error", entry.step)
        assertEquals("This video can't be played.", entry.shown)
        assertEquals("decoder", entry.error?.message)
    }

    private data class Logged(val step: String, val error: Throwable?, val shown: String?, val media: MediaFacts?)

    private class Resource : Closeable {
        var closed = false

        override fun close() {
            closed = true
        }
    }

    private class FakeSource : ExternalTrackSource {
        var facts = MediaFacts()
        var failure: Exception? = null
        var prepared = 0
        var cleared = 0
        var lastResource: Resource? = null

        override fun describe(uri: String): MediaFacts = facts

        override fun prepare(kind: ExternalTrackKind, uri: String, facts: MediaFacts): PreparedTrack {
            failure?.let { throw it }
            prepared++
            val resource = Resource().also { lastResource = it }
            return when (kind) {
                ExternalTrackKind.Subtitle -> PreparedTrack("file:///cache/${facts.fileName}", facts.fileName!!, null)
                ExternalTrackKind.Audio -> PreparedTrack("fd://42", facts.fileName!!, resource)
            }
        }

        override fun clear() {
            cleared++
        }
    }

    private object NoFocus : VideoAudioFocus {
        override fun acquire(onEvent: (VideoAudioEvent) -> Unit): Boolean = true
        override fun release() = Unit
    }

    private companion object {
        val movie = Video(
            id = 1L,
            title = "Movie",
            displayName = "movie.mkv",
            durationMs = 5_400_000L,
            sizeBytes = 2_000_000_000L,
            width = 1920,
            height = 1080,
            mimeType = "video/x-matroska",
            dateAddedSeconds = 0L,
            folder = null,
            contentUri = "content://media/external/video/media/1",
        )

        val movieTracks = TrackSnapshot(
            audio = listOf(
                RawTrackDescription(-1, "Disable"),
                RawTrackDescription(1, "Track 1 - [English]"),
                RawTrackDescription(2, "Track 2 - [Japanese]"),
            ),
            subtitles = listOf(RawTrackDescription(-1, "Disable"), RawTrackDescription(3, "Track 1 - [English]")),
            mediaTracks = listOf(
                RawMediaTrack(0, codec = "h264", type = MediaTrackType.Video, info = "1920x1080"),
                RawMediaTrack(1, language = "eng", codec = "a52", type = MediaTrackType.Audio),
                RawMediaTrack(2, language = "jpn", codec = "mp4a", type = MediaTrackType.Audio),
                RawMediaTrack(3, language = "eng", codec = "subt", type = MediaTrackType.Text),
            ),
            selectedAudioId = 1,
        )
    }
}
