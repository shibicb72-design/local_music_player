package com.shibi.musicplayer.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
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
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val results = remember(tracks, query) { LibrarySearch.filter(tracks, query) }
    SectionFrame(
        title = stringResource(R.string.search_title),
        subtitle = pluralStringResource(R.plurals.song_count, results.size, results.size),
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
        SongColumn(
            tracks = results,
            nowPlaying = nowPlaying,
            favoriteIds = favoriteIds,
            onTrackSelected = onTrackSelected,
            onToggleFavorite = onToggleFavorite,
            onAddToPlaylist = onAddToPlaylist,
            contentBottomPadding = contentBottomPadding,
            emptyMessage = stringResource(
                if (query.isBlank()) R.string.empty_library else R.string.search_empty,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsContent(
        darkTheme = darkTheme,
        onDarkThemeChange = onDarkThemeChange,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionFrame(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_about),
        modifier = modifier,
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
