package com.shibi.musicplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fitInside
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.WindowInsetsRulers
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.AudioOutput
import com.shibi.musicplayer.domain.LibrarySearch
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.background.ContrastSurface
@Composable
fun FavoritesScreen(
    tracks: List<Track>,
    favoriteIds: Set<Long>,
    nowPlaying: PlayerUiState,
    onTrackSelected: (List<Track>, Long) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val songs = remember(tracks, favoriteIds) {
        tracks.filter { it.id in favoriteIds }
    }
    FavoritesContent(
        tracks = songs,
        favoriteIds = favoriteIds,
        nowPlaying = nowPlaying,
        onTrackSelected = onTrackSelected,
        onToggleFavorite = onToggleFavorite,
        onAddToPlaylist = onAddToPlaylist,
        contentBottomPadding = contentBottomPadding,
        modifier = modifier,
    )
}

@Composable
internal fun FavoritesContent(
    tracks: List<Track>,
    favoriteIds: Set<Long>,
    nowPlaying: PlayerUiState,
    onTrackSelected: (List<Track>, Long) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    SectionFrame(
        title = stringResource(R.string.favorites_title),
        subtitle = pluralStringResource(R.plurals.song_count, tracks.size, tracks.size),
        modifier = modifier,
    ) {
        if (tracks.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.FavoriteBorder,
                message = stringResource(R.string.favorites_empty),
                modifier = Modifier.weight(1f),
            )
        } else {
            SongColumn(
                tracks = tracks,
                nowPlaying = nowPlaying,
                favoriteIds = favoriteIds,
                onTrackSelected = onTrackSelected,
                onToggleFavorite = onToggleFavorite,
                onAddToPlaylist = onAddToPlaylist,
                contentBottomPadding = contentBottomPadding,
                emptyMessage = stringResource(R.string.favorites_empty),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Devices as a real destination, so the carousel can rest on it. */
@Composable
internal fun DevicesScreen(
    outputs: List<AudioOutput>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    SectionFrame(
        title = stringResource(R.string.devices_title),
        subtitle = stringResource(R.string.devices_subtitle),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = contentBottomPadding),
        ) {
            ContrastSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                OutputPickerContent(
                    outputs = outputs,
                    selectedId = selectedId,
                    onSelect = onSelect,
                    inSheet = false,
                )
            }
        }
    }
}

