package com.shibi.musicplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.containsTamil
import com.shibi.musicplayer.playlist.PlaylistSummary
import com.shibi.musicplayer.playlist.PlaylistViewModel
import com.shibi.musicplayer.ui.theme.Base4
import com.shibi.musicplayer.ui.theme.NotoTamil
import com.shibi.musicplayer.ui.theme.Secondary
import com.shibi.musicplayer.ui.theme.TextHigh

data class PlaylistCardUi(
    val id: Long,
    val name: String,
    val songCount: Int,
    val coverTitles: List<String>,
)

@Composable
fun PlaylistsScreen(
    viewModel: PlaylistViewModel,
    tracksById: Map<Long, Track>,
    onOpen: (Long) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    val cards = remember(state.playlists, tracksById) {
        state.playlists.map { it.toCard(tracksById) }
    }
    if (!state.loaded) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        PlaylistsContent(
            cards = cards,
            onOpen = onOpen,
            onCreate = { showCreate = true },
            contentBottomPadding = contentBottomPadding,
            modifier = modifier,
        )
    }
    if (showCreate) {
        PlaylistNameDialog(
            title = stringResource(R.string.new_playlist),
            confirmLabel = stringResource(R.string.create_playlist),
            initialName = "",
            onConfirm = { name ->
                viewModel.create(name)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
internal fun PlaylistsContent(
    cards: List<PlaylistCardUi>,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
            ),
    ) {
        PlaylistHeader(
            count = cards.size,
            onCreate = onCreate,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        )
        if (cards.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = contentBottomPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.playlists_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = contentBottomPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(cards, key = { it.id }) { card ->
                    PlaylistCard(card = card, onClick = { onOpen(card.id) })
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    count: Int,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.playlists_title),
                style = MaterialTheme.typography.headlineMedium,
                color = TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.playlist_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onCreate) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = Secondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.new_playlist),
                color = Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    card: PlaylistCardUi,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = TextHigh,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            ArtCollage(
                titles = card.coverTitles,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = if (containsTamil(card.name)) NotoTamil else null,
                color = TextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.song_count, card.songCount, card.songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ArtCollage(
    titles: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
    ) {
        repeat(2) { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                repeat(2) { column ->
                    val title = titles.getOrNull(row * 2 + column)
                    if (title.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(Base4),
                        )
                    } else {
                        GeneratedArtTile(
                            title = title,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

internal fun PlaylistSummary.toCard(tracksById: Map<Long, Track>): PlaylistCardUi {
    return PlaylistCardUi(
        id = id,
        name = name,
        songCount = songCount,
        coverTitles = coverTrackIds.mapNotNull { trackId ->
            tracksById[trackId]?.title?.takeIf { it.isNotBlank() }
        },
    )
}
