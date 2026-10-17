package com.shibi.musicplayer

import com.shibi.musicplayer.video.VideoEngineEvent
import com.shibi.musicplayer.video.VideoEngineState
import com.shibi.musicplayer.video.VideoError
import com.shibi.musicplayer.video.VideoStateReducer
import com.shibi.musicplayer.video.VideoStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoStateReducerTest {

    private fun run(vararg events: VideoEngineEvent, from: VideoEngineState = VideoEngineState()) =
        events.fold(from, VideoStateReducer::reduce)

    @Test
    fun openingBuffersThenPlays() {
        val opening = run(VideoEngineEvent.Opening)
        assertEquals(VideoStatus.Buffering, opening.status)
        val playing = run(
            VideoEngineEvent.Opening,
            VideoEngineEvent.Buffering(40f),
            VideoEngineEvent.Length(90_000L),
            VideoEngineEvent.Playing,
            VideoEngineEvent.Time(1_500L),
        )
        assertEquals(VideoStatus.Playing, playing.status)
        assertEquals(1_500L, playing.positionMs)
        assertEquals(90_000L, playing.durationMs)
    }

    @Test
    fun bufferingWhilePlayingKeepsPlaying() {
        val state = run(VideoEngineEvent.Opening, VideoEngineEvent.Playing, VideoEngineEvent.Buffering(20f))
        assertEquals(VideoStatus.Playing, state.status)
    }

    @Test
    fun openingResetsTheLastVideo() {
        val previous = run(
            VideoEngineEvent.Length(60_000L),
            VideoEngineEvent.Playing,
            VideoEngineEvent.Time(30_000L),
            VideoEngineEvent.Size(1920, 1080, 16f / 9f),
        )
        val next = VideoStateReducer.reduce(previous, VideoEngineEvent.Opening)
        assertEquals(VideoEngineState(status = VideoStatus.Buffering), next)
    }

    @Test
    fun pauseAndResume() {
        val paused = run(VideoEngineEvent.Playing, VideoEngineEvent.Paused)
        assertEquals(VideoStatus.Paused, paused.status)
        assertEquals(VideoStatus.Playing, VideoStateReducer.reduce(paused, VideoEngineEvent.Playing).status)
    }

    @Test
    fun endShowsTheFullLengthAndSurvivesTheTrailingStop() {
        val ended = run(
            VideoEngineEvent.Length(60_000L),
            VideoEngineEvent.Playing,
            VideoEngineEvent.Time(59_700L),
            VideoEngineEvent.Ended,
            VideoEngineEvent.Stopped,
            VideoEngineEvent.Paused,
        )
        assertEquals(VideoStatus.Ended, ended.status)
        assertEquals(60_000L, ended.positionMs)
    }

    @Test
    fun aStaleStopNeverHidesTheNextOpening() {
        // load() stops the old media; its Stopped event lands after the new Opening.
        val state = run(VideoEngineEvent.Playing, VideoEngineEvent.Opening, VideoEngineEvent.Stopped)
        assertEquals(VideoStatus.Buffering, state.status)
    }

    @Test
    fun stopWhilePlayingIsIdle() {
        assertEquals(VideoStatus.Idle, run(VideoEngineEvent.Playing, VideoEngineEvent.Stopped).status)
    }

    @Test
    fun errorsAreKeptUntilPlayback() {
        val failed = run(VideoEngineEvent.Opening, VideoEngineEvent.Failed(VideoError.Playback), VideoEngineEvent.Stopped)
        assertEquals(VideoStatus.Error, failed.status)
        assertEquals(VideoError.Playback, failed.error)
        val recovered = VideoStateReducer.reduce(failed, VideoEngineEvent.Playing)
        assertNull(recovered.error)
    }

    @Test
    fun timeIsClampedToTheLength() {
        val state = run(VideoEngineEvent.Length(10_000L), VideoEngineEvent.Time(12_000L))
        assertEquals(10_000L, state.positionMs)
        assertEquals(0L, run(VideoEngineEvent.Time(-5L)).positionMs)
        // Unknown length: no clamp.
        assertEquals(12_000L, run(VideoEngineEvent.Time(12_000L)).positionMs)
    }

    @Test
    fun sizeKeepsTheLastKnownAspect() {
        val sized = run(VideoEngineEvent.Size(1920, 1080, 16f / 9f), VideoEngineEvent.Size(1920, 1080, null))
        assertEquals(16f / 9f, sized.aspect!!, 0.0001f)
        assertEquals(1920, sized.videoWidth)
    }
}
