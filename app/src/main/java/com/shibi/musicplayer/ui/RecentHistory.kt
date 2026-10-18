package com.shibi.musicplayer.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.ArtTile
import com.shibi.musicplayer.domain.DayLabel
import com.shibi.musicplayer.domain.HistoryDays
import com.shibi.musicplayer.domain.HistoryType
import com.shibi.musicplayer.library.videoThumbnailRequest
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.background.contentColorForContainer
import com.shibi.musicplayer.ui.background.rememberSurfaceRoles
import com.shibi.musicplayer.ui.background.toComposeColor
import com.shibi.musicplayer.ui.theme.Spacing
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** One entry of the play history, resolved against the library for display. */
data class HistoryRow(
    val key: Long,
    val type: HistoryType,
    val mediaId: Long,
    val title: String,
    val subtitle: String,
    val artUri: String?,
    val videoUri: String?,
    val playedAt: Long,
)

/** The Recently Played screen: the play history grouped into Today, Yesterday and past days. */
@Composable
fun RecentHistoryScreen(
    rows: List<HistoryRow>,
    onPlay: (HistoryRow) -> Unit,
    onClearHistory: () -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var confirming by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Spacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.recent_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.history_count, rows.size, rows.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (rows.isNotEmpty()) {
                TextButton(onClick = { confirming = true }) {
                    Text(stringResource(R.string.history_clear))
                }
            }
        }
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.recent_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val groups = remember(rows, nowMs, zone) {
                HistoryDays.group(rows, { it.playedAt }, nowMs, zone)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = contentBottomPadding),
            ) {
                groups.forEach { (label, items) ->
                    stickyHeader(
                        key = "h" + label.toString() + items.first().key,
                        contentType = "header",
                    ) {
                        DayHeader(label)
                    }
                    items(items, key = { it.key }, contentType = { "row" }) { row ->
                        HistoryRowItem(
                            row = row,
                            onPlay = onPlay,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
    if (confirming) {
        val dialogColor = LocalBackgroundChrome.current.dialog
        val onDialog = contentColorForContainer(dialogColor)
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = dialogColor,
            titleContentColor = onDialog,
            textContentColor = onDialog,
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onClearHistory()
                    },
                ) {
                    Text(stringResource(R.string.history_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Opaque page fill, so rows sliding under a stuck day header never show through. */
@Composable
private fun DayHeader(label: DayLabel, modifier: Modifier = Modifier) {
    val chrome = LocalBackgroundChrome.current
    val roles = rememberSurfaceRoles(chrome.pageFill)
    val text = when (label) {
        DayLabel.Today -> stringResource(R.string.history_today)
        DayLabel.Yesterday -> stringResource(R.string.history_yesterday)
        is DayLabel.Weekday -> label.day.getDisplayName(TextStyle.FULL, Locale.getDefault())
        is DayLabel.Date -> DateTimeFormatter
            .ofPattern(if (label.year == null) "MMM d" else "MMM d, yyyy", Locale.getDefault())
            .format(LocalDate.of(label.year ?: 2000, label.month, label.day))
    }
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .background(chrome.pageFill)
            .padding(start = 20.dp, top = 12.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = roles.onSurfaceArgb.toComposeColor(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The TrackRow pill with a 48dp song art or a 64x36 video thumbnail, a badge and the time. */
@Composable
private fun HistoryRowItem(
    row: HistoryRow,
    onPlay: (HistoryRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalBackgroundChrome.current
    val roles = rememberSurfaceRoles(chrome.rowFill)
    val onSurface = roles.onSurfaceArgb.toComposeColor()
    val onVariant = roles.onVariantArgb.toComposeColor()
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(chrome.rowFill)
            .clickable(onClick = { onPlay(row) })
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.type == HistoryType.Video) {
            HistoryVideoThumbnail(
                uri = row.videoUri.orEmpty(),
                title = row.title,
                modifier = Modifier.size(width = 64.dp, height = 36.dp),
            )
        } else {
            AlbumArtwork(
                albumArtUri = row.artUri,
                shape = RoundedCornerShape(12.dp),
                title = row.title,
                modifier = Modifier.size(48.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.md)
                .clipToBounds(),
        ) {
            TrackTitle(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            if (row.subtitle.isNotEmpty()) {
                Text(
                    text = row.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            text = stringResource(
                if (row.type == HistoryType.Video) R.string.history_badge_video else R.string.history_badge_song,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, onSurface, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = DateFormat.getTimeFormat(context).format(Date(row.playedAt)),
            style = MaterialTheme.typography.labelMedium,
            color = onVariant,
        )
    }
}

/** A replayed song; a 16:9 frame from MediaStore when one exists, otherwise the film tile. */
@Composable
private fun HistoryVideoThumbnail(
    uri: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(uri) { videoThumbnailRequest(context, uri) }
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
    ) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        ) {
            val imageState by painter.state.collectAsState()
            if (imageState is AsyncImagePainter.State.Success) {
                SubcomposeAsyncImageContent()
            } else {
                HistoryFilmTile(title = title, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** The GeneratedArtTile gradient with a film glyph, for anything with no frame. */
@Composable
private fun HistoryFilmTile(title: String, modifier: Modifier = Modifier) {
    val spec = remember(title) { ArtTile.specFor(title) }
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(Color(spec.startArgb), Color(spec.endArgb)),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Movie,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(18.dp),
        )
    }
}
