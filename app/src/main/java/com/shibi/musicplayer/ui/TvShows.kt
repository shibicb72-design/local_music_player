package com.shibi.musicplayer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.TvEpisode
import com.shibi.musicplayer.domain.TvSeason
import com.shibi.musicplayer.domain.TvShow
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.domain.episodeCode
import com.shibi.musicplayer.domain.episodeLabel
import com.shibi.musicplayer.domain.minutesLeft
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.theme.Base0
import com.shibi.musicplayer.ui.theme.Spacing

/**
 * The TV chip's list: one row per show, in [shows] order, each opening its page
 * ([onOpenShow] with [TvShow.key]). It takes the screen's shared [listState], so it keeps its
 * place while a page is open and across fullscreen.
 */
@Composable
internal fun TvShowList(
    shows: List<TvShow>,
    continueByShow: Map<String, TvEpisode>,
    savedProgress: Map<String, VideoProgress>,
    listState: LazyListState,
    onOpenShow: (String) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(top = topPadding, bottom = contentBottomPadding),
    ) {
        items(shows, key = { it.key }, contentType = { "show" }) { show ->
            TvShowRow(
                show = show,
                next = continueByShow[show.key],
                saved = continueByShow[show.key]?.let { savedProgress[it.video.contentUri] },
                onClick = { onOpenShow(show.key) },
            )
        }
    }
}

/**
 * A show: its first episode's frame, its name, its season and episode counts, and, when it has
 * an episode to continue ([next], saved at [saved]), the accent line "Continue S01E03 · 12 min
 * left". No ⋮.
 */
@Composable
internal fun TvShowRow(
    show: TvShow,
    next: TvEpisode?,
    saved: VideoProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListRow(
        title = show.name,
        detail = showCounts(show),
        fill = LocalBackgroundChrome.current.rowFill,
        isPlaying = false,
        onClick = onClick,
        onLongClick = null,
        art = { VideoThumbnail(video = show.episodes.first().video, modifier = Modifier.size(ListThumbnailSize)) },
        modifier = modifier,
        accentLine = next?.let { continueLine(it, saved) },
    )
}

/** The show page's title row, in place of "Videos": a 48 dp back arrow and the show's name. */
@Composable
internal fun TvShowHeader(name: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        // The icon button's own 12 dp inset lines its glyph up with the rows' side margin.
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A show's page under its [TvShowHeader]: the counts, one button that continues [next] (else
 * plays the first episode), the season chips (none for one season) and the [chosenSeason]'s
 * episodes. The caller holds [listState] and the season, per show, above the layouts that
 * compose this page in different places, so neither resets when the inline player opens.
 */
@Composable
internal fun TvShowPage(
    show: TvShow,
    next: TvEpisode?,
    playingVideoId: Long?,
    savedProgress: Map<String, VideoProgress>,
    onVideoClick: (Video) -> Unit,
    onTrashVideo: ((Video) -> Unit)?,
    listState: LazyListState,
    chosenSeason: Int,
    onChooseSeason: (Int) -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
) {
    val start = next ?: show.episodes.first()
    // A rescan can drop the chosen season: fall back to the first.
    val season = show.seasons.firstOrNull { it.number == chosenSeason } ?: show.seasons.first()
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(top = topPadding, bottom = contentBottomPadding),
    ) {
        item(key = "counts", contentType = "counts") {
            Text(
                text = showCounts(show),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = ListRowSideMargin),
            )
        }
        item(key = "start", contentType = "start") {
            EpisodeStartButton(
                text = if (next != null) {
                    stringResource(R.string.tv_continue, episodeCode(next))
                } else {
                    stringResource(R.string.tv_play_episode, episodeCode(start))
                },
                onClick = { onVideoClick(start.video) },
                modifier = Modifier.padding(start = ListRowSideMargin, end = ListRowSideMargin, top = Spacing.sm, bottom = Spacing.sm),
            )
        }
        if (show.seasons.size > 1) {
            item(key = "seasons", contentType = "seasons") {
                SeasonChips(seasons = show.seasons, chosen = season.number, onSelect = onChooseSeason)
            }
        }
        items(season.episodes, key = { it.video.id }, contentType = { "video" }) { episode ->
            val video = episode.video
            val last = episode.lastEpisode
            val untitled = if (last != null) {
                stringResource(R.string.tv_episodes_untitled, episode.episode, last)
            } else {
                stringResource(R.string.tv_episode_untitled, episode.episode)
            }
            VideoRow(
                video = video,
                isPlaying = video.id == playingVideoId,
                onClick = { onVideoClick(video) },
                onTrash = onTrashVideo?.let { trash -> { trash(video) } },
                saved = savedProgress[video.contentUri],
                title = episodeLabel(episode, untitled),
                subtitle = videoSubtitle(video, tail = null),
            )
        }
    }
}

/** "12 seasons · 279 episodes". */
@Composable
private fun showCounts(show: TvShow): String = listOf(
    pluralStringResource(R.plurals.season_count, show.seasons.size, show.seasons.size),
    pluralStringResource(R.plurals.episode_count, show.episodeCount, show.episodeCount),
).joinToString(" · ")

/** "Continue S01E03 · 12 min left"; without the time when the row would show no watched bar. */
@Composable
private fun continueLine(episode: TvEpisode, saved: VideoProgress?): String {
    val label = stringResource(R.string.tv_continue, episodeCode(episode))
    val minutes = minutesLeft(episode.video, saved)
    val left = minutes?.let { pluralStringResource(R.plurals.minutes_left, it, it) }
    return listOfNotNull(label, left).joinToString(" · ")
}

/** The page's one primary button, in [PlayAllButton]'s colours: the white pill, dark text. */
@Composable
private fun EpisodeStartButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Base0),
    ) {
        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = text, maxLines = 1)
    }
}

/** One chip per season ("Season 1"; season 0 is "Specials"), scrolling sideways. */
@Composable
private fun SeasonChips(seasons: List<TvSeason>, chosen: Int, onSelect: (Int) -> Unit) {
    val groupLabel = stringResource(R.string.tv_seasons_label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .semantics { contentDescription = groupLabel }
            .padding(horizontal = ListRowSideMargin),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        seasons.forEach { season ->
            val label = if (season.number == 0) {
                stringResource(R.string.tv_specials)
            } else {
                stringResource(R.string.tv_season, season.number)
            }
            FilterChipItem(
                label = label,
                count = null,
                description = label,
                chosen = season.number == chosen,
                onClick = { onSelect(season.number) },
            )
        }
    }
}
