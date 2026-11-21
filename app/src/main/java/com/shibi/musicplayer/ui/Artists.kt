package com.shibi.musicplayer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.AlbumEntry
import com.shibi.musicplayer.domain.ArtistEntry
import com.shibi.musicplayer.domain.ArtistPage
import com.shibi.musicplayer.domain.MusicCatalog
import com.shibi.musicplayer.domain.MusicView
import com.shibi.musicplayer.domain.ScrollSection
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortLetters
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.playback.PlayerUiState
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome

/** Test tag of the Artists list's fast-scroll thumb. */
internal const val ArtistFastScrollerTag = "artist_fast_scroller"

/** The unknown artist's list key: its catalog key is null, and no artist key starts with a NUL. */
private const val UnknownArtistItemKey = "\u0000unknown"

/** The cards in an artist page's album strips. */
private val AlbumCardSize = 140.dp

@StringRes
private fun labelRes(view: MusicView): Int = when (view) {
    MusicView.Songs -> R.string.music_view_songs
    MusicView.Artists -> R.string.music_view_artists
    MusicView.Albums -> R.string.music_view_albums
}

/**
 * The chips under the Songs screen's title: Songs, Artists and Albums, with no counts. The row
 * scrolls sideways when they don't fit, as the Videos filters do. Tapping the chosen chip does nothing.
 */
@Composable
internal fun MusicViewChips(
    selected: MusicView,
    onSelect: (MusicView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupLabel = stringResource(R.string.music_views_label)
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .semantics { contentDescription = groupLabel }
            .padding(horizontal = ListRowSideMargin),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MusicView.entries.forEach { view ->
            val label = stringResource(labelRes(view))
            FilterChipItem(
                label = label,
                count = null,
                description = label,
                chosen = view == selected,
                onClick = { if (view != selected) onSelect(view) },
            )
        }
    }
}

/** "2 albums · 8 songs"; just "1 song" when the artist has no album of its own. */
@Composable
internal fun artistCounts(albumCount: Int, songCount: Int): String {
    val songs = pluralStringResource(R.plurals.song_count, songCount, songCount)
    if (albumCount == 0) return songs
    return pluralStringResource(R.plurals.album_count, albumCount, albumCount) + " · " + songs
}

/** An artist's name; the unknown entry gets the `unknown_artist` label. */
@Composable
internal fun artistName(artist: ArtistEntry): String = artist.name ?: stringResource(R.string.unknown_artist)

/**
 * One row per artist, in the catalog's order (`#`, then A-Z, unknown last), with the A-Z fast
 * scroller over their ends. Tapping a row opens the artist's page.
 */
@Composable
internal fun ArtistList(
    catalog: MusicCatalog,
    listState: LazyListState,
    onOpenArtist: (String?) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = contentBottomPadding),
        ) {
            items(catalog.artists, key = { it.key ?: UnknownArtistItemKey }, contentType = { "artist" }) { artist ->
                ArtistRow(artist = artist, onClick = { onOpenArtist(artist.key) })
            }
        }
        ArtistFastScroller(
            listState = listState,
            catalog = catalog,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(bottom = contentBottomPadding),
        )
    }
}

/**
 * An artist: a round tile (its first album's art, else its sort letter, so "The Beatles" shows B),
 * its name and its counts. No ⋮.
 */
@Composable
private fun ArtistRow(artist: ArtistEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListRow(
        title = artistName(artist),
        detail = artistCounts(artist.albumCount, artist.songCount),
        fill = LocalBackgroundChrome.current.rowFill,
        isPlaying = false,
        onClick = onClick,
        onLongClick = null,
        art = {
            AlbumArtwork(
                albumArtUri = artist.artUri,
                modifier = Modifier.size(ListThumbnailSize),
                shape = CircleShape,
                title = artist.key?.let(SortLetters::of).orEmpty(),
            )
        },
        modifier = modifier,
    )
}

