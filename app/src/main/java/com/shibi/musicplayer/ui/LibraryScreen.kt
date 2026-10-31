package com.shibi.musicplayer.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.PlaybackTime
import com.shibi.musicplayer.domain.RouteIcon
import com.shibi.musicplayer.domain.Selection
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.ScrollSection
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortGroup
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.SortedList
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.TrackDisplay
import com.shibi.musicplayer.domain.VideoTimeline
import com.shibi.musicplayer.domain.section
import com.shibi.musicplayer.library.LibraryViewModel
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.background.ContrastSurface
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.theme.DeviceGreen
import com.shibi.musicplayer.ui.theme.Secondary
import com.shibi.musicplayer.ui.theme.Spacing
import java.time.ZoneId
import java.util.Locale

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    nowPlaying: PlayerUiState,
    onTrackSelected: (List<Track>, Long) -> Unit,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onAddToPlaylist: (Track) -> Unit = {},
    favoriteIds: Set<Long> = emptySet(),
    onToggleFavorite: (Track) -> Unit = {},
    onTrashSongs: (List<Track>, Boolean) -> Unit = { _, _ -> },
    showMiniPlayer: Boolean = true,
    contentBottomPadding: Dp = 120.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The Songs list in its own sort; Favorites, Search and Recent keep the library order.
    val songs = state.songs
    val audioPermission = remember { audioReadPermission() }
    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[audioPermission] == true
        if (granted && !audioGranted) viewModel.refresh()
        audioGranted = granted
    }

    LaunchedEffect(Unit) {
        if (audioGranted) {
            viewModel.refresh()
        } else {
            permissionLauncher.launch(startupPermissions())
        }
    }

    // Access granted from system Settings only shows up when the app resumes.
    LifecycleResumeEffect(audioPermission) {
        val granted = ContextCompat.checkSelfPermission(context, audioPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted && !audioGranted) viewModel.refresh()
        audioGranted = granted
        onPauseOrDispose { }
    }

    when {
        !audioGranted -> LibraryScaffold(
            nowPlaying = nowPlaying,
            showMiniPlayer = showMiniPlayer,
            onOpenPlayer = onOpenPlayer,
            onTogglePlayPause = onTogglePlayPause,
            modifier = modifier,
        ) { padding ->
            PermissionPrompt(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                onGrant = { permissionLauncher.launch(startupPermissions()) },
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${context.packageName}".toUri(),
                    )
                    context.startActivity(intent)
                },
            )
        }
        state.isLoading && state.tracks.isEmpty() -> LibraryScaffold(
            nowPlaying = nowPlaying,
            showMiniPlayer = showMiniPlayer,
            onOpenPlayer = onOpenPlayer,
            onTogglePlayPause = onTogglePlayPause,
            modifier = modifier,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }
        state.errorMessage != null && state.tracks.isEmpty() -> LibraryScaffold(
            nowPlaying = nowPlaying,
            showMiniPlayer = showMiniPlayer,
            onOpenPlayer = onOpenPlayer,
            onTogglePlayPause = onTogglePlayPause,
            modifier = modifier,
        ) { padding ->
            MessagePane(
                message = state.errorMessage ?: stringResource(R.string.library_error),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            )
        }
        state.tracks.isEmpty() -> LibraryScaffold(
            nowPlaying = nowPlaying,
            showMiniPlayer = showMiniPlayer,
            onOpenPlayer = onOpenPlayer,
            onTogglePlayPause = onTogglePlayPause,
            modifier = modifier,
        ) { padding ->
            MessagePane(
                message = stringResource(R.string.empty_library),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            )
        }
        // Loaded but not yet in the stored sort: wait, rather than flash the library order.
        songs == null || songs.items.isEmpty() -> LibraryScaffold(
            nowPlaying = nowPlaying,
            showMiniPlayer = showMiniPlayer,
            onOpenPlayer = onOpenPlayer,
            onTogglePlayPause = onTogglePlayPause,
            modifier = modifier,
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        else -> LibraryContent(
            tracks = songs.items,
            sorted = songs,
            onSortChange = viewModel::setSort,
            nowPlaying = nowPlaying,
            showMiniPlayer = showMiniPlayer,
            onTrackSelected = onTrackSelected,
            onOpenPlayer = onOpenPlayer,
            onTogglePlayPause = onTogglePlayPause,
            onAddToPlaylist = onAddToPlaylist,
            favoriteIds = favoriteIds,
            onToggleFavorite = onToggleFavorite,
            onTrashSongs = onTrashSongs,
            contentBottomPadding = contentBottomPadding,
            modifier = modifier,
        )
    }
}

