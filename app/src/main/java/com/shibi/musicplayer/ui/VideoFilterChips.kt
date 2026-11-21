package com.shibi.musicplayer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.VideoCounts
import com.shibi.musicplayer.domain.VideoFilter
import com.shibi.musicplayer.ui.background.rememberSurfaceRoles
import com.shibi.musicplayer.ui.background.toComposeColor

/** The chip's label. */
@StringRes
private fun labelRes(filter: VideoFilter): Int = when (filter) {
    VideoFilter.All -> R.string.video_filter_all
    VideoFilter.Movies -> R.string.video_filter_movies
    VideoFilter.Tv -> R.string.video_filter_tv
    VideoFilter.Personal -> R.string.video_filter_personal
}

private fun countOf(counts: VideoCounts, filter: VideoFilter): Int = when (filter) {
    VideoFilter.All -> counts.all
    VideoFilter.Movies -> counts.movies
    VideoFilter.Tv -> counts.tv
    VideoFilter.Personal -> counts.personal
}

/**
 * The filter row under the Videos title: All, Movies, TV and Personal, each with its file count.
 * It scrolls sideways when the chips don't fit. [counts] is null while they don't match the list
 * yet: the chips then show only their labels. Tapping the chosen chip does nothing.
 */
@Composable
internal fun VideoFilterChips(
    selected: VideoFilter,
    counts: VideoCounts?,
    onSelect: (VideoFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtersLabel = stringResource(R.string.video_filters_label)
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .semantics { contentDescription = filtersLabel }
            .padding(horizontal = ListRowSideMargin),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoFilter.entries.forEach { filter ->
            val label = stringResource(labelRes(filter))
            val count = counts?.let { countOf(it, filter) }
            FilterChipItem(
                label = label,
                count = count,
                description = if (count != null) {
                    pluralStringResource(R.plurals.video_filter_description, count, label, count)
                } else {
                    label
                },
                chosen = filter == selected,
                onClick = { if (filter != selected) onSelect(filter) },
            )
        }
    }
}

/**
 * One chip, a tab in a `selectableGroup` row: the Videos filters, a show page's seasons. Fill:
 * the theme's `primary` when chosen, `surfaceVariant` otherwise; the text takes its colour from
 * that fill (`rememberSurfaceRoles`), the [count] (none when null) in the quieter role. The pill
 * is 40 dp tall inside a 48 dp touch target, and grows with the font. [description] is what it
 * reads out: the label and the count as one phrase.
 */
@Composable
internal fun FilterChipItem(
    label: String,
    count: Int?,
    description: String,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val fill = if (chosen) colors.primary else colors.surfaceVariant
    val roles = rememberSurfaceRoles(fill)
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            // The row reads as one tab: its label and count are one spoken phrase.
            .selectable(selected = chosen, onClick = onClick, role = Role.Tab)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(fill)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = roles.onSurfaceArgb.toComposeColor(),
                maxLines = 1,
                // The chip's description speaks the label and the count; the texts add nothing.
                modifier = Modifier.clearAndSetSemantics {},
            )
            if (count != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = roles.onVariantArgb.toComposeColor(),
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}