/** A-Z fast scroll over the artists, by their letter runs; the unknown run gets no dot. */
@Composable
private fun ArtistFastScroller(
    listState: LazyListState,
    catalog: MusicCatalog,
    modifier: Modifier = Modifier,
) {
    val names = rememberSortGroupNames(music = true)
    val runs = catalog.artistRuns
    val sections = remember(runs) { runs.map { ScrollSection(it.start, it.count) } }
    val stops = remember(runs) { runs.filterNot { it.group.isUnknown }.map { ScrollSection(it.start, it.count) } }
    FastScroller(
        listState = listState,
        entryCount = catalog.artists.size,
        sections = sections,
        stops = stops,
        label = { index ->
            val group = index?.let(runs::getOrNull)?.group
            if (group != null) names.name(group, SortField.Artist) else ""
        },
        description = stringResource(R.string.fast_scroll_by_artist),
        modifier = modifier,
        tag = ArtistFastScrollerTag,
    )
}

/**
 * An artist's page under its [TvShowHeader]: the counts, Play all and Shuffle, the strips of its
 * own albums and of the albums it appears on (each left out when empty), then every song in page
 * order. A song row's detail is its album only when the catalog kept that album. The caller holds
 * [listState] per artist.
 */
@Composable
internal fun ArtistPageContent(
    page: ArtistPage,
    catalog: MusicCatalog,
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
    val songs = page.songs
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(bottom = contentBottomPadding),
    ) {
        item(key = "counts", contentType = "counts") {
            Text(
                text = artistCounts(page.artist.albumCount, page.artist.songCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = ListRowSideMargin),
            )
        }
        item(key = "buttons", contentType = "buttons") {
            ArtistButtons(
                enabled = songs.isNotEmpty(),
                onPlayAll = { songs.firstOrNull()?.let { onTrackSelected(songs, it.id) } },
                onShuffle = { if (songs.isNotEmpty()) onShuffle(songs) },
            )
        }
        if (page.albums.isNotEmpty()) {
            item(key = "albums", contentType = "strip") {
                AlbumStrip(heading = stringResource(R.string.artist_albums), albums = page.albums)
            }
        }
        if (page.appearsOn.isNotEmpty()) {
            item(key = "appears_on", contentType = "strip") {
                AlbumStrip(heading = stringResource(R.string.artist_appears_on), albums = page.appearsOn)
            }
        }
        item(key = "songs_heading", contentType = "heading") {
            SectionHeading(stringResource(R.string.artist_songs))
        }
        items(songs, key = { it.id }, contentType = { "track" }) { track ->
            SongListRow(
                track = track,
                isPlaying = nowPlaying.currentMediaId == track.id.toString(),
                isFavorite = track.id in favoriteIds,
                // The queue is the artist's songs in page order.
                onClick = { onTrackSelected(songs, track.id) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onToggleFavorite = { onToggleFavorite(track) },
                onTrash = onTrash?.let { trash -> { trash(track) } },
                detail = catalog.albumOf(track)?.title,
            )
        }
    }
}

/** Play all and Shuffle side by side, as on a playlist's page. */
@Composable
private fun ArtistButtons(enabled: Boolean, onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ListRowSideMargin, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayAllButton(onClick = onPlayAll, enabled = enabled, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onShuffle,
            enabled = enabled,
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
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = ListRowSideMargin, end = ListRowSideMargin, top = 12.dp, bottom = 8.dp),
    )
}

/** A heading over a row of album cards that scrolls sideways. */
@Composable
private fun AlbumStrip(heading: String, albums: List<AlbumEntry>) {
    Column {
        SectionHeading(heading)
        LazyRow(
            contentPadding = PaddingValues(horizontal = ListRowSideMargin),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(albums, key = { it.key }, contentType = { "album" }) { album ->
                AlbumCard(album)
            }
        }
    }
}

/**
 * An album: square art, its title and "2013 · 4 songs" (no year when unknown). Not tappable
 * yet: the album page comes in the next step.
 */
@Composable
private fun AlbumCard(album: AlbumEntry) {
    val songs = pluralStringResource(R.plurals.song_count, album.songCount, album.songCount)
    Column(modifier = Modifier.width(AlbumCardSize)) {
        AlbumArtwork(
            albumArtUri = album.albumArtUri,
            modifier = Modifier.size(AlbumCardSize),
            shape = RoundedCornerShape(12.dp),
            title = album.title,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.year?.let { "$it · $songs" } ?: songs,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The Albums chip for now: one centred line. The grid and the album page replace it in the next step. */
@Composable
internal fun AlbumsPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.albums_coming),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
