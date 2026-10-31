package com.shibi.musicplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.musicplayer.CarouselV2ScreenshotTest.Companion.playingState
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.Selection
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoTimeline
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * V22 review set: month headers and the week fast-scroll bubble on the folded and
 * unfolded layouts, headers with a selection active, and headers on a pastel backdrop;
 * plus the busy-week dots on the track, folded and unfolded.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class VideoTimelineScreenshotTest {

    @Test
    fun headersFolded() = capture("cv22_headers_folded.png", expanded = false)

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun headersUnfolded() = capture("cv22_headers_unfolded.png", expanded = true)

    @Test
    fun bubbleFolded() = capture("cv22_bubble_folded.png", expanded = false, dragFraction = 0.5f)

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun bubbleUnfolded() = capture("cv22_bubble_unfolded.png", expanded = true, dragFraction = 0.5f)

    @Test
    fun headersSelectionFolded() = capture(
        "cv22_headers_selection_folded.png",
        expanded = false,
        selection = Selection().start(VideoTimelineScreenshotTest.videos[1].id).toggle(VideoTimelineScreenshotTest.videos[3].id),
    )

    @Test
    fun headersPastelFolded() = capture(
        "cv22_headers_pastel_folded.png",
        expanded = false,
        background = AppBackground.Solid("blush"),
        dragFraction = 0.3f,
    )

    @Test
    fun weekDotsFolded() = capture(
        "cv24_week_dots_folded.png",
        expanded = false,
        dragFraction = busyDragFraction,
        videos = busyVideos,
    )

    @Test
    @Config(qualifiers = "w900dp-h600dp-xhdpi")
    fun weekDotsUnfolded() = capture(
        "cv24_week_dots_unfolded.png",
        expanded = true,
        dragFraction = busyDragFraction,
        videos = busyVideos,
    )

    private fun capture(
        fileName: String,
        expanded: Boolean,
        background: AppBackground = AppBackground.Default,
        dragFraction: Float? = null,
        selection: Selection = Selection(),
        videos: List<Video> = VideoTimelineScreenshotTest.videos,
    ) {
        captureRoboImage("build/outputs/roborazzi/$fileName") {
            MusicPlayerTheme {
                AppBackgroundHost(background, emptyMap(), forceStatic = true) {
                    if (expanded) {
                        TwoPane(dragFraction, selection, videos)
                    } else {
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
                            Videos(bottom, dragFraction, selection, videos)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TwoPane(dragFraction: Float?, selection: Selection, videos: List<Video>) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                ListViewport(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                    Videos(SongListBottomPadding, dragFraction, selection, videos)
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

    @OptIn(ExperimentalCoilApi::class)
    @Composable
    private fun Videos(bottom: Dp, dragFraction: Float?, selection: Selection, videos: List<Video>) {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides VideosScreenshotTest.thumbnails,
        ) {
            VideosContent(
                state = VideosScreenshotTest.sortedState(videos),
                onVideoClick = {},
                onRequestAccess = {},
                onOpenSettings = {},
                contentBottomPadding = bottom,
                modifier = Modifier.fillMaxSize(),
                initialSelection = selection,
                fastScrollDragFraction = dragFraction,
                // Where a real drag to this fraction leaves the list: the entry under the thumb.
                listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = dragFraction?.let {
                        VideoTimeline.entryIndexForFraction(it, VideoTimeline.layout(videos, ZoneId.systemDefault()).entries.size)
                    } ?: 0,
                ),
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
        private fun takenAt(year: Int, month: Int, day: Int): Long =
            LocalDateTime.of(year, month, day, 12, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

        /** About 30 videos over 2026, 2025 and 2024, plus one dated only by DATE_ADDED and one undated. */
        val videos: List<Video> = run {
            val months = listOf(
                2026 to 8, 2026 to 7, 2026 to 5,
                2025 to 12, 2025 to 10, 2025 to 6,
                2024 to 11, 2024 to 8, 2024 to 3,
            )
            var id = 100L
            val dated = months.flatMap { (year, month) ->
                (1..3).map { day ->
                    id++
                    VideosScreenshotTest.video(
                        id,
                        "Clip $year-$month-$day",
                        60_000L * day,
                        50_000_000L * day,
                        1920,
                        1080,
                        "Camera",
                    ).copy(dateTakenMs = takenAt(year, month, day * 7))
                }
            }
            val addedOnly = VideosScreenshotTest.video(id++, "Added only", 30_000L, 9_000_000L, 1280, 720, "Download")
                .copy(dateTakenMs = 0L, dateAddedSeconds = 1_700_000_000L)
            val undated = VideosScreenshotTest.video(id, "No date at all", 12_000L, 4_000_000L, 640, 360, null)
                .copy(dateTakenMs = 0L, dateAddedSeconds = 0L)
            VideoTimeline.sortNewestFirst(dated + addedOnly + undated)
        }
        val state = VideosScreenshotTest.sortedState(videos)

        /** [videos] plus two weeks busy enough for a fast-scroll dot, in October and June 2025. */
        val busyVideos: List<Video> = run {
            var id = 500L
            fun week(year: Int, month: Int, day: Int, count: Int) = (1..count).map { n ->
                VideosScreenshotTest.video(id++, "Busy $year-$month-$day #$n", 20_000L, 8_000_000L, 1920, 1080, "Camera")
                    .copy(dateTakenMs = takenAt(year, month, day))
            }
            VideoTimeline.sortNewestFirst(videos + week(2025, 10, 15, 30) + week(2025, 6, 18, 26))
        }

        /** Just past the first busy week's dot, so the thumb sits on it and the bubble names that week. */
        val busyDragFraction: Float = run {
            val zone = ZoneId.systemDefault()
            val layout = VideoTimeline.layout(busyVideos, zone)
            val stops = VideoTimeline.weekStops(VideoTimeline.weeks(layout, zone, Locale.getDefault()))
            VideoTimeline.dotFractions(stops, layout.entries.size).first() + 0.005f
        }
    }
}