@Composable
fun LibraryContent(
    tracks: List<Track>,
    nowPlaying: PlayerUiState,
    showMiniPlayer: Boolean,
    onTrackSelected: (List<Track>, Long) -> Unit,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onAddToPlaylist: (Track) -> Unit = {},
    favoriteIds: Set<Long> = emptySet(),
    onToggleFavorite: (Track) -> Unit = {},
    onTrashSongs: (List<Track>, Boolean) -> Unit = { _, _ -> },
    contentBottomPadding: Dp = 120.dp,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    initialSelection: Selection = Selection(),
    /**
     * [tracks] with their sort and groups: shows the sort menu, the sort in the subtitle and the
     * fast scroller. Null keeps the plain list.
     */
    sorted: SortedList<Track>? = null,
    /** The user picked a new sort; [sorted] comes back in it. */
    onSortChange: (SortSpec) -> Unit = {},
    /** Screenshots only: the sort menu starts open. */
    initialSortMenuOpen: Boolean = false,
    /** Tests only: starts the fast-scroll thumb mid-drag at this fraction. */
    fastScrollDragFraction: Float? = null,
) {
    // Set when the user picks a sort: the list goes to the top once it shows that sort.
    var jumpToTopFor by remember { mutableStateOf<SortSpec?>(null) }
    if (jumpToTopFor != null && jumpToTopFor == sorted?.spec) {
        SideEffect {
            // Before the new order's first layout, so the list never shows the old position.
            listState.requestScrollToItem(0)
            jumpToTopFor = null
        }
    }
    var selection by remember { mutableStateOf(initialSelection) }
    val allIds = remember(tracks) { tracks.map { it.id } }
    LaunchedEffect(allIds) { selection = selection.retain(allIds.toSet()) }
    AppBackHandler(enabled = selection.active) { selection = selection.clear() }
    LibraryScaffold(
        nowPlaying = nowPlaying,
        showMiniPlayer = showMiniPlayer,
        onOpenPlayer = onOpenPlayer,
        onTogglePlayPause = onTogglePlayPause,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                ),
        ) {
            if (selection.active) {
                SelectionTopBar(
                    count = selection.count,
                    onSelectAll = { selection = selection.selectAll(allIds) },
                    onCancel = { selection = selection.clear() },
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    actions = {
                        IconButton(
                            onClick = {
                                val picked = tracks.filter { it.id in selection.ids }
                                val whole = selection.isWholeList(allIds)
                                selection = selection.clear()
                                onTrashSongs(picked, whole)
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
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        // The icon button's own 12 dp inset lines its glyph up with the 20 dp margin.
                        .padding(start = 20.dp, end = if (sorted != null) 8.dp else 20.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.library_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        val count = pluralStringResource(
                            R.plurals.song_count,
                            tracks.size,
                            tracks.size,
                        )
                        Text(
                            text = if (sorted != null) sortSubtitle(count, sorted.spec) else count,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (sorted != null) {
                        SortButton(
                            sort = sorted.spec,
                            options = LibrarySorts.MusicOptions,
                            onSortChange = { spec ->
                                jumpToTopFor = spec
                                onSortChange(spec)
                            },
                            initialOpen = initialSortMenuOpen,
                        )
                    }
                }
            }
            if (tracks.isNotEmpty()) {
                ListPlayAll(
                    // The queue is the list as shown, from its first song.
                    onPlayAll = { onTrackSelected(tracks, tracks.first().id) },
                    enabled = !selection.active,
                )
            }
            SongColumn(
                tracks = tracks,
                nowPlaying = nowPlaying,
                favoriteIds = favoriteIds,
                onTrackSelected = onTrackSelected,
                onToggleFavorite = onToggleFavorite,
                onAddToPlaylist = onAddToPlaylist,
                contentBottomPadding = contentBottomPadding,
                emptyMessage = stringResource(R.string.empty_library),
                listState = listState,
                selection = selection,
                onSelectionChange = { selection = it },
                fastScroll = sorted,
                fastScrollDragFraction = fastScrollDragFraction,
                listLayout = true,
                onTrash = { track -> onTrashSongs(listOf(track), allIds == listOf(track.id)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LibraryScaffold(
    nowPlaying: PlayerUiState,
    showMiniPlayer: Boolean,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showMiniPlayer && nowPlaying.hasMedia) {
                NowPlayingBar(
                    state = nowPlaying,
                    onOpen = onOpenPlayer,
                    onTogglePlayPause = onTogglePlayPause,
                )
            }
        },
        content = content,
    )
}

@Composable
internal fun SongColumn(
    tracks: List<Track>,
    nowPlaying: PlayerUiState,
    favoriteIds: Set<Long>,
    onTrackSelected: (List<Track>, Long) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    contentBottomPadding: Dp,
    emptyMessage: String,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    // Null keeps long-press = add to playlist (Favorites, Search).
    onSelectionChange: ((Selection) -> Unit)? = null,
    selection: Selection = Selection(),
    /**
     * Opt-in fast scroll, the Songs list only: [tracks] with their sort and groups. The rows end
     * short of the thumb, so it never covers a row's heart or menu button.
     */
    fastScroll: SortedList<Track>? = null,
    /** Tests only: starts the fast-scroll thumb mid-drag at this fraction. */
    fastScrollDragFraction: Float? = null,
    /**
     * The Songs list: [SongListRow]s, whose ⋮ menu can also [onTrash] a song. Favorites, Recent
     * and Search keep [TrackRow].
     */
    listLayout: Boolean = false,
    onTrash: (Track) -> Unit = {},
) {
    if (tracks.isEmpty()) {
        MessagePane(
            message = emptyMessage,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
    } else if (fastScroll != null) {
        Box(modifier = modifier) {
            SongRows(
                tracks = tracks,
                nowPlaying = nowPlaying,
                favoriteIds = favoriteIds,
                onTrackSelected = onTrackSelected,
                onToggleFavorite = onToggleFavorite,
                onAddToPlaylist = onAddToPlaylist,
                contentPadding = PaddingValues(end = ListScrollerGutter, bottom = contentBottomPadding),
                listState = listState,
                onSelectionChange = onSelectionChange,
                selection = selection,
                listLayout = listLayout,
                onTrash = onTrash,
                modifier = Modifier.fillMaxSize(),
            )
            SongFastScroller(
                listState = listState,
                songs = fastScroll,
                initialDragFraction = fastScrollDragFraction,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(bottom = contentBottomPadding),
            )
        }
    } else {
        SongRows(
            tracks = tracks,
            nowPlaying = nowPlaying,
            favoriteIds = favoriteIds,
            onTrackSelected = onTrackSelected,
            onToggleFavorite = onToggleFavorite,
            onAddToPlaylist = onAddToPlaylist,
            contentPadding = PaddingValues(bottom = contentBottomPadding),
            listState = listState,
            onSelectionChange = onSelectionChange,
            selection = selection,
            listLayout = listLayout,
            onTrash = onTrash,
            modifier = modifier,
        )
    }
}

/** Test tag of the Songs list's fast-scroll thumb. */
internal const val SongFastScrollerTag = "song_fast_scroller"

/**
 * Fast scroll for the Songs list, by its sort: busy weeks of date added (as on Videos), letters
 * for name, artist and album, ranges for size and length. The unknown group gets no dot.
 */
@Composable
private fun SongFastScroller(
    listState: LazyListState,
    songs: SortedList<Track>,
    modifier: Modifier = Modifier,
    initialDragFraction: Float? = null,
) {
    val sort = songs.spec
    val entryCount = songs.items.size
    val description = stringResource(fastScrollDescriptionRes(sort.field))
    if (sort.field == SortField.Date) {
        val zone = remember { ZoneId.systemDefault() }
        val locale = Locale.getDefault()
        val weekOf = stringResource(R.string.video_week_of)
        val unknown = stringResource(R.string.video_date_unknown)
        val weeks = remember(songs, zone, locale) {
            VideoTimeline.weeks(
                entryCount = entryCount,
                zone = zone,
                locale = locale,
                isHeader = { false },
                timeAt = { index -> songs.items[index].dateAddedSeconds.takeIf { it > 0L }?.times(1_000L) },
            )
        }
        val sections = remember(weeks) { weeks.map { it.section() } }
        val stops = remember(weeks) { VideoTimeline.weekStops(weeks).map { it.section() } }
        // A year label where the first song added that year sits.
        val ticks = remember(songs) {
            val denominator = maxOf(1, entryCount - 1)
            songs.runs.mapNotNull { run -> (run.group as? SortGroup.Month)?.year?.let { it to run.start } }
                .distinctBy { it.first }
                .map { (year, start) -> FastScrollTick(year.toString(), start.toFloat() / denominator) }
        }
        FastScroller(
            listState = listState,
            entryCount = entryCount,
            sections = sections,
            stops = stops,
            label = { index ->
                index?.let(weeks::getOrNull)?.let { VideoTimeline.weekLabel(it, locale) }
                    ?.let { weekOf.format(locale, it) } ?: unknown
            },
            description = description,
            modifier = modifier,
            ticks = ticks,
            resetKey = sort,
            tag = SongFastScrollerTag,
            initialDragFraction = initialDragFraction,
            // Every busy week keeps its dot, as on Videos.
            thin = false,
        )
    } else {
        val names = rememberSortGroupNames(music = true)
        val sections = remember(songs) { songs.runs.map { ScrollSection(it.start, it.count) } }
        val stops = remember(songs) {
            songs.runs.filterNot { it.group.isUnknown }.map { ScrollSection(it.start, it.count) }
        }
        FastScroller(
            listState = listState,
            entryCount = entryCount,
            sections = sections,
            stops = stops,
            label = { index ->
                val group = index?.let(songs.runs::getOrNull)?.group
                if (group != null) names.name(group, sort.field) else ""
            },
            description = description,
            modifier = modifier,
            resetKey = sort,
            tag = SongFastScrollerTag,
            initialDragFraction = initialDragFraction,
        )
    }
}

@Composable
private fun SongRows(
    tracks: List<Track>,
    nowPlaying: PlayerUiState,
    favoriteIds: Set<Long>,
    onTrackSelected: (List<Track>, Long) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
    onSelectionChange: ((Selection) -> Unit)?,
    selection: Selection,
    listLayout: Boolean,
    onTrash: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
    ) {
        items(tracks, key = { it.id }, contentType = { "track" }) { track ->
            val change = onSelectionChange
            val selecting = change != null && selection.active
            val onClick = {
                if (selecting) {
                    change(selection.toggle(track.id))
                } else {
                    // The queue is the list as shown: the Songs sort, or the screen's own order.
                    onTrackSelected(tracks, track.id)
                }
            }
            val onLongClick = change?.let { apply ->
                {
                    apply(if (selection.active) selection.toggle(track.id) else selection.start(track.id))
                }
            }
            val itemModifier = if (reduceMotion) {
                Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
            } else {
                Modifier.animateItem()
            }
            if (listLayout) {
                SongListRow(
                    track = track,
                    isPlaying = nowPlaying.currentMediaId == track.id.toString(),
                    isFavorite = track.id in favoriteIds,
                    onClick = onClick,
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onTrash = { onTrash(track) },
                    modifier = itemModifier,
                    selectionMode = selecting,
                    selected = selecting && track.id in selection.ids,
                    onLongClick = onLongClick,
                )
            } else {
                TrackRow(
                    track = track,
                    isPlaying = nowPlaying.currentMediaId == track.id.toString(),
                    isFavorite = track.id in favoriteIds,
                    onClick = onClick,
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    modifier = itemModifier,
                    selectionMode = selecting,
                    selected = selecting && track.id in selection.ids,
                    onLongClick = onLongClick,
                )
            }
        }
    }
}

/**
 * A Songs list row ([ListRow]): the album art, the title over the artist and length, then the
 * ♥ and the ⋮ menu (add to a playlist, move to trash).
 */
@Composable
internal fun SongListRow(
    track: Track,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTrash: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    // Null keeps long-press = add to playlist.
    onLongClick: (() -> Unit)? = null,
) {
    val chrome = LocalBackgroundChrome.current
    ListRow(
        title = TrackDisplay.title(track.title),
        detail = TrackDisplay.subtitle(track.artist, track.album),
        length = PlaybackTime.format(track.durationMs),
        fill = if (isPlaying || selected) chrome.playingRowFill else chrome.rowFill,
        isPlaying = isPlaying,
        onClick = onClick,
        onLongClick = onLongClick ?: onAddToPlaylist,
        art = {
            AlbumArtwork(
                albumArtUri = track.albumArtUri,
                modifier = Modifier.size(ListThumbnailSize),
                shape = ListThumbnailShape,
                title = track.title,
            )
        },
        modifier = modifier,
        selectionMode = selectionMode,
        selected = selected,
    ) { tint ->
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(
                    if (isFavorite) R.string.unfavorite else R.string.favorite,
                ),
                tint = if (isFavorite) Secondary else chrome.iconMuted,
            )
        }
        RowOverflowMenu(tint = tint) { dismiss ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_playlist)) },
                onClick = {
                    dismiss()
                    onAddToPlaylist()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.move_to_trash)) },
                onClick = {
                    dismiss()
                    onTrash()
                },
            )
        }
    }
}

