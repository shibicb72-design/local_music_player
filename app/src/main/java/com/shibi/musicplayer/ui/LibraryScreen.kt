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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.PlaybackTime
import com.shibi.musicplayer.domain.RouteIcon
import com.shibi.musicplayer.domain.RoutePresentation
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.TrackDisplay
import com.shibi.musicplayer.library.LibraryViewModel
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.theme.DeviceGreen
import com.shibi.musicplayer.ui.theme.Secondary
import com.shibi.musicplayer.ui.theme.Spacing

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
    showMiniPlayer: Boolean = true,
    contentBottomPadding: Dp = 120.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        audioGranted = result[audioPermission] == true
        if (audioGranted) viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        if (audioGranted) {
            viewModel.refresh()
        } else {
            permissionLauncher.launch(startupPermissions())
        }
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
        else -> LibraryContent(
            tracks = state.tracks,
            nowPlaying = nowPlaying,
            showMiniPlayer = showMiniPlayer,
            onTrackSelected = onTrackSelected,
            onOpenPlayer = onOpenPlayer,
            onTogglePlayPause = onTogglePlayPause,
            onAddToPlaylist = onAddToPlaylist,
            favoriteIds = favoriteIds,
            onToggleFavorite = onToggleFavorite,
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
    contentBottomPadding: Dp = 120.dp,
    modifier: Modifier = Modifier,
) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.song_count,
                        tracks.size,
                        tracks.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) {
        MessagePane(
            message = emptyMessage,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = contentBottomPadding),
        ) {
            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isPlaying = nowPlaying.currentMediaId == track.id.toString(),
                    isFavorite = track.id in favoriteIds,
                    onClick = { onTrackSelected(tracks, track.id) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    modifier = Modifier.animateItem(),
                )
            }
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
) {
    val title = TrackDisplay.title(track.title)
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isPlaying) Color(0xFF7C5CFF).copy(alpha = 0.12f) else Color.Transparent,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onAddToPlaylist,
            )
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtwork(
            albumArtUri = track.albumArtUri,
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(12.dp),
            title = track.title,
        )
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
                tint = Color(0xFFB9A8FF),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            text = PlaybackTime.format(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(
                    if (isFavorite) R.string.unfavorite else R.string.favorite,
                ),
                tint = if (isFavorite) Secondary else Color.White.copy(alpha = 0.92f),
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
            )
            .then(if (padForNavigationBars) Modifier.navigationBarsPadding() else Modifier)
            .padding(start = Spacing.sm, end = Spacing.sm, bottom = bottomPadding),
    ) {
        Surface(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
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
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .background(Color.White),
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
    val tint by animateColorAsState(
        targetValue = if (isFavorite) Secondary else Color.White.copy(alpha = 0.92f),
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
