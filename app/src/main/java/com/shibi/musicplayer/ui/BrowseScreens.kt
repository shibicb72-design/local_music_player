package com.shibi.musicplayer.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.LibrarySearch
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.theme.TextHigh

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
        modifier = modifier,
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
                focusedTextColor = TextHigh,
                unfocusedTextColor = TextHigh,
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
            items(queries, key = { it }) { query ->
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
                        color = TextHigh,
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
    contentBottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    SettingsContent(
        darkTheme = darkTheme,
        onDarkThemeChange = onDarkThemeChange,
        contentBottomPadding = contentBottomPadding,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    contentBottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    SectionFrame(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_about),
        modifier = modifier.padding(bottom = contentBottomPadding),
    ) {
        Surface(
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
                        color = TextHigh,
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
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHigh,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                color = TextHigh,
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
