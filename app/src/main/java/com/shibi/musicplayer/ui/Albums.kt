package com.shibi.musicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.AlbumEntry
import com.shibi.musicplayer.domain.AlbumPage
import com.shibi.musicplayer.domain.AlbumSort
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.albumTrackArtist
import com.shibi.musicplayer.playback.PlayerUiState
import androidx.compose.foundation.lazy.grid.items as gridItems

/** The Albums menu's orders as the shared sort menu's specs, in menu order. */
private val AlbumSortOptions: List<SortSpec> = AlbumSort.entries.map(::albumSortSpec)

/** [sort] as the spec the shared sort menu and subtitle show: "Album A–Z", "Artist A–Z", "Newest first". */
internal fun albumSortSpec(sort: AlbumSort): SortSpec = when (sort) {
    AlbumSort.Title -> SortSpec(SortField.Album, ascending = true)
    AlbumSort.Artist -> SortSpec(SortField.Artist, ascending = true)
    AlbumSort.YearNewest -> SortSpec(SortField.Date, ascending = false)
}

/** The sort icon of the Albums grid: the shared menu over the three album orders. */
@Composable
internal fun AlbumSortButton(sort: AlbumSort, onSortChange: (AlbumSort) -> Unit) {
    SortButton(
        sort = albumSortSpec(sort),
        options = AlbumSortOptions,
        onSortChange = { spec -> AlbumSort.entries.firstOrNull { albumSortSpec(it) == spec }?.let(onSortChange) },
    )
}

/** "The Beatles · 1965": the album's artist (or the unknown label), then its year when known. */
@Composable
private fun artistAndYear(album: AlbumEntry): String {
    val artist = album.artistName ?: stringResource(R.string.unknown_artist)
    return album.year?.let { "$artist · $it" } ?: artist
}

/**
 * One card per album in the given order, two columns on a phone and more when unfolded. Tapping
 * a card opens its page. With no albums, one centred line.
 */
@Composable
internal fun AlbumGrid(
    albums: List<AlbumEntry>,
    gridState: LazyGridState,
    onOpenAlbum: (String) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) {
        Box(modifier = modifier.padding(24.dp).padding(bottom = contentBottomPadding), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.albums_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = modifier,
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The key itself, not a tag built from it: album keys hold a NUL.
        gridItems(albums, key = { it.key }, contentType = { "album" }) { album ->
            AlbumCard(album = album, meta = artistAndYear(album), onClick = { onOpenAlbum(album.key) })
        }
    }
}

/** Rounded to the nearest minute, and never under one: "12 min". */
internal fun albumMinutes(durationMs: Long): Int = maxOf(1L, (durationMs + 30_000L) / 60_000L).toInt()

/**
 * An album's page under its [TvShowHeader]: the artist and year, the songs and minutes, Play all
 * and Shuffle, then every disc in order, each under a "Disc 1" heading only when there are
 * several. A row shows its track number in place of the art, and its artist only when that is a
 * guest. Every queue is the album's songs in page order. The caller holds [listState] per album.
 */
@Composable
internal fun AlbumPageContent(
    page: AlbumPage,
    nowPlaying: PlayerUiState,
    favoriteIds: Set<Long>,
    onTrackSelected: (List<Track>, Long) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onTrash: ((Track) -> Unit)?,
    listState: LazyListState,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val album = page.album
    val tracks = remember(page) { page.discs.flatMap { it.tracks } }
    val songs = pluralStringResource(R.plurals.song_count, album.songCount, album.songCount)
    val minutes = albumMinutes(album.durationMs)
    val length = pluralStringResource(R.plurals.album_minutes, minutes, minutes)
    val meta = artistAndYear(album)
    val showDiscs = page.discs.size > 1
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(bottom = contentBottomPadding),
    ) {
        item(key = "meta", contentType = "meta") {
            Column(modifier = Modifier.padding(horizontal = ListRowSideMargin)) {
                MetaLine(meta)
                MetaLine("$songs · $length")
            }
        }
        item(key = "buttons", contentType = "buttons") {
            ArtistButtons(
                enabled = tracks.isNotEmpty(),
                onPlayAll = { tracks.firstOrNull()?.let { onTrackSelected(tracks, it.id) } },
                onShuffle = { if (tracks.isNotEmpty()) onShuffle(tracks) },
            )
        }
        page.discs.forEach { disc ->
            if (showDiscs) {
                item(key = "disc_${disc.number}", contentType = "heading") {
                    DiscHeading(stringResource(R.string.album_disc, disc.number))
                }
            }
            items(disc.tracks, key = { it.id }, contentType = { "track" }) { track ->
                SongListRow(
                    track = track,
                    isPlaying = nowPlaying.currentMediaId == track.id.toString(),
                    isFavorite = track.id in favoriteIds,
                    onClick = { onTrackSelected(tracks, track.id) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onTrash = onTrash?.let { trash -> { trash(track) } },
                    detail = albumTrackArtist(track, album),
                    leading = { tint -> TrackNumber(track.trackNumber, tint) },
                )
            }
        }
    }
}

@Composable
private fun MetaLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DiscHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = ListRowSideMargin, end = ListRowSideMargin, top = 12.dp, bottom = 8.dp),
    )
}

/**
 * A track's number in a box the size of the art, in the row's own muted colour; a dash when it
 * has none. Three digits take a smaller style, and the text keeps its own width, so "128" is
 * never clipped, even at 2x font.
 */
@Composable
private fun TrackNumber(number: Int?, tint: Color) {
    val text = number?.toString() ?: stringResource(R.string.track_number_missing)
    Box(modifier = Modifier.size(ListThumbnailSize), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = if (text.length >= 3) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleMedium,
            color = tint,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.wrapContentWidth(unbounded = true),
        )
    }
}
