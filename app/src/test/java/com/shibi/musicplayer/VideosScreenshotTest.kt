package com.shibi.musicplayer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.request.ErrorResult
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.playingState
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.library.VideoLibraryUiState
import com.shibi.musicplayer.library.VideoThumbnail
import com.shibi.musicplayer.ui.ChromeSlot
import com.shibi.musicplayer.ui.ListViewport
import com.shibi.musicplayer.ui.MiniPlayerCarouselGap
import com.shibi.musicplayer.ui.NavCarousel
import com.shibi.musicplayer.ui.NavSection
import com.shibi.musicplayer.ui.NowPlayingBar
import com.shibi.musicplayer.ui.PlayerScreen
import com.shibi.musicplayer.ui.SectionRoutes
import com.shibi.musicplayer.ui.SectionScaffold
import com.shibi.musicplayer.ui.SongListBottomPadding
import com.shibi.musicplayer.ui.VideosContent
import com.shibi.musicplayer.ui.background.AppBackgroundHost
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * V3 review set: the Videos list on the 9-tile compact ring and the 8-tile two-pane ring,
 * over the dark default and two pastels, plus the partial, denied and empty states.
 * The list is a fake; no MediaStore. Even ids get a frame, odd ids the film-glyph tile.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class VideosScreenshotTest {

    @Test
    fun compactDark() = captureCompact(AppBackground.Default, full, "cv5_videos_compact_dark.png")

    @Test
    fun compactSky() = captureCompact(AppBackground.Solid("sky"), full, "cv5_videos_compact_sky.png")

    @Test
    fun compactBlush() = captureCompact(AppBackground.Solid("blush"), full, "cv5_videos_compact_blush.png")

    /** Cover-screen height: rows still end in the fade above the chrome. */
    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun compactShortDark() =
        captureCompact(AppBackground.Default, full, "cv5_videos_compact_short_dark.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun twoPaneDark() = captureTwoPane(AppBackground.Default, "cv5_videos_two_pane_dark.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun twoPaneSky() = captureTwoPane(AppBackground.Solid("sky"), "cv5_videos_two_pane_sky.png")

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun twoPaneBlush() = captureTwoPane(AppBackground.Solid("blush"), "cv5_videos_two_pane_blush.png")

    @Test
    fun partial() = captureCompact(
        AppBackground.Solid("blush"),
        VideoLibraryUiState(access = VideoAccess.Partial, videos = videos.take(2)),
        "videos_partial.png",
    )

    @Test
    fun denied() = captureCompact(
        AppBackground.Default,
        VideoLibraryUiState(access = VideoAccess.Denied),
        "videos_denied.png",
    )

    @Test
    fun empty() = captureCompact(
        AppBackground.Solid("sky"),
        VideoLibraryUiState(access = VideoAccess.Full),
        "videos_empty.png",
    )

    private fun captureCompact(background: AppBackground, state: VideoLibraryUiState, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    SectionScaffold(
                        modifier = Modifier.fillMaxSize(),
                        showMiniPlayer = true,
                        showCarousel = true,
                        contentBottomPadding = SongListBottomPadding,
                        miniPlayer = { MiniBar() },
                        carousel = {
                            NavCarousel(
                                selected = NavSection.Videos,
                                onSelect = {},
                                sections = SectionRoutes.sectionsFor(expanded = false),
                            )
                        },
                    ) { bottom ->
                        Videos(state, bottom)
                    }
                }
            }
        }
    }

    private fun captureTwoPane(background: AppBackground, fileName: String) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                            ListViewport(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                                Videos(full, SongListBottomPadding)
                            }
                            Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
                                PlayerScreen(
                                    state = playingState,
                                    onBack = {},
                                    onTogglePlayPause = {},
                                    onNext = {},
                                    onPrevious = {},
                                    onSeek = {},
                                    selectedOutputId = null,
                                    onSelectOutput = {},
                                    showBack = false,
                                    includeNavigationBars = false,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(start = 8.dp, top = 16.dp, end = 16.dp, bottom = SongListBottomPadding)
                                        .clip(RoundedCornerShape(28.dp)),
                                )
                            }
                        }
                        ChromeSlot {
                            MiniBar()
                            NavCarousel(
                                selected = NavSection.Videos,
                                onSelect = {},
                                sections = SectionRoutes.sectionsFor(expanded = true),
                            )
                        }
                    }
                }
            }
        }
    }

    /** Inspection mode only around the list, so the preview handler feeds the thumbnails. */
    @OptIn(ExperimentalCoilApi::class)
    @Composable
    private fun Videos(state: VideoLibraryUiState, bottom: Dp) {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides thumbnails,
        ) {
            VideosContent(
                state = state,
                onVideoClick = {},
                onRequestAccess = {},
                onOpenSettings = {},
                contentBottomPadding = bottom,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Composable
    private fun MiniBar() {
        NowPlayingBar(
            state = playingState,
            onOpen = {},
            onTogglePlayPause = {},
            route = RoutePresentation.phone("This phone"),
            padForNavigationBars = false,
            bottomPadding = MiniPlayerCarouselGap,
        )
    }

    internal companion object {
        val videos = listOf(
            video(1L, "Beach day", 754_000L, 1_412_000_000L, 1920, 1080, "Camera"),
            video(2L, "Concert encore", 3_723_000L, 4_800_000_000L, 3840, 2160, "Movies"),
            video(3L, "Old holiday tape", null, 812_000_000L, 720, 576, "Archive", "video/x-ms-wmv"),
            video(4L, "Lecture 12: Signals and systems, the long recorded version", 5_400_000L, 2_100_000_000L, 1280, 720, "Download"),
            video(5L, "Screen recording", 42_000L, 18_400_000L, 1080, 2340, "Screen recordings"),
            video(6L, "Birthday", 186_000L, 356_000_000L, 1920, 1080, null),
            video(7L, "Drone over the lake", 312_000L, 1_020_000_000L, 2560, 1440, "DCIM"),
            video(8L, "Clip", 9_000L, null, null, null, "WhatsApp Video"),
            video(9L, "Recital", 2_640_000L, 3_300_000_000L, 1920, 1080, "Movies"),
        )
        val full = VideoLibraryUiState(access = VideoAccess.Full, videos = videos)

        /** Even ids load a frame; odd ids fail like a WMV with no thumbnail. */
        @OptIn(ExperimentalCoilApi::class)
        val thumbnails = AsyncImagePreviewHandler { imageLoader, request ->
            val id = (request.data as? VideoThumbnail)?.uri?.lastPathSegment?.toLongOrNull()
            if (id != null && id % 2 == 0L) {
                AsyncImagePreviewHandler { frame.asImage() }.handle(imageLoader, request)
            } else {
                AsyncImagePainter.State.Error(null, ErrorResult(null, request, IllegalStateException("no frame")))
            }
        }

        val frame: Bitmap by lazy {
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

        fun video(
            id: Long,
            title: String,
            durationMs: Long?,
            sizeBytes: Long?,
            width: Int?,
            height: Int?,
            folder: String?,
            mimeType: String = "video/mp4",
        ) = Video(
            id = id,
            title = title,
            displayName = "$title.mp4",
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            mimeType = mimeType,
            dateAddedSeconds = 1_760_000_000L,
            folder = folder,
            contentUri = "content://media/external/video/media/$id",
        )
    }
}
