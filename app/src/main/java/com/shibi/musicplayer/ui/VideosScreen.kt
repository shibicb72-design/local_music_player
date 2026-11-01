package com.shibi.musicplayer.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.ResumePolicy
import com.shibi.musicplayer.domain.Selection
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.domain.VideoFormat
import com.shibi.musicplayer.domain.VideoListEntry
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.domain.VideoTimeline
import com.shibi.musicplayer.library.MediaTrash
import com.shibi.musicplayer.library.VideoLibraryUiState
import com.shibi.musicplayer.library.VideoLibraryViewModel
import com.shibi.musicplayer.library.videoThumbnailRequest
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.background.rememberSurfaceRoles
import com.shibi.musicplayer.ui.background.toComposeColor
import com.shibi.musicplayer.video.VideoEngine
import com.shibi.musicplayer.video.VideoUiState
import java.time.ZoneId
import kotlin.math.roundToInt

/** The film glyph on a [ListThumbnailSize] tile with no frame. */
private val FilmGlyphSize = 20.dp

/**
 * The Videos section. The view model (and so the MediaStore query and the permission
 * request) exists only while this destination does: music-only users are never asked.
 * Access is re-checked on every resume; the first visit asks once if access is denied.
 */
@Composable
internal fun VideosScreen(
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
    videoExtras: PlayerExtras = NoPlayerExtras,
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
    // The length sort uses the length a row shows, which can be libVLC's saved one.
    LaunchedEffect(savedProgress) { viewModel.setSavedProgress(savedProgress) }
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
        onSortChange = viewModel::setSort,
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
                    extras = videoExtras,
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
    /** Tests only: starts the fast-scroll thumb mid-drag at this fraction. */
    fastScrollDragFraction: Float? = null,
    /** The user picked a new sort; [state] comes back sorted by it. */
    onSortChange: (SortSpec) -> Unit = {},
    /** Screenshots only: the sort menu starts open. */
    initialSortMenuOpen: Boolean = false,
    /** Whether rows get a ⋮ with Move to trash: the system trash needs Android 11. */
    canTrash: Boolean = MediaTrash.isSupported(),
) {
    // Set when the user picks a sort: the list goes to the top once it shows that sort. It is
    // not saved, so a return from fullscreen (which recomposes this) leaves the list where it was.
    var jumpToTopFor by remember { mutableStateOf<SortSpec?>(null) }
    if (jumpToTopFor != null && jumpToTopFor == state.sort) {
        SideEffect {
            // Before the new order's first layout, so the list never shows the old position.
            listState.requestScrollToItem(0)
            jumpToTopFor = null
        }
    }
    val pickSort: (SortSpec) -> Unit = { spec ->
        jumpToTopFor = spec
        onSortChange(spec)
    }
    var selection by remember { mutableStateOf(initialSelection) }
    val allIds = remember(state.videos) { state.videos.map { it.id } }
    val trashVideo: ((Video) -> Unit)? = if (canTrash) {
        { video -> onTrashVideos(listOf(video), allIds == listOf(video.id)) }
    } else {
        null
    }
    LaunchedEffect(allIds) { selection = selection.retain(allIds.toSet()) }
    AppBackHandler(enabled = selection.active) { selection = selection.clear() }
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
                    onTrashVideo = trashVideo,
                    contentBottomPadding = contentBottomPadding,
                    playingVideoId = playingVideoId,
                    savedProgress = savedProgress,
                    listState = listState,
                    selection = selection,
                    onSelectionChange = { selection = it },
                    topPadding = 8.dp,
                    fastScrollDragFraction = fastScrollDragFraction,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
        return
    }
    val denied = state.access == VideoAccess.Denied
    // Fullscreen is its own route, so this screen leaves composition and `checked` starts
    // false again on the way back. Navigation stays below RESUMED until the pop transition
    // ends, which is the blank gap under the title. A list already loaded stays up through
    // that gap and through a refresh; only a first visit with nothing loaded stays blank.
    val showLibrary = state.videos.isNotEmpty() || (checked && !denied)
    val count = pluralStringResource(R.plurals.video_count, state.videos.size, state.videos.size)
    VideosFrame(
        subtitle = when {
            !showLibrary -> ""
            state.videos.isEmpty() -> count
            else -> sortSubtitle(count, state.sort)
        },
        selectionBar = selectionBar,
        modifier = modifier,
        // Hidden with the title row while selecting, and with nothing to sort.
        sortButton = if (state.videos.isNotEmpty()) {
            {
                SortButton(
                    sort = state.sort,
                    options = LibrarySorts.VideoOptions,
                    onSortChange = pickSort,
                    initialOpen = initialSortMenuOpen,
                )
            }
        } else {
            null
        },
    ) {
        when {
            showLibrary -> {
                if (state.access == VideoAccess.Partial) {
                    VideoPartialBanner(
                        onAllowAll = onRequestAccess,
                        onPickMore = onRequestAccess,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    )
                }
                if (state.videos.isNotEmpty()) {
                    ListPlayAll(
                        // Videos never auto-advance (see MusicApp), so this opens the first in the sort.
                        onPlayAll = { onVideoClick(state.videos.first()) },
                        enabled = !selection.active,
                    )
                }
                VideoList(
                    state = state,
                    onVideoClick = onVideoClick,
                    onTrashVideo = trashVideo,
                    contentBottomPadding = contentBottomPadding,
                    playingVideoId = playingVideoId,
                    savedProgress = savedProgress,
                    listState = listState,
                    selection = selection,
                    onSelectionChange = { selection = it },
                    fastScrollDragFraction = fastScrollDragFraction,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
            !checked -> Unit
            else -> Box(
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
        }
    }
}

@Composable
private fun VideoList(
    state: VideoLibraryUiState,
    onVideoClick: (Video) -> Unit,
    /** A row's ⋮ Move to trash; null gives the rows no ⋮. */
    onTrashVideo: ((Video) -> Unit)?,
    contentBottomPadding: Dp,
    playingVideoId: Long?,
    savedProgress: Map<String, VideoProgress>,
    listState: LazyListState,
    selection: Selection,
    onSelectionChange: (Selection) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    fastScrollDragFraction: Float? = null,
) {
    val videos = state.videos
    val reduceMotion = rememberReduceMotion()
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
        else -> {
            val zone = remember { ZoneId.systemDefault() }
            val sort = state.sort
            // One place builds the rows, the headers and (in the scroller) the stops, from the
            // sort's groups: one header per group, so no key repeats. Nothing sorts here; the
            // view model sorted off the main thread.
            val layout = remember(videos, state.runs) { VideoTimeline.layout(videos, state.runs) }
            val names = rememberSortGroupNames(music = false)
            Box(modifier = modifier) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(top = topPadding, bottom = contentBottomPadding),
                ) {
                    layout.entries.forEach { entry ->
                        when (entry) {
                            is VideoListEntry.Header -> stickyHeader(
                                key = entry.key,
                                contentType = entry.contentType,
                            ) {
                                MonthHeader(text = names.name(entry.section.group, sort.field))
                            }
                            is VideoListEntry.Row -> {
                                val video = entry.video
                                item(key = video.id, contentType = "video") {
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
                                        modifier = if (reduceMotion) {
                                            Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                                        } else {
                                            Modifier.animateItem()
                                        },
                                        selectionMode = selection.active,
                                        selected = video.id in selection.ids,
                                        onLongClick = {
                                            onSelectionChange(
                                                if (selection.active) {
                                                    selection.toggle(video.id)
                                                } else {
                                                    selection.start(video.id)
                                                },
                                            )
                                        },
                                        onTrash = onTrashVideo?.let { trash -> { trash(video) } },
                                    )
                                }
                            }
                        }
                    }
                }
                VideoFastScroller(
                    listState = listState,
                    layout = layout,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = topPadding, bottom = contentBottomPadding),
                    zone = zone,
                    initialDragFraction = fastScrollDragFraction,
                    sort = sort,
                )
            }
        }
    }
}

