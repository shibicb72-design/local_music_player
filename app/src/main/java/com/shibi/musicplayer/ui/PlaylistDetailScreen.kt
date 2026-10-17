package com.shibi.musicplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.PlaylistOrder
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.TrackDisplay
import com.shibi.musicplayer.playlist.PlaylistViewModel
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.background.contentColorForContainer
import com.shibi.musicplayer.ui.theme.Base0
import com.shibi.musicplayer.ui.theme.Base3
import com.shibi.musicplayer.ui.theme.Error
import kotlin.math.abs

data class PlaylistSongRow(
    val trackId: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumArtUri: String?,
    val missing: Boolean,
)

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    viewModel: PlaylistViewModel,
    tracksById: Map<Long, Track>,
    onBack: () -> Unit,
    onPlay: (List<Track>, Long) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val detail by remember(playlistId) { viewModel.playlist(playlistId) }
        .collectAsStateWithLifecycle(null)
    val unavailable = stringResource(R.string.unavailable_song)
    val current = detail
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    if (current == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val songs = current.trackIds.map { trackId ->
        val track = tracksById[trackId]
        if (track == null) {
            PlaylistSongRow(
                trackId = trackId,
                title = unavailable,
                artist = null,
                album = null,
                albumArtUri = null,
                missing = true,
            )
        } else {
            PlaylistSongRow(
                trackId = trackId,
                title = TrackDisplay.title(track.title),
                artist = TrackDisplay.artist(track.artist),
                album = TrackDisplay.album(track.album),
                albumArtUri = track.albumArtUri,
                missing = false,
            )
        }
    }
    val playable = current.trackIds.mapNotNull { tracksById[it] }
    PlaylistDetailContent(
        name = current.name,
        songs = songs,
        onBack = onBack,
        onPlayAll = { playable.firstOrNull()?.let { onPlay(playable, it.id) } },
        onShuffle = { if (playable.isNotEmpty()) onShuffle(playable) },
        onRenameClick = { renaming = true },
        onDeleteClick = { confirmingDelete = true },
        onRemove = { trackId -> viewModel.removeSong(playlistId, trackId) },
        onReorder = { ids -> viewModel.reorder(playlistId, ids) },
        onPlayFrom = { trackId ->
            if (playable.any { it.id == trackId }) onPlay(playable, trackId)
        },
        contentBottomPadding = contentBottomPadding,
        modifier = modifier,
    )
    if (renaming) {
        PlaylistNameDialog(
            title = stringResource(R.string.rename_playlist),
            confirmLabel = stringResource(R.string.save),
            initialName = current.name,
            onConfirm = { name ->
                viewModel.rename(playlistId, name)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmingDelete) {
        val dialogColor = LocalBackgroundChrome.current.dialog
        val onDialog = contentColorForContainer(dialogColor)
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            containerColor = dialogColor,
            titleContentColor = onDialog,
            textContentColor = onDialog,
            title = { Text(stringResource(R.string.delete_playlist)) },
            text = { Text(stringResource(R.string.delete_playlist_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.delete(playlistId)
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.delete), color = Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
internal fun PlaylistDetailContent(
    name: String,
    songs: List<PlaylistSongRow>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRemove: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onPlayFrom: (Long) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var localIds by remember(songs.map { it.trackId }) { mutableStateOf(songs.map { it.trackId }) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val idsState = rememberUpdatedState(localIds)
    val onReorderState = rememberUpdatedState(onReorder)
    val songById = songs.associateBy { it.trackId }
    val shown = localIds.mapNotNull { songById[it] }
    val canPlay = songs.any { !it.missing }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                TrackTitle(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = pluralStringResource(R.plurals.song_count, songs.size, songs.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.more_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename_playlist)) },
                        onClick = {
                            menuOpen = false
                            onRenameClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_playlist), color = Error) },
                        onClick = {
                            menuOpen = false
                            onDeleteClick()
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onPlayAll,
                enabled = canPlay,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Base0,
                    disabledContainerColor = Color.White.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.play_all), maxLines = 1)
            }
            OutlinedButton(
                onClick = onShuffle,
                enabled = canPlay,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.72f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.shuffle), maxLines = 1)
            }
        }
        if (shown.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = contentBottomPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.playlist_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = contentBottomPadding),
            ) {
                items(shown, key = { it.trackId }) { song ->
                    val dragging = draggingId == song.trackId
                    PlaylistSongLine(
                        song = song,
                        dragging = dragging,
                        dragDy = if (dragging) dragDy else 0f,
                        onPlay = { onPlayFrom(song.trackId) },
                        onRemove = { onRemove(song.trackId) },
                        handleModifier = Modifier.pointerInput(song.trackId) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingId = song.trackId
                                    dragDy = 0f
                                },
                                onDragEnd = {
                                    onReorderState.value(idsState.value)
                                    draggingId = null
                                    dragDy = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragDy = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDy += amount.y
                                    val layout = listState.layoutInfo
                                    val current = layout.visibleItemsInfo
                                        .firstOrNull { it.key == song.trackId }
                                        ?: return@detectDragGestures
                                    val center = current.offset + current.size / 2f + dragDy
                                    val target = layout.visibleItemsInfo
                                        .filter { it.key is Long }
                                        .minByOrNull { abs((it.offset + it.size / 2f) - center) }
                                        ?: return@detectDragGestures
                                    if (target.key == song.trackId) return@detectDragGestures
                                    val ids = idsState.value
                                    val from = ids.indexOf(song.trackId)
                                    val to = ids.indexOf(target.key as Long)
                                    if (from >= 0 && to >= 0 && from != to) {
                                        localIds = PlaylistOrder.moved(ids, from, to)
                                        dragDy += current.offset - target.offset
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistSongLine(
    song: PlaylistSongRow,
    dragging: Boolean,
    dragDy: Float,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    handleModifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val chrome = LocalBackgroundChrome.current
    val dragFill = if (chrome.isLight) chrome.frost else Base3
    Row(
        modifier = Modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = dragDy }
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (dragging) dragFill else Color.Transparent)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.DragIndicator,
            contentDescription = stringResource(R.string.reorder),
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = handleModifier
                .size(48.dp)
                .padding(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !song.missing, onClick = onPlay)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlbumArtwork(
                albumArtUri = song.albumArtUri,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                title = song.title,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                TrackTitle(
                    text = TrackDisplay.title(song.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (song.missing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
                val subtitle = song.artist ?: song.album
                if (subtitle != null) {
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
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more_actions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_from_playlist)) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}
