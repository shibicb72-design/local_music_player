package com.shibi.musicplayer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.HistoryType
import com.shibi.musicplayer.library.VideoThumbnail
import com.shibi.musicplayer.ui.HistoryRow
import com.shibi.musicplayer.ui.RecentHistoryScreen
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * V16 Recently Played: the history grouped by day, over the dark default and one pastel,
 * plus the empty state. Rows: a song and a video today, a song yesterday, a video three
 * days ago and a song from last year. A fixed now and zone keeps the labels exact.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class RecentHistoryScreenshotTest {

    private var originalTimeZone: TimeZone? = null

    /** The row times read as clock times; pin the zone so labels match every host machine. */
    @Before
    fun pinTimeZone() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    }

    @After
    fun restoreTimeZone() {
        originalTimeZone?.let { TimeZone.setDefault(it) }
    }

    @Test
    fun dark() = capture(AppBackground.Default, rows, "cv16_recent_history_dark.png")

    @Test
    fun sky() = capture(AppBackground.Solid("sky"), rows, "cv16_recent_history_sky.png")

    @Test
    fun empty() = capture(AppBackground.Solid("sky"), emptyList(), "cv16_recent_history_empty.png")

    @OptIn(ExperimentalCoilApi::class)
    private fun capture(background: AppBackground, rows: List<HistoryRow>, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    CompositionLocalProvider(
                        LocalInspectionMode provides true,
                        LocalAsyncImagePreviewHandler provides previews,
                    ) {
                        RecentHistoryScreen(
                            rows = rows,
                            onPlay = {},
                            onClearHistory = {},
                            contentBottomPadding = SongListBottomPadding,
                            modifier = Modifier.fillMaxSize(),
                            nowMs = Now,
                            zone = Zone,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val Zone: ZoneId = ZoneId.of("America/New_York")

        /** Sat 2026-10-17 21:30 in New York: Today, Yesterday, Wednesday, then Oct 3, 2025. */
        val Now: Long = ZonedDateTime.of(2026, 10, 17, 21, 30, 0, 0, Zone).toInstant().toEpochMilli()

        private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
            ZonedDateTime.of(year, month, day, hour, minute, 0, 0, Zone).toInstant().toEpochMilli()

        private val rows = listOf(
            HistoryRow(
                key = 1L,
                type = HistoryType.Song,
                mediaId = 11L,
                title = "Neon Skyline",
                subtitle = "The Meridian",
                artUri = "content://media/external/audio/albumart/11",
                videoUri = null,
                playedAt = at(2026, 10, 17, 21, 6),
            ),
            HistoryRow(
                key = 2L,
                type = HistoryType.Video,
                mediaId = 901L,
                title = "Beach day",
                subtitle = "",
                artUri = null,
                videoUri = "content://media/external/video/media/901",
                playedAt = at(2026, 10, 17, 19, 44),
            ),
            HistoryRow(
                key = 3L,
                type = HistoryType.Song,
                mediaId = 12L,
                title = "Paper Lanterns",
                subtitle = "Mio Hara",
                artUri = "content://media/external/audio/albumart/12",
                videoUri = null,
                playedAt = at(2026, 10, 16, 9, 2),
            ),
            HistoryRow(
                key = 4L,
                type = HistoryType.Video,
                mediaId = 903L,
                title = "Drone over the lake",
                subtitle = "",
                artUri = null,
                videoUri = "content://media/external/video/media/903",
                playedAt = at(2026, 10, 14, 17, 26),
            ),
            HistoryRow(
                key = 5L,
                type = HistoryType.Song,
                mediaId = 13L,
                title = "Winter rehearsal, take two",
                subtitle = "Ensemble Aurora",
                artUri = "content://media/external/audio/albumart/13",
                videoUri = null,
                playedAt = at(2025, 10, 3, 15, 0),
            ),
        )

        /** Every request succeeds: square art for songs, a 16:9 frame for videos. */
        @OptIn(ExperimentalCoilApi::class)
        private val previews = AsyncImagePreviewHandler { imageLoader, request ->
            val out = if (request.data is VideoThumbnail) frame else art
            AsyncImagePreviewHandler { out.asImage() }.handle(imageLoader, request)
        }

        private val art: Bitmap by lazy {
            Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.shader = LinearGradient(
                    0f, 0f, 320f, 320f,
                    0xFFB9A8FF.toInt(), 0xFF2E2A5C.toInt(),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, 320f, 320f, paint)
                paint.shader = null
                paint.color = 0xFFFFFFFF.toInt()
                paint.alpha = 200
                canvas.drawCircle(160f, 160f, 60f, paint)
            }
        }

        private val frame: Bitmap by lazy {
            Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.shader = LinearGradient(
                    0f, 0f, 0f, 180f,
                    0xFF8EC5FF.toInt(), 0xFF2B4A6F.toInt(),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, 320f, 180f, paint)
                paint.shader = null
                paint.color = 0xFFFFE3A3.toInt()
                canvas.drawCircle(240f, 60f, 26f, paint)
                paint.color = 0xFF1F3326.toInt()
                canvas.drawRect(0f, 130f, 320f, 180f, paint)
            }
        }
    }
}
