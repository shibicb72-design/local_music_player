package com.shibi.musicplayer.domain

import java.text.Collator
import java.util.Locale

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

/** What a file is, and for an episode its show's [ArtistNames.sortKey], worked out once. */
class Classified(val identity: VideoIdentity) {
    val showKey: String? = (identity as? VideoIdentity.Episode)?.let { ArtistNames.sortKey(it.show) }
}

/**
 * [VideoNameParser] behind a cache keyed by file and folder name, so a rescan of the same library
 * parses nothing again. Not thread-safe: use it from one background dispatcher at a time, never
 * from the main thread.
 */
class VideoClassifier(
    private val parse: (String, String?) -> VideoIdentity = VideoNameParser::parse,
) {
    private val cache = HashMap<Pair<String, String?>, Classified>()

    fun classify(video: Video): Classified =
        cache.getOrPut(keyOf(video)) { Classified(parse(video.displayName, video.folder)) }

    fun identityOf(video: Video): VideoIdentity = classify(video).identity

    /** Forgets every file not in [videos] (the current scan), so deleted files do not pile up. */
    fun retainOnly(videos: Collection<Video>) {
        val current = videos.mapTo(HashSet(videos.size), ::keyOf)
        cache.keys.retainAll(current)
    }

    private fun keyOf(video: Video) = video.displayName to video.folder
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

/** An episode file as [shows] takes it: the video, what it is, and its show key. */
data class TvFile(val video: Video, val episode: VideoIdentity.Episode, val showKey: String) {
    companion object {
        /** The file as an episode, or null when [classified] is not one. */
        fun of(video: Video, classified: Classified): TvFile? {
            val episode = classified.identity as? VideoIdentity.Episode ?: return null
            return TvFile(video, episode, checkNotNull(classified.showKey))
        }
    }
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
    /** Every episode file in show order: season, then episode. Built once. */
    val episodes: List<TvEpisode> = seasons.flatMap { it.episodes }

    /** Episode files, so two copies of one episode count twice. */
    val episodeCount: Int get() = episodes.size
}

/**
 * Groups episode files into shows by [TvFile.showKey], so spellings that share a key ("The
 * Office", "the office") are one show, named by its most common spelling (on a tie, the one that
 * sorts first). Shows go by key, seasons ascend, and episodes go by number, then title, then
 * video id; two files of one episode are both kept.
 */
fun shows(files: List<TvFile>, collator: Collator): List<TvShow> {
    val byNumber = compareBy<TvEpisode> { it.episode }
        .then(compareBy<TvEpisode, String?>(nullsFirst<String>(collator)) { it.title })
        .thenBy { it.video.id }
    val byCountThenSpelling = compareByDescending<Map.Entry<String, Int>> { it.value }
        .then(compareBy<Map.Entry<String, Int>, String>(collator) { it.key })
    return files
        .groupBy { it.showKey }
        .map { (key, group) ->
            val name = group.groupingBy { it.episode.show }.eachCount().entries
                .minWith(byCountThenSpelling)
                .key
            val seasons = group
                .map { file ->
                    val identity = file.episode
                    TvEpisode(file.video, identity.season, identity.episode, identity.lastEpisode, identity.episodeTitle)
                }
                .groupBy { it.season }
                .toSortedMap()
                .map { (number, episodes) -> TvSeason(number, episodes.sortedWith(byNumber)) }
            TvShow(key, name, seasons)
        }
        .sortedWith(compareBy<TvShow, String>(collator) { it.key })
}

/**
 * The episode to continue: of the show's episodes with a timed save ([VideoProgress.updatedAtMs]
 * above 0, by `contentUri`), the latest. If it stopped part way, that one, however few seconds
 * in. If it was watched (saved at 0, or in its last 5%), the first later file past the episodes
 * it holds: another copy of it, or an E11 after an E10E11 file, is skipped. Null after the last,
 * and when nothing was saved.
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
    val watched = ordered[index]
    val duration = saved.durationMs.takeIf { it > 0L } ?: watched.video.durationMs ?: 0L
    val stoppedPartWay = saved.positionMs > 0L && !ResumePolicy.isFinished(saved.positionMs, duration)
    if (stoppedPartWay) return watched
    val lastHeld = watched.lastEpisode ?: watched.episode
    return ordered.subList(index + 1, ordered.size).firstOrNull { next ->
        next.season > watched.season || (next.season == watched.season && next.episode > lastHeld)
    }
}
/** The episode numbers a file holds: `E03`, or `E10–E11` for a multi-episode file. */
fun episodeNumbers(episode: TvEpisode): String =
    "E%02d".format(Locale.ROOT, episode.episode) +
        (episode.lastEpisode?.let { "–E%02d".format(Locale.ROOT, it) } ?: "")

/** `S01E03`; a special reads `S00E01`, a multi-episode file `S02E10–E11`. */
fun episodeCode(episode: TvEpisode): String = "S%02d".format(Locale.ROOT, episode.season) + episodeNumbers(episode)

/** A show page row's title: `E03 · Pilot`, or `E03 · ` [untitled] when the file names none. */
fun episodeLabel(episode: TvEpisode, untitled: String): String =
    "${episodeNumbers(episode)} · ${episode.title ?: untitled}"

/**
 * Whole minutes left in [video] after [saved], rounded up; the length is the one the row shows
 * ([ResumePolicy.displayDuration]). Null when the row shows no watched bar
 * ([ResumePolicy.watchedFraction]): nothing saved, not started, finished, or no length.
 */
fun minutesLeft(video: Video, saved: VideoProgress?): Int? {
    if (ResumePolicy.watchedFraction(saved, video.durationMs) == null) return null
    val length = ResumePolicy.displayDuration(video.durationMs, saved) ?: return null
    val left = (length - checkNotNull(saved).positionMs).coerceAtLeast(0L)
    return ((left + 59_999L) / 60_000L).toInt()
}
