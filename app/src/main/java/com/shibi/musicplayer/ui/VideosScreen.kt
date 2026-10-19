package com.shibi.musicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.ArtTile
import com.shibi.musicplayer.domain.ResumePolicy
import com.shibi.musicplayer.domain.Selection
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.domain.VideoFormat
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.library.VideoLibraryUiState
import com.shibi.musicplayer.library.VideoLibraryViewModel
import com.shibi.musicplayer.library.videoThumbnailRequest
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.background.rememberSurfaceRoles
import com.shibi.musicplayer.ui.background.toComposeColor
import com.shibi.musicplayer.ui.theme.Spacing
import com.shibi.musicplayer.video.VideoEngine
import com.shibi.musicplayer.video.VideoUiState
import kotlin.math.roundToInt

private val ThumbnailShape = RoundedCornerShape(12.dp)

/**
 * The Videos section. The view model (and so the MediaStore query and the permission
 * request) exists only while this destination does: music-only users are never asked.
 * Access is re-checked on every resume; the first visit asks once if access is denied.
 */
@Composable
fun VideosScreen(
    onVideoClick: (Video) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    video: VideoUiState = VideoUiState(),
    /** Saved resume positions by URI: the rows' hairlines and missing durations. */
    savedProgress: Map<String, VideoProgress> = emptyMap(),
    onStartOver: () -> Unit = {},
    videoEngine: VideoEngine? = null,
    onTogglePlayPause: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onCloseVideo: () -> Unit = {},
    onFullscreen: () -> Unit = {},
    onOpenTracks: (() -> Unit)? = null,
    holdControls: Boolean = false,
    // False in two-pane: the video plays in the right pane and the list only marks its row.
    showInlinePlayer: Boolean = true,
    viewModel: VideoLibraryViewModel = viewModel(),
    // Called with the loaded list so stale history and saved positions can be pruned.
    onLibraryLoaded: (List<Video>) -> Unit = {},
    onTrashVideos: (List<Video>, Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.videos, state.access) {
        // Partial access lists only the picked videos: pruning then would drop the rest.
        if (!state.hasError && !state.isLoading && state.access == VideoAccess.Full) {
            onLibraryLoaded(state.videos)
        }
    }
    var checked by remember { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val requestAccess = rememberVideoAccessRequest(onResult = viewModel::refresh)

    // Access granted or revoked in system Settings only shows up when the app resumes.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        checked = true
        onPauseOrDispose { }
    }
    LaunchedEffect(checked) {
        // Read the flow, not the collected state, which can still hold the initial Denied.
        if (checked && !asked && viewModel.uiState.value.access == VideoAccess.Denied) {
            asked = true
            requestAccess()
        }
    }

    VideosContent(
        state = state,
        checked = checked,
        onVideoClick = onVideoClick,
        onRequestAccess = requestAccess,
        onOpenSettings = { openAppSettings(context) },
        contentBottomPadding = contentBottomPadding,
        modifier = modifier,
        playingVideoId = video.video?.id,
        savedProgress = savedProgress,
        onTrashVideos = onTrashVideos,
        player = if (video.isActive && showInlinePlayer) {
            { maxHeight ->
                InlineVideoPlayer(
                    state = video,
                    maxHeight = maxHeight,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeek = onSeek,
                    onClose = onCloseVideo,
                    surface = { videoEngine?.let { engine -> VideoSurface(engine) } },
                    onFullscreen = onFullscreen,
                    onOpenTracks = onOpenTracks,
                    holdControls = holdControls,
                    onStartOver = onStartOver,
                )
            }
        } else {
            null
        },
    )
}

