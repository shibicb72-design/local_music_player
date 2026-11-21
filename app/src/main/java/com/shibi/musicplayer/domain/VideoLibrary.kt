package com.shibi.musicplayer.domain

import java.text.Collator

/** Which part of the library the Videos screen shows. [key] is what the store keeps. */
enum class VideoFilter(val key: String) {
    All("all"),
    Movies("movies"),
    Tv("tv"),
    Personal("personal"),
    ;

    companion object {
        /** An unknown or missing value (an old build, a corrupt file) reads as [All]. */
        fun fromKey(raw: String?): VideoFilter = entries.firstOrNull { it.key == raw } ?: All
    }
}

/** The Movies list's own sort, Name A-Z until the user picks one from [LibrarySorts.VideoOptions]. */
val MovieSortDefault = SortSpec(SortField.Name, ascending = true)

/** The filter a video falls under besides [VideoFilter.All]: anything not a movie or episode is Personal. */
fun filterOf(identity: VideoIdentity): VideoFilter = when (identity) {
    is VideoIdentity.Movie -> VideoFilter.Movies
    is VideoIdentity.Episode -> VideoFilter.Tv
    is VideoIdentity.Personal, is VideoIdentity.Other -> VideoFilter.Personal
}

/**
 * [VideoNameParser] behind a cache keyed by file and folder name, so a rescan of the same library
 * parses nothing again. Not thread-safe: use it from one background dispatcher at a time, never
 * from the main thread.
 */
class VideoClassifier(
    private val parse: (String, String?) -> VideoIdentity = VideoNameParser::parse,
) {
    private val cache = HashMap<Pair<String, String?>, VideoIdentity>()

    fun identityOf(video: Video): VideoIdentity =
        cache.getOrPut(video.displayName to video.folder) { parse(video.displayName, video.folder) }
}

/** Files per filter; [tv] counts episode files, not shows. */
data class VideoCounts(val all: Int = 0, val movies: Int = 0, val tv: Int = 0, val personal: Int = 0)

fun countsOf(identities: Collection<VideoIdentity>): VideoCounts {
    val byFilter = identities.groupingBy(::filterOf).eachCount()
    return VideoCounts(
        all = identities.size,
        movies = byFilter[VideoFilter.Movies] ?: 0,
        tv = byFilter[VideoFilter.Tv] ?: 0,
        personal = byFilter[VideoFilter.Personal] ?: 0,
    )
}

/**
 * The video as a list row shows it: a movie under its cleaned title, so the Name sort and its
 * letter headers read "Inception", not "Inception.2010.1080p". Anything else is returned as is.
 */
fun displayVideo(video: Video, identity: VideoIdentity): Video = when (identity) {
    is VideoIdentity.Movie -> video.copy(title = identity.title)
    else -> video
}

/** One episode file. [lastEpisode] is set for a multi-episode file (`S02E10E11`). */
data class TvEpisode(
    val video: Video,
    val season: Int,
    val episode: Int,
    val lastEpisode: Int?,
    val title: String?,
)

data class TvSeason(val number: Int, val episodes: List<TvEpisode>)

/** A show's files, grouped by season. [key] is [ArtistNames.sortKey] of the name. */
data class TvShow(val key: String, val name: String, val seasons: List<TvSeason>) {
    /** Every episode file in show order: season, then episode. */
    val episodes: List<TvEpisode> get() = seasons.flatMap { it.episodes }

    /** Episode files, so two copies of one episode count twice. */
    val episodeCount: Int get() = seasons.sumOf { it.episodes.size }
}

/**
 * Groups episodes into shows. Spellings that share a [ArtistNames.sortKey] ("The Office",
 * "the office") are one show, named by its most common spelling (on a tie, the one that sorts
 * first). Shows go by key, seasons ascend, and episodes go by number, then title, then video id;
 * two files of one episode are both kept.
 */
fun shows(episodes: List<Pair<Video, VideoIdentity.Episode>>, collator: Collator): List<TvShow> {
    val byNumber = compareBy<TvEpisode> { it.episode }
        .then(compareBy<TvEpisode, String?>(nullsFirst<String>(collator)) { it.title })
        .thenBy { it.video.id }
    val byCountThenSpelling = compareByDescending<Map.Entry<String, Int>> { it.value }
        .then(compareBy<Map.Entry<String, Int>, String>(collator) { it.key })
    return episodes
        .groupBy { (_, identity) -> ArtistNames.sortKey(identity.show) }
        .map { (key, group) ->
            val name = group.groupingBy { (_, identity) -> identity.show }.eachCount().entries
                .minWith(byCountThenSpelling)
                .key
            val seasons = group
                .map { (video, identity) ->
                    TvEpisode(video, identity.season, identity.episode, identity.lastEpisode, identity.episodeTitle)
                }
                .groupBy { it.season }
                .toSortedMap()
                .map { (number, files) -> TvSeason(number, files.sortedWith(byNumber)) }
            TvShow(key, name, seasons)
        }
        .sortedWith(compareBy<TvShow, String>(collator) { it.key })
}

/**
 * The episode to continue: of the show's episodes with a timed save ([VideoProgress.updatedAtMs]
 * above 0, by `contentUri`), the latest. If it stopped part way, that one, however few seconds
 * in. If it was watched (saved at 0, or in its last 5%), the next in show order, or null after
 * the last. Null when nothing was saved.
 *
 * Known limit: Start over followed by closing at exactly 0 also stores 0, so it counts as watched.
 */
fun continueEpisode(show: TvShow, progress: Map<String, VideoProgress>): TvEpisode? {
    val ordered = show.episodes
    val latest = ordered.indices
        .mapNotNull { index -> progress[ordered[index].video.contentUri]?.takeIf { it.updatedAtMs > 0L }?.let { index to it } }
        .maxByOrNull { (_, saved) -> saved.updatedAtMs }
        ?: return null
    val (index, saved) = latest
    val duration = saved.durationMs.takeIf { it > 0L } ?: ordered[index].video.durationMs ?: 0L
    val stoppedPartWay = saved.positionMs > 0L && !ResumePolicy.isFinished(saved.positionMs, duration)
    return if (stoppedPartWay) ordered[index] else ordered.getOrNull(index + 1)
}
