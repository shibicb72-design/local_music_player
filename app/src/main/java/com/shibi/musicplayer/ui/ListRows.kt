package com.shibi.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.background.rememberSurfaceRoles
import com.shibi.musicplayer.ui.background.toComposeColor
import com.shibi.musicplayer.ui.theme.Base0
import com.shibi.musicplayer.ui.theme.Spacing

/** Inset of a Songs or Videos row's content from the row's edges: the art's start, the ⋮ glyph's end. */
internal val ListRowSideMargin = 16.dp

/** A Songs or Videos row at the default font scale; larger text grows it. */
internal val ListRowHeight = 60.dp

/** The row's square art: album art or a video frame. */
internal val ListThumbnailSize = 40.dp

/** Corner radius of the [ListThumbnailSize] art. */
internal val ListThumbnailCorner = 8.dp

internal val ListThumbnailShape = RoundedCornerShape(ListThumbnailCorner)

/** Touch target of a row's ♥ and ⋮ buttons: Material's minimum, which `IconButton` keeps. */
internal val ListRowButtonTouchSize = 48.dp

/** Height of the Play all pill at the top of the Songs and Videos lists. */
internal val PlayAllPillHeight = 48.dp

/**
 * Room the Songs and Videos rows leave at their end for the fast-scroll thumb: with the rows'
 * own [ListRowFillInset], the ♥ and ⋮ touch targets end 4 dp left of the thumb's touch area, so
 * it never covers them (`LibrarySortUiTest.theSongsScrollerNeverCoversARowsHeartOrMenu`,
 * `ListRowsUiTest`). The room stays while the thumb is hidden, so the rows never change width as
 * it shows and fades.
 */
internal val ListScrollerGutter = FastScrollTouchWidth

/** Test tag of a row's art. */
internal const val ListThumbnailTag = "list_thumbnail"

/**
 * How far the row's fill (light backdrops, the playing or selected row) stays in from the row's
 * edges. The ⋮ touch target reaches the fill's end, which puts its glyph on [ListRowSideMargin].
 */
private val ListRowFillInset = 4.dp
private val ListRowFillShape = RoundedCornerShape(16.dp)

/** Between the art and the text. */
private val ListRowTextGap = 16.dp

/** A Material glyph, centred in its [ListRowButtonTouchSize] button. */
private val ListRowGlyphSize = 24.dp
private val PlayingGlyphSize = 18.dp

/** The fill's inner padding: the art lands on the margin at the start, the ⋮ glyph at the end. */
private val ListRowStartPadding = ListRowSideMargin - ListRowFillInset
private val ListRowEndPadding = ListRowStartPadding - (ListRowButtonTouchSize - ListRowGlyphSize) / 2

private val PlayAllGlyphSize = 18.dp
private val PlayAllGlyphGap = 6.dp

/**
 * One row of the Songs or Videos list: [art] ([ListThumbnailSize]), the title over a grey second
 * line ([detail], then [length], which always shows in full), the playing glyph and [actions]
 * (the ♥, the ⋮ menu) at the end. Text colours come from the row's own [fill]. While
 * [selectionMode] a check rides the art's corner and the actions give way to the end margin.
 */
@Composable
internal fun ListRow(
    title: String,
    detail: String?,
    fill: Color,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    art: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    length: String? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    actions: @Composable RowScope.(tint: Color) -> Unit = {},
) {
    val roles = rememberSurfaceRoles(fill)
    val variant = roles.onVariantArgb.toComposeColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ListRowFillInset)
            .clip(ListRowFillShape)
            .background(fill)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = ListRowHeight)
            .padding(start = ListRowStartPadding, end = ListRowEndPadding, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.testTag(ListThumbnailTag)) {
            art()
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
                .padding(start = ListRowTextGap, end = Spacing.sm)
                .clipToBounds(),
        ) {
            TrackTitle(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = roles.onSurfaceArgb.toComposeColor(),
                modifier = Modifier.fillMaxWidth(),
            )
            SecondLine(detail = detail, length = length, color = variant)
        }
        if (isPlaying) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = LocalBackgroundChrome.current.accentText,
                modifier = Modifier.size(PlayingGlyphSize),
            )
        }
        if (selectionMode) {
            Spacer(Modifier.width(ListRowStartPadding - ListRowEndPadding))
        } else {
            actions(variant)
        }
    }
}

/** The grey second line: [detail] ellipsizes first, so [length] always shows in full. */
@Composable
private fun SecondLine(detail: String?, length: String?, color: Color) {
    if (detail == null && length == null) return
    Row(modifier = Modifier.fillMaxWidth()) {
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (length != null) {
            Text(
                text = if (detail != null) " · $length" else length,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * A row's ⋮ button, drawn in [tint], and its menu. [items] are the menu's entries; each calls
 * the dismiss it is handed before it acts.
 */
@Composable
internal fun RowOverflowMenu(
    tint: Color,
    items: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.more_actions),
                tint = tint,
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            items { open = false }
        }
    }
}

/**
 * The white Play all pill, shared by a playlist's header and the Songs and Videos lists. Its
 * dark text and icon come from the pill itself, so it reads over every backdrop.
 */
@Composable
internal fun PlayAllButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
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
            modifier = Modifier.size(PlayAllGlyphSize),
        )
        Spacer(Modifier.width(PlayAllGlyphGap))
        Text(stringResource(R.string.play_all), maxLines = 1)
    }
}

/**
 * [PlayAllButton] at the top of the Songs or Videos list, on the rows' side margin and
 * [PlayAllPillHeight] tall. It stays (off) while selecting, so the list never jumps.
 */
@Composable
internal fun ListPlayAll(
    onPlayAll: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    PlayAllButton(
        onClick = onPlayAll,
        enabled = enabled,
        modifier = modifier
            .padding(start = ListRowSideMargin, end = ListRowSideMargin, top = Spacing.xs, bottom = Spacing.sm)
            .heightIn(min = PlayAllPillHeight),
    )
}
