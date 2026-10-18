package com.shibi.musicplayer

import com.shibi.musicplayer.domain.PlaybackCheck
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCheckTest {

    @Test
    fun framesShownPerSecondOverTheWindow() {
        assertEquals(30.0, PlaybackCheck.fps(100, 250, 5_000L), 0.0)
    }

    @Test
    fun zeroWindowReadsZero() {
        assertEquals(0.0, PlaybackCheck.fps(100, 250, 0L), 0.0)
    }

    @Test
    fun summaryNamesHardwareDecoderAndSpeakerAudio() {
        assertEquals(
            "Playback check: 29.8 fps shown, 150 frames decoded, 0 frames lost; " +
                "audio 230 buffers played, 0 lost; " +
                "decoder MediaCodec (mediacodec_ndk, software fallback allowed); " +
                "audio out android_audiotrack to speaker",
            PlaybackCheck.summary(
                fps = 29.84,
                lostPictures = 0,
                decodedVideo = 150,
                playedAudioBuffers = 230,
                lostAudioBuffers = 0,
                decoder = "MediaCodec (mediacodec_ndk, software fallback allowed)",
                audioOutput = "android_audiotrack to speaker",
            ),
        )
    }

    @Test
    fun summaryNamesSoftwareDecoderAndBluetoothAudioWithLostBuffers() {
        assertEquals(
            "Playback check: 12.0 fps shown, 90 frames decoded, 7 frames lost; " +
                "audio 120 buffers played, 3 lost; " +
                "decoder software (avcodec); audio out opensles to bluetooth",
            PlaybackCheck.summary(
                fps = 12.0,
                lostPictures = 7,
                decodedVideo = 90,
                playedAudioBuffers = 120,
                lostAudioBuffers = 3,
                decoder = "software (avcodec)",
                audioOutput = "opensles to bluetooth",
            ),
        )
    }
}