@Composable
fun RecentScreen(
    tracks: List<Track>,
    favoriteIds: Set<Long>,
    nowPlaying: PlayerUiState,
    onTrackSelected: (List<Track>, Long) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    SectionFrame(
        title = stringResource(R.string.recent_title),
        subtitle = pluralStringResource(R.plurals.song_count, tracks.size, tracks.size),
        modifier = modifier,
    ) {
        SongColumn(
            tracks = tracks,
            nowPlaying = nowPlaying,
            favoriteIds = favoriteIds,
            onTrackSelected = onTrackSelected,
            onToggleFavorite = onToggleFavorite,
            onAddToPlaylist = onAddToPlaylist,
            contentBottomPadding = contentBottomPadding,
            emptyMessage = stringResource(R.string.recent_empty),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun SearchScreen(
    tracks: List<Track>,
    favoriteIds: Set<Long>,
    nowPlaying: PlayerUiState,
    onTrackSelected: (List<Track>, Long) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    recentSearches: List<String>,
    onSearchCommitted: (String) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    SearchContent(
        tracks = tracks,
        query = query,
        onQueryChange = { query = it },
        favoriteIds = favoriteIds,
        nowPlaying = nowPlaying,
        onTrackSelected = onTrackSelected,
        onToggleFavorite = onToggleFavorite,
        onAddToPlaylist = onAddToPlaylist,
        recentSearches = recentSearches,
        onSearchCommitted = onSearchCommitted,
        contentBottomPadding = contentBottomPadding,
        modifier = modifier,
    )
}

@Composable
internal fun SearchContent(
    tracks: List<Track>,
    query: String,
    onQueryChange: (String) -> Unit,
    favoriteIds: Set<Long>,
    nowPlaying: PlayerUiState,
    onTrackSelected: (List<Track>, Long) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    recentSearches: List<String> = emptyList(),
    onSearchCommitted: (String) -> Unit = {},
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val trimmed = query.trim()
    val results = remember(tracks, trimmed) {
        if (trimmed.isEmpty()) emptyList() else LibrarySearch.filter(tracks, trimmed)
    }
    val subtitle = when {
        trimmed.isEmpty() -> stringResource(R.string.search_prompt)
        else -> pluralStringResource(R.plurals.song_count, results.size, results.size)
    }
    SectionFrame(
        title = stringResource(R.string.search_title),
        subtitle = subtitle,
        // Results end at the keyboard's top edge, never under it, folded or not.
        // Only the overlap is removed, so the chrome slot under the list is not counted twice.
        modifier = modifier.fitInside(WindowInsetsRulers.Ime.current),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { onSearchCommitted(trimmed) },
            ),
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.secondary,
            ),
        )
        Spacer(Modifier.height(8.dp))
        when {
            trimmed.isEmpty() && recentSearches.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.Search,
                    message = stringResource(R.string.search_prompt),
                    modifier = Modifier.weight(1f),
                )
            }
            trimmed.isEmpty() -> {
                RecentSearches(
                    queries = recentSearches,
                    onPick = onQueryChange,
                    contentBottomPadding = contentBottomPadding,
                    modifier = Modifier.weight(1f),
                )
            }
            results.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    message = stringResource(R.string.search_empty),
                    modifier = Modifier.weight(1f),
                )
            }
            else -> {
                SongColumn(
                    tracks = results,
                    nowPlaying = nowPlaying,
                    favoriteIds = favoriteIds,
                    onTrackSelected = onTrackSelected,
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    contentBottomPadding = contentBottomPadding,
                    emptyMessage = stringResource(R.string.search_empty),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RecentSearches(
    queries: List<String>,
    onPick: (String) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.search_recent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = contentBottomPadding),
        ) {
            items(queries, key = { it }, contentType = { "recent_search" }) { query ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(query) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
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

@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    backgroundName: String,
    onOpenBackgrounds: () -> Unit,
    onOpenLicenses: () -> Unit = {},
    brightnessSwipe: Boolean = true,
    onBrightnessSwipeChange: (Boolean) -> Unit = {},
    onOpenErrorLog: () -> Unit = {},
    contentBottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SettingsContent(
        darkTheme = darkTheme,
        onDarkThemeChange = onDarkThemeChange,
        backgroundName = backgroundName,
        onOpenBackgrounds = onOpenBackgrounds,
        onOpenLicenses = onOpenLicenses,
        brightnessSwipe = brightnessSwipe,
        onBrightnessSwipeChange = onBrightnessSwipeChange,
        onOpenErrorLog = onOpenErrorLog,
        onSendFeedback = { sendFeedback(context) },
        contentBottomPadding = contentBottomPadding,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    backgroundName: String = "",
    onOpenBackgrounds: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    brightnessSwipe: Boolean = true,
    onBrightnessSwipeChange: (Boolean) -> Unit = {},
    onOpenErrorLog: () -> Unit = {},
    onSendFeedback: () -> Unit = {},
    contentBottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    SectionFrame(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_about),
        modifier = modifier.padding(bottom = contentBottomPadding),
    ) {
        // Scrolls on short screens; on tall ones everything fits and nothing moves.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsCards(
                darkTheme = darkTheme,
                onDarkThemeChange = onDarkThemeChange,
                backgroundName = backgroundName,
                onOpenBackgrounds = onOpenBackgrounds,
                onOpenLicenses = onOpenLicenses,
                brightnessSwipe = brightnessSwipe,
                onBrightnessSwipeChange = onBrightnessSwipeChange,
                onOpenErrorLog = onOpenErrorLog,
                onSendFeedback = onSendFeedback,
            )
        }
    }
}

@Composable
private fun SettingsCards(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    backgroundName: String,
    onOpenBackgrounds: () -> Unit,
    onOpenLicenses: () -> Unit,
    brightnessSwipe: Boolean,
    onBrightnessSwipeChange: (Boolean) -> Unit,
    onOpenErrorLog: () -> Unit,
    onSendFeedback: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ContrastSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_dark),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = darkTheme,
                    onCheckedChange = onDarkThemeChange,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ContrastSurface(
            onClick = onOpenBackgrounds,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_backgrounds),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = backgroundName.ifBlank { stringResource(R.string.bg_default) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // Fullscreen video: the left-half brightness swipe. Volume is always a swipe.
        ContrastSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_video),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.settings_brightness_swipe),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = brightnessSwipe,
                    onCheckedChange = onBrightnessSwipeChange,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // Every error lands here; error screens themselves only show a short message.
        ContrastSurface(
            onClick = onOpenErrorLog,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_error_log),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_error_log_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // Leaves the app: the user's email app opens on a prefilled message.
        ContrastSurface(
            onClick = onSendFeedback,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_feedback),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_feedback_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.MailOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ContrastSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_about_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenLicenses)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_licenses),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionFrame(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        ) {
            Text(
                text = title,
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
        content()
    }
}
