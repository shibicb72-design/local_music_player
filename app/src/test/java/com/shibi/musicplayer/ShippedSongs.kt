package com.shibi.musicplayer

import com.shibi.musicplayer.domain.LibrarySorter
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.SortedList
import com.shibi.musicplayer.domain.Track
import com.shibi.musicplayer.domain.UnknownNames
import java.text.Collator
import java.time.ZoneId

/**
 * [tracks] as the Songs screen ships them: sorted, with their groups, so a screenshot shows the
 * sort button, the sort in the subtitle and the rows' room for the fast scroller. Name A–Z keeps
 * the fixtures easy to read.
 */
internal fun shippedSongs(
    tracks: List<Track>,
    sort: SortSpec = SortSpec(SortField.Name, ascending = true),
): SortedList<Track> = LibrarySorter.tracks(
    tracks,
    sort,
    Collator.getInstance(),
    ZoneId.systemDefault(),
    UnknownNames(title = "Unknown title", artist = "Unknown artist", album = "Unknown album"),
)