@Composable
internal fun TrackRow(
    track: Track,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    // Null keeps long-press = add to playlist.
    onLongClick: (() -> Unit)? = null,
) {
    val title = TrackDisplay.title(track.title)
    var menuOpen by remember { mutableStateOf(false) }
    val chrome = LocalBackgroundChrome.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isPlaying || selected) chrome.playingRowFill else chrome.rowFill)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick ?: onAddToPlaylist,
            )
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AlbumArtwork(
                albumArtUri = track.albumArtUri,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(12.dp),
                title = track.title,
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
                .padding(horizontal = Spacing.md)
                .clipToBounds(),
        ) {
            TrackTitle(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            TrackDisplay.subtitle(track.artist, track.album)?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (isPlaying) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = chrome.accentText,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            text = PlaybackTime.format(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selectionMode) {
            Spacer(Modifier.width(8.dp))
            return@Row
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(
                    if (isFavorite) R.string.unfavorite else R.string.favorite,
                ),
                tint = if (isFavorite) Secondary else chrome.iconMuted,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.more_actions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_to_playlist)) },
                    onClick = {
                        menuOpen = false
                        onAddToPlaylist()
                    },
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) {
            Text(stringResource(R.string.grant_access))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.open_settings))
        }
    }
}

@Composable
private fun MessagePane(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun NowPlayingBar(
    state: PlayerUiState,
    onOpen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    route: RoutePresentation = RoutePresentation.phone(),
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    padForNavigationBars: Boolean = true,
    bottomPadding: Dp = Spacing.sm,
) {
    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val phoneLabel = stringResource(R.string.output_this_phone)
    val connectName = route.deviceName.ifBlank { phoneLabel }
    val connectMuted = route.icon == RouteIcon.PHONE
    val connectColor = if (connectMuted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        DeviceGreen.copy(alpha = 0.7f)
    }
    val deviceTint = if (connectMuted) {
        MaterialTheme.colorScheme.onSurface
    } else {
        DeviceGreen.copy(alpha = 0.82f)
    }
    val chrome = LocalBackgroundChrome.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
            )
            .then(if (padForNavigationBars) Modifier.navigationBarsPadding() else Modifier)
            .padding(start = Spacing.sm, end = Spacing.sm, bottom = bottomPadding),
    ) {
        ContrastSurface(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = if (chrome.isLight) {
                chrome.frost
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
            },
            border = BorderStroke(1.dp, chrome.frostBorder),
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.sm, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumArtwork(
                        albumArtUri = state.albumArtUri,
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        title = state.title,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.sm)
                            .clipToBounds(),
                    ) {
                        val title = TrackDisplay.title(state.title)
                            .ifBlank { stringResource(R.string.nothing_playing) }
                        TrackTitle(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = TrackDisplay.subtitle(state.artist, state.album) ?: "\u00A0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (route.showBluetoothGlyph) {
                                Icon(
                                    imageVector = Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    tint = connectColor,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = connectName,
                                style = MaterialTheme.typography.labelMedium,
                                color = connectColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(onClick = onOpenDevices),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = iconForRoute(route.icon),
                            contentDescription = stringResource(R.string.choose_output),
                            tint = deviceTint,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    FavoriteHeart(
                        isFavorite = isFavorite,
                        onToggle = onToggleFavorite,
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White, CircleShape)
                            .clickable(onClick = onTogglePlayPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (state.isPlaying) R.string.pause else R.string.play,
                            ),
                            tint = Color(0xFF0E0E12),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(chrome.progressTrack),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .background(chrome.progress),
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteHeart(
    isFavorite: Boolean,
    onToggle: () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(isFavorite) {
        if (!seen) {
            seen = true
            return@LaunchedEffect
        }
        scale.snapTo(0.72f)
        scale.animateTo(
            1.18f,
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
        scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
    }
    val chrome = LocalBackgroundChrome.current
    val tint by animateColorAsState(
        targetValue = if (isFavorite) Secondary else chrome.iconMuted,
        label = "favoriteTint",
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(if (isFavorite) R.string.unfavorite else R.string.favorite),
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}

private fun iconForRoute(icon: RouteIcon): ImageVector = when (icon) {
    RouteIcon.PHONE -> Icons.Filled.Smartphone
    RouteIcon.HEADPHONES -> Icons.Filled.Headphones
    RouteIcon.SPEAKER -> Icons.Filled.Speaker
    RouteIcon.CAR -> Icons.Filled.DirectionsCar
    RouteIcon.UNKNOWN -> Icons.Filled.Bluetooth
}