/** Stateless body, so screenshots feed it a fake list and no MediaStore. */
@Composable
internal fun VideosContent(
    state: VideoLibraryUiState,
    onVideoClick: (Video) -> Unit,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    checked: Boolean = true,
    playingVideoId: Long? = null,
    savedProgress: Map<String, VideoProgress> = emptyMap(),
    listState: LazyListState = rememberLazyListState(),
    player: (@Composable (maxHeight: Dp) -> Unit)? = null,
    onTrashVideos: (List<Video>, Boolean) -> Unit = { _, _ -> },
    initialSelection: Selection = Selection(),
) {
    var selection by remember { mutableStateOf(initialSelection) }
    val allIds = remember(state.videos) { state.videos.map { it.id } }
    LaunchedEffect(allIds) { selection = selection.retain(allIds.toSet()) }
    BackHandler(enabled = selection.active) { selection = selection.clear() }
    val selectionBar: (@Composable () -> Unit)? = if (selection.active) {
        {
            SelectionTopBar(
                count = selection.count,
                onSelectAll = { selection = selection.selectAll(allIds) },
                onCancel = { selection = selection.clear() },
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                actions = {
                    IconButton(
                        onClick = {
                            val picked = state.videos.filter { it.id in selection.ids }
                            val whole = selection.isWholeList(allIds)
                            selection = selection.clear()
                            onTrashVideos(picked, whole)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.move_to_trash),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        }
    } else {
        null
    }
    if (player != null) {
        PlayingFrame(
            player = player,
            modifier = modifier,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                selectionBar?.invoke()
                VideoList(
                    state = state,
                    onVideoClick = onVideoClick,
                    contentBottomPadding = contentBottomPadding,
                    playingVideoId = playingVideoId,
                    savedProgress = savedProgress,
                    listState = listState,
                    selection = selection,
                    onSelectionChange = { selection = it },
                    topPadding = 8.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
        return
    }
    val denied = state.access == VideoAccess.Denied
    VideosFrame(
        subtitle = if (!checked || denied) {
            ""
        } else {
            pluralStringResource(R.plurals.video_count, state.videos.size, state.videos.size)
        },
        selectionBar = selectionBar,
        modifier = modifier,
    ) {
        when {
            !checked -> Unit
            denied -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                VideoPermissionPrompt(
                    onGrant = onRequestAccess,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, bottom = contentBottomPadding),
                )
            }
            else -> {
                if (state.access == VideoAccess.Partial) {
                    VideoPartialBanner(
                        onAllowAll = onRequestAccess,
                        onPickMore = onRequestAccess,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    )
                }
                VideoList(
                    state = state,
                    onVideoClick = onVideoClick,
                    contentBottomPadding = contentBottomPadding,
                    playingVideoId = playingVideoId,
                    savedProgress = savedProgress,
                    listState = listState,
                    selection = selection,
                    onSelectionChange = { selection = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun VideoList(
    state: VideoLibraryUiState,
    onVideoClick: (Video) -> Unit,
    contentBottomPadding: Dp,
    playingVideoId: Long?,
    savedProgress: Map<String, VideoProgress>,
    listState: LazyListState,
    selection: Selection,
    onSelectionChange: (Selection) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
) {
    val videos = state.videos
    when {
        videos.isEmpty() && state.isLoading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        videos.isEmpty() && state.hasError -> VideosMessage(
            icon = Icons.Rounded.VideoLibrary,
            message = stringResource(R.string.video_library_error),
            modifier = modifier.padding(bottom = contentBottomPadding),
        )
        videos.isEmpty() -> VideosMessage(
            icon = Icons.Rounded.VideoLibrary,
            message = stringResource(R.string.videos_empty),
            modifier = modifier.padding(bottom = contentBottomPadding),
        )
        else -> LazyColumn(
            modifier = modifier,
            state = listState,
            contentPadding = PaddingValues(top = topPadding, bottom = contentBottomPadding),
        ) {
            items(videos, key = { it.id }, contentType = { "video" }) { video ->
                VideoRow(
                    video = video,
                    isPlaying = video.id == playingVideoId,
                    saved = savedProgress[video.contentUri],
                    onClick = {
                        // While selecting, a tap toggles and never opens the video.
                        if (selection.active) {
                            onSelectionChange(selection.toggle(video.id))
                        } else {
                            onVideoClick(video)
                        }
                    },
                    modifier = Modifier.animateItem(),
                    selectionMode = selection.active,
                    selected = video.id in selection.ids,
                    onLongClick = {
                        onSelectionChange(
                            if (selection.active) selection.toggle(video.id) else selection.start(video.id),
                        )
                    },
                )
            }
        }
    }
}

/**
 * The `TrackRow` pill with a 16:9 thumbnail; text colours come from the row's own fill. A
 * partly watched video ([saved] would resume) gets a hairline under its thumbnail, drawn in
 * the row's own text colour, so it reads on the dark row and on every pastel one.
 */
@Composable
internal fun VideoRow(
    video: Video,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    saved: VideoProgress? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val chrome = LocalBackgroundChrome.current
    val fill by animateColorAsState(
        if (isPlaying || selected) chrome.playingRowFill else chrome.rowFill,
        highlightSpec(),
        label = "videoRowFill",
    )
    val roles = rememberSurfaceRoles(fill)
    val watched = ResumePolicy.watchedFraction(saved, video.durationMs)
    val watchedText = watched?.let { stringResource(R.string.video_watched, (it * 100).roundToInt()) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            VideoThumbnail(
                video = video,
                durationMs = ResumePolicy.displayDuration(video.durationMs, saved),
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .then(
                        if (watched != null && watchedText != null) {
                            Modifier.watchedHairline(watched, roles.onSurfaceArgb.toComposeColor(), watchedText)
                        } else {
                            Modifier
                        },
                    ),
            )
            if (selectionMode) {
                SelectionCheck(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-4).dp, y = (-4).dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.md)
                .clipToBounds(),
        ) {
            TrackTitle(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = roles.onSurfaceArgb.toComposeColor(),
                modifier = Modifier.fillMaxWidth(),
            )
            val subtitle = videoSubtitle(video)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = roles.onVariantArgb.toComposeColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (isPlaying) {
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = chrome.accentText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** `1.4 GB · 1080p · Camera`; values MediaStore could not read are left out, not dashed. */
internal fun videoSubtitle(video: Video): String = listOfNotNull(
    VideoFormat.size(video.sizeBytes).takeIf { it != VideoFormat.MISSING },
    VideoFormat.resolution(video.width, video.height).takeIf { it != VideoFormat.MISSING },
    video.folder,
).joinToString(" · ")

/**
 * A 3dp bar in the row's bottom padding, 3dp under the thumbnail: the watched part in
 * [color], the rest in [color] at 25%. It sits outside the thumbnail's clip, so the row's
 * height is the same with or without it.
 */
private fun Modifier.watchedHairline(fraction: Float, color: Color, description: String): Modifier =
    semantics { stateDescription = description }
        .drawBehind {
            val gap = 3.dp.toPx()
            val thickness = 3.dp.toPx()
            val top = size.height + gap
            val radius = CornerRadius(thickness / 2f)
            drawRoundRect(
                color = color.copy(alpha = 0.25f),
                topLeft = Offset(0f, top),
                size = Size(size.width, thickness),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, top),
                size = Size((size.width * fraction).coerceAtLeast(thickness), thickness),
                cornerRadius = radius,
            )
        }

@Composable
private fun VideoThumbnail(video: Video, durationMs: Long?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The request carries its own fetcher factory, so build it once per video.
    val request = remember(video.contentUri) { videoThumbnailRequest(context, video.contentUri) }
    Box(modifier = modifier.clip(ThumbnailShape)) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        ) {
            val imageState by painter.state.collectAsState()
            if (imageState is AsyncImagePainter.State.Success) {
                SubcomposeAsyncImageContent()
            } else {
                FilmTile(title = video.title, modifier = Modifier.fillMaxSize())
            }
        }
        DurationBadge(
            text = VideoFormat.duration(durationMs),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
        )
    }
}

/** `GeneratedArtTile`'s gradient with a film glyph, for WMV and anything else with no frame. */
@Composable
private fun FilmTile(title: String, modifier: Modifier = Modifier) {
    val spec = remember(title) { ArtTile.specFor(title) }
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(Color(spec.startArgb), Color(spec.endArgb)),
                start = Offset.Zero,
                end = Offset.Infinite,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset.Zero,
                    ),
                ),
        )
        Icon(
            imageVector = Icons.Rounded.Movie,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Fixed dark scrim and white text, so it reads the same over any frame or backdrop. */
@Composable
private fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun VideosFrame(
    subtitle: String,
    modifier: Modifier = Modifier,
    selectionBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
            ),
    ) {
        if (selectionBar != null) {
            Box(modifier = Modifier.statusBarsPadding()) { selectionBar() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.videos_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        content()
    }
}

/**
 * A video is open: the player box sits at the top as a column above the list (never over
 * it), and the list scrolls in its own viewport below, fading into the chrome.
 */
@Composable
private fun PlayingFrame(
    player: @Composable (maxHeight: Dp) -> Unit,
    modifier: Modifier = Modifier,
    list: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                ),
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            player(viewport)
            ListViewport(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                list()
            }
        }
    }
}

@Composable
private fun VideosMessage(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
