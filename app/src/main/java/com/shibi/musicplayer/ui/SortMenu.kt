package com.shibi.musicplayer.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortGroup
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.VideoTimeline
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.background.contentColorForContainer
import java.util.Locale

/** Test tag of the sort button in a title row. */
internal const val SortButtonTag = "sort_button"

/** Test tag of one menu row, from its stored form (`sort_option_name_asc`). */
internal fun sortOptionTag(spec: SortSpec): String = "sort_option_${spec.encode()}"

/**
 * The sort icon at the end of a list's title row and its menu of [options], the current one
 * checked. Picking another one calls [onSortChange]; picking the current one only closes the
 * menu. The menu draws its own colours: the sheet fill with the content colour taken from it.
 * [initialOpen] is for screenshots. The open state is the menu's own, so an animated menu can
 * replace the [DropdownMenu] here without touching the screens.
 */
@Composable
internal fun SortButton(
    sort: SortSpec,
    options: List<SortSpec>,
    onSortChange: (SortSpec) -> Unit,
    modifier: Modifier = Modifier,
    initialOpen: Boolean = false,
) {
    var expanded by remember { mutableStateOf(initialOpen) }
    val chrome = LocalBackgroundChrome.current
    val container = chrome.sheet
    val onContainer = contentColorForContainer(container)
    val current = stringResource(sortLabelRes(sort))
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .testTag(SortButtonTag)
                .semantics { stateDescription = current },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = stringResource(R.string.sort_button),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = container,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(sortLabelRes(option))) },
                    trailingIcon = if (option == sort) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.sort_current),
                            )
                        }
                    } else {
                        null
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = onContainer,
                        trailingIconColor = onContainer,
                    ),
                    onClick = {
                        expanded = false
                        if (option != sort) onSortChange(option)
                    },
                    modifier = Modifier.testTag(sortOptionTag(option)),
                )
            }
        }
    }
}

/** A sort's name in the menu and the subtitle: "Newest first", "Name A–Z". */
@StringRes
internal fun sortLabelRes(spec: SortSpec): Int = when (spec.field) {
    SortField.Date -> if (spec.ascending) R.string.sort_date_oldest else R.string.sort_date_newest
    SortField.Name -> if (spec.ascending) R.string.sort_name_az else R.string.sort_name_za
    SortField.Artist -> if (spec.ascending) R.string.sort_artist_az else R.string.sort_artist_za
    SortField.Album -> if (spec.ascending) R.string.sort_album_az else R.string.sort_album_za
    SortField.Size -> if (spec.ascending) R.string.sort_size_smallest else R.string.sort_size_largest
    SortField.Length -> if (spec.ascending) R.string.sort_length_shortest else R.string.sort_length_longest
}

/** What the fast scroller says it scrolls by, for accessibility: it follows the sort. */
@StringRes
internal fun fastScrollDescriptionRes(field: SortField): Int = when (field) {
    SortField.Date -> R.string.video_fast_scroll
    SortField.Name -> R.string.fast_scroll_by_name
    SortField.Artist -> R.string.fast_scroll_by_artist
    SortField.Album -> R.string.fast_scroll_by_album
    SortField.Size -> R.string.fast_scroll_by_size
    SortField.Length -> R.string.fast_scroll_by_length
}

/** The title-row subtitle: "12 videos · Newest first". */
@Composable
internal fun sortSubtitle(count: String, sort: SortSpec): String =
    stringResource(R.string.sort_subtitle, count, stringResource(sortLabelRes(sort)))

/** The words a header or a fast-scroll bubble uses for a group, read once per composition. */
@Immutable
internal class SortGroupNames(
    private val unknownDate: String,
    private val unknown: String,
    private val unknownSize: String,
    private val unknownLength: String,
    private val sizeBands: List<String>,
    private val lengthBands: List<String>,
    private val locale: Locale,
) {
    /** "March 2025", "E", "#", "10–100 MB", or the unknown group's name. */
    fun name(group: SortGroup, field: SortField): String = when (group) {
        is SortGroup.Month -> VideoTimeline.monthLabel(group.year, group.month, locale) ?: unknownDate
        is SortGroup.Letter -> group.letter ?: unknown
        is SortGroup.Band -> {
            val index = group.index
            val size = field == SortField.Size
            when {
                index == null -> if (size) unknownSize else unknownLength
                size -> sizeBands.getOrElse(index) { unknownSize }
                else -> lengthBands.getOrElse(index) { unknownLength }
            }
        }
    }
}

/** [SortGroupNames] with the Songs bands when [music], else the Videos bands. */
@Composable
internal fun rememberSortGroupNames(music: Boolean): SortGroupNames {
    val unknownDate = stringResource(R.string.video_date_unknown)
    val unknown = stringResource(R.string.sort_group_unknown)
    val unknownSize = stringResource(R.string.sort_unknown_size)
    val unknownLength = stringResource(R.string.sort_unknown_length)
    val sizes = stringArrayResource(if (music) R.array.sort_bands_music_size else R.array.sort_bands_video_size)
    val lengths = stringArrayResource(if (music) R.array.sort_bands_music_length else R.array.sort_bands_video_length)
    val locale = Locale.getDefault()
    return remember(unknownDate, unknown, unknownSize, unknownLength, locale, music) {
        SortGroupNames(unknownDate, unknown, unknownSize, unknownLength, sizes.toList(), lengths.toList(), locale)
    }
}