/** Opaque page fill, so rows sliding under a stuck month header never show through. */
@Composable
private fun MonthHeader(text: String, modifier: Modifier = Modifier) {
    val chrome = LocalBackgroundChrome.current
    val roles = rememberSurfaceRoles(chrome.pageFill)
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .background(chrome.pageFill)
            // On the rows' side margin, so the label lines up with the art below it.
            .padding(start = ListRowSideMargin, top = 12.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = roles.onSurfaceArgb.toComposeColor(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A Videos list row ([ListRow]): a square frame, the title over the length and details, then
 * the ⋮ menu with move to trash ([onTrash]; null leaves the row without a ⋮). A partly watched
 * video ([saved] would resume) gets a hairline under its frame, drawn in the row's own text
 * colour, so it reads on the dark row and on every pastel one.
 */
@Composable
internal fun VideoRow(
    video: Video,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onTrash: (() -> Unit)?,
    modifier: Modifier = Modifier,
    saved: VideoProgress? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val chrome = LocalBackgroundChrome.current
    val fill = if (isPlaying || selected) chrome.playingRowFill else chrome.rowFill
    val roles = rememberSurfaceRoles(fill)
    val watched = ResumePolicy.watchedFraction(saved, video.durationMs)
    val watchedText = watched?.let { stringResource(R.string.video_watched, (it * 100).roundToInt()) }
    // MediaStore often has no length for MKV/WMV; libVLC's from an earlier play is saved.
    val length = ResumePolicy.displayDuration(video.durationMs, saved) ?: 0L
    ListRow(
        title = video.title,
        detail = videoDetails(length, videoSubtitle(video)).ifEmpty { null },
        fill = fill,
        isPlaying = isPlaying,
        onClick = onClick,
        onLongClick = onLongClick,
        art = {
            VideoThumbnail(
                video = video,
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .then(
                        if (watched != null && watchedText != null) {
                            Modifier.watchedHairline(watched, roles.onSurfaceArgb.toComposeColor(), watchedText)
                        } else {
                            Modifier
                        },
                    ),
            )
        },
        modifier = modifier,
        selectionMode = selectionMode,
        selected = selected,
        actions = onTrash?.let { trash ->
            { tint ->
                RowOverflowMenu(tint = tint) { dismiss ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_to_trash)) },
                        onClick = {
                            dismiss()
                            trash()
                        },
                    )
                }
            }
        },
    )
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

/** The row's frame, cropped square; the length is on the row's second line. */
@Composable
private fun VideoThumbnail(video: Video, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The request carries its own fetcher factory, so build it once per video.
    val request = remember(video.contentUri) { videoThumbnailRequest(context, video.contentUri) }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier.clip(ListThumbnailShape),
        contentScale = ContentScale.Crop,
    ) {
        val imageState by painter.state.collectAsState()
        if (imageState is AsyncImagePainter.State.Success) {
            SubcomposeAsyncImageContent()
        } else {
            FilmTile(title = video.title, modifier = Modifier.fillMaxSize())
        }
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
            modifier = Modifier.size(FilmGlyphSize),
        )
    }
}

@Composable
private fun VideosFrame(
    subtitle: String,
    modifier: Modifier = Modifier,
    selectionBar: (@Composable () -> Unit)? = null,
    sortButton: (@Composable () -> Unit)? = null,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    // The icon button's own 12 dp inset lines its glyph up with the 20 dp margin.
                    .padding(start = 20.dp, end = if (sortButton != null) 8.dp else 20.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                sortButton?.invoke()
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
