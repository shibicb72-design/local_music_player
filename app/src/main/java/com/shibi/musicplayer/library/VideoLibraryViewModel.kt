package com.shibi.musicplayer.library

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shibi.musicplayer.R
import com.shibi.musicplayer.diagnostics.ErrorLog
import com.shibi.musicplayer.diagnostics.ErrorLogger
import com.shibi.musicplayer.domain.LibrarySorter
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.SortField
import com.shibi.musicplayer.domain.SortRun
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.SortedList
import com.shibi.musicplayer.domain.TvEpisode
import com.shibi.musicplayer.domain.TvFile
import com.shibi.musicplayer.domain.TvShow
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.domain.VideoClassifier
import com.shibi.musicplayer.domain.VideoCounts
import com.shibi.musicplayer.domain.VideoFilter
import com.shibi.musicplayer.domain.VideoIdentity
import com.shibi.musicplayer.domain.VideoProgress
import com.shibi.musicplayer.domain.continueEpisode
import com.shibi.musicplayer.domain.countsOf
import com.shibi.musicplayer.domain.displayVideo
import com.shibi.musicplayer.domain.filterOf
import com.shibi.musicplayer.domain.shows
import java.io.IOException
import java.text.Collator
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VideoLibraryUiState(
    val access: VideoAccess = VideoAccess.Denied,
    /** In [sort] order. */
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    /** The order [videos] are in; it changes only together with them. */
    val sort: SortSpec = LibrarySorts.VideoDefault,
    /**
     * The groups (headers) of [videos], made by the same sort. The screen shows only these runs,
     * so a state with videos carries them; tests build both with `LibrarySorter.videos`.
     */
    val runs: List<SortRun> = emptyList(),
    /**
     * The part of the library in [videos]. Under [VideoFilter.Tv], [videos] is every episode in
     * [shows] order and [runs] is empty: [shows] drives that screen.
     */
    val filter: VideoFilter = VideoFilter.All,
    /**
     * False until the stored filter (and both sorts) loaded and a list was published with them,
     * so filter chips never show All first and then jump.
     */
    val isReady: Boolean = false,
    /**
     * Files per filter, whatever [filter] is. Under All, [counts], [shows], [identities] and
     * [continueByShow] follow the list in a second update, once the files are classified.
     */
    val counts: VideoCounts = VideoCounts(),
    /** Every TV show in the library, whatever [filter] is. */
    val shows: List<TvShow> = emptyList(),
    /** What each video is, by id: a row's year or camera source. */
    val identities: Map<Long, VideoIdentity> = emptyMap(),
    /** The episode to continue, by [TvShow.key]; a show with nothing to continue is absent. */
    val continueByShow: Map<String, TvEpisode> = emptyMap(),
    /**
     * The show whose page is open, by [TvShow.key]; null shows the list. Not stored: it lives as
     * long as this view model, so it survives a trip to fullscreen and back.
     */
    val openShowKey: String? = null,
)

/**
 * The video list and the current access level. Create it only from the Videos section (V3):
 * nothing here runs at app start, so music-only users never see a video permission prompt
 * and MediaStore's video table is never queried for them.
 *
 * Call [refresh] on every resume and after a permission result; access is re-checked each
 * time. While the screen is alive, MediaStore changes (new downloads) reload the list too.
 * The list is published only in its stored sort, sorted off the main thread, so the screen
 * never shows the default order first.
 */
class VideoLibraryViewModel internal constructor(
    application: Application,
    private val repository: VideoRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val errorLogger: ErrorLogger = ErrorLogger.None,
    private val sortPrefs: SortPrefs = InMemorySortPrefs(),
    /** Sorting is CPU work; tests get the same inline dispatcher as [ioDispatcher]. */
    private val sortDispatcher: CoroutineDispatcher = ioDispatcher,
    /** Parses each file name once for the view model's life. Used only on [sortDispatcher]. */
    private val classifier: VideoClassifier = VideoClassifier(),
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        VideoRepository(application),
        Dispatchers.IO,
        ErrorLog.get(application),
        SortPrefs.get(application),
        Dispatchers.Default,
    )

    private val _uiState = MutableStateFlow(VideoLibraryUiState())
    val uiState: StateFlow<VideoLibraryUiState> = _uiState.asStateFlow()

    /** The last scan failure logged; cleared by a scan that works. */
    private var lastFailure: String? = null

    /** The last scan that worked, in MediaStore order; null before one and while access is denied. */
    private val scan = MutableStateFlow<Scan?>(null)

    /**
     * The video sort, the Movies sort and the filter asked for: the stored ones, then the user's
     * picks. Each is null until the store answers.
     */
    private val requestedSort = MutableStateFlow<SortSpec?>(null)
    private val requestedMovieSort = MutableStateFlow<SortSpec?>(null)
    private val requestedFilter = MutableStateFlow<VideoFilter?>(null)

    /** libVLC's saved lengths by URI, for the rows MediaStore gave no length. */
    private val savedLengths = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Every saved resume entry, for Continue. Not part of [SortInput], so a save never re-sorts. */
    private val savedProgress = MutableStateFlow<Map<String, VideoProgress>>(emptyMap())

    init {
        loadStored(requestedSort) { sortPrefs.videoSort.first() }
        loadStored(requestedMovieSort) { sortPrefs.movieSort.first() }
        loadStored(requestedFilter) { sortPrefs.videoFilter.first() }
        viewModelScope.launch {
            combine(
                scan.filterNotNull(),
                requestedFilter.filterNotNull(),
                requestedSort.filterNotNull(),
                requestedMovieSort.filterNotNull(),
                savedLengths,
                ::SortInput,
            )
                .distinctUntilChanged { old, new ->
                    old.scan === new.scan && old.filter == new.filter &&
                        old.videoSort == new.videoSort && old.movieSort == new.movieSort &&
                        (new.sortInForce.field != SortField.Length || old.lengths == new.lengths)
                }
                .collectLatest(::publish)
        }
        viewModelScope.launch { savedProgress.collectLatest(::refreshContinue) }
        viewModelScope.launch {
            repository.changes().collectLatest {
                // A download or a multi-file copy fires a burst of changes; reload once it settles.
                delay(CHANGE_SETTLE_MS)
                refresh()
            }
        }
    }

    fun refresh() {
        val access = currentAccess()
        if (access == VideoAccess.Denied) {
            scan.value = null
            // The list empties; the chosen sort and filter stay for when access comes back.
            _uiState.update { current ->
                VideoLibraryUiState(access = access, sort = current.sort, filter = current.filter, isReady = current.isReady)
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    access = access,
                    // The first scan is the only one that has nothing to show. A later scan
                    // (resume, a MediaStore change) keeps the list the screen already has.
                    isLoading = current.videos.isEmpty(),
                    hasError = false,
                )
            }
            val result = withContext(ioDispatcher) {
                runCatching { repository.loadVideos() }
            }
            val failure = result.exceptionOrNull()
            // The same failure on every MediaStore burst would push useful entries out of the log.
            val signature = failure?.let { "${it.javaClass.name}: ${it.message}" }
            val repeated = signature != null && signature == lastFailure
            lastFailure = signature
            failure?.takeUnless { repeated }?.let { error ->
                // The list shows a short message; the cause goes to Settings > Error log.
                errorLogger.log(
                    step = "Scan videos (MediaStore query, access $access)",
                    error = error,
                    shown = getApplication<Application>().getString(R.string.video_library_error),
                    media = null,
                )
            }
            result.fold(
                // Shown once sorted: see publish.
                onSuccess = { videos -> scan.value = Scan(videos) },
                onFailure = { _uiState.update { current -> current.copy(isLoading = false, hasError = true) } },
            )
        }
    }

    /**
     * The user picked [spec] in the menu: the list re-sorts, and the pick is stored. Under Movies
     * it is the Movies sort; under every other filter, the video sort.
     */
    fun setSort(spec: SortSpec) {
        if (spec !in LibrarySorts.VideoOptions) return
        val movies = requestedFilter.value == VideoFilter.Movies
        (if (movies) requestedMovieSort else requestedSort).value = spec
        viewModelScope.launch {
            try {
                if (movies) sortPrefs.setMovieSort(spec) else sortPrefs.setVideoSort(spec)
            } catch (_: IOException) {
                // Kept for this session; the next start falls back to the stored sort.
            }
        }
    }

    /**
     * Shows [filter]'s part of the library, and stores it. The Videos screen's filter chips call
     * it. An open show page closes.
     */
    fun setFilter(filter: VideoFilter) {
        closeShow()
        requestedFilter.value = filter
        viewModelScope.launch {
            try {
                sortPrefs.setVideoFilter(filter)
            } catch (_: IOException) {
                // Kept for this session; the next start falls back to the stored filter.
            }
        }
    }

    /** Opens the page of the show with [key] ([TvShow.key]) in place of the show list. */
    fun openShow(key: String) {
        _uiState.update { it.copy(openShowKey = key) }
    }

    fun closeShow() {
        _uiState.update { it.copy(openShowKey = null) }
    }

    /**
     * The saved resume entries: a row with no MediaStore length shows libVLC's, and sorts by it;
     * TV's Continue follows the latest save.
     */
    fun setSavedProgress(progress: Map<String, VideoProgress>) {
        savedProgress.value = progress
        savedLengths.value = buildMap {
            progress.forEach { (uri, saved) -> if (saved.durationMs > 0L) put(uri, saved.durationMs) }
        }
    }

    private fun <T : Any> loadStored(target: MutableStateFlow<T?>, read: suspend () -> T) {
        viewModelScope.launch {
            val stored = read()
            // A pick made while the store was still reading wins.
            if (target.value == null) target.value = stored
        }
    }

    private suspend fun publish(input: SortInput) {
        val unknownTitle = getApplication<Application>().getString(R.string.unknown_title)
        val videos = input.scan.videos
        if (input.filter == VideoFilter.All) {
            // Today's work first: the list never waits for the files to be classified.
            val list = withContext(sortDispatcher) { sortVideos(videos, input.videoSort, input.lengths, unknownTitle) }
            show(input.filter, list, library = null)
        }
        val progress = savedProgress.value
        if (input.filter == VideoFilter.All) {
            val library = withContext(sortDispatcher) { classify(videos, progress) }
            show(input.filter, list = null, library)
        } else {
            // The filtered list needs the classification, so both land together.
            val (library, list) = withContext(sortDispatcher) {
                val library = classify(videos, progress)
                library to filteredList(input, library, unknownTitle)
            }
            show(input.filter, list, library)
        }
        // A save landed while this ran: the progress collector worked it out for the old shows.
        if (savedProgress.value !== progress) refreshContinue(savedProgress.value)
    }

    /** Publishes [list] (with [filter]) and/or [library]; a null part keeps what is shown. */
    private fun show(filter: VideoFilter, list: SortedList<Video>?, library: Library?) {
        _uiState.update { current ->
            // Access went away while this sorted: the denied state stays empty.
            if (current.access == VideoAccess.Denied) return@update current
            var next = current
            if (list != null) {
                next = next.copy(
                    videos = list.items,
                    sort = list.spec,
                    runs = list.runs,
                    filter = filter,
                    // A show page belongs to Tv: any other list closes it.
                    openShowKey = next.openShowKey.takeIf { filter == VideoFilter.Tv },
                    isReady = true,
                    isLoading = false,
                    hasError = false,
                )
            }
            if (library != null) {
                next = next.copy(
                    counts = library.counts,
                    shows = library.shows,
                    identities = library.identities,
                    continueByShow = library.continueByShow,
                    // A rescan without the open show closes its page.
                    openShowKey = current.openShowKey?.takeIf { key -> library.shows.any { it.key == key } },
                )
            }
            next
        }
    }

    /** Classifies one scan and groups its episodes. CPU work: runs on [sortDispatcher] only. */
    private fun classify(videos: List<Video>, progress: Map<String, VideoProgress>): Library {
        val classified = videos.map(classifier::classify)
        classifier.retainOnly(videos)
        val identities = HashMap<Long, VideoIdentity>(videos.size * 2)
        videos.forEachIndexed { index, video -> identities[video.id] = classified[index].identity }
        val files = videos.indices.mapNotNull { index -> TvFile.of(videos[index], classified[index]) }
        val grouped = shows(files, Collator.getInstance())
        return Library(countsOf(classified.map { it.identity }), grouped, identities, continueByShow(grouped, progress))
    }

    /** The list under Movies, Personal or Tv (All is just [sortVideos]). Runs on [sortDispatcher]. */
    private fun filteredList(input: SortInput, library: Library, unknownTitle: String): SortedList<Video> {
        val videos = input.scan.videos
        val identityOf = { video: Video -> library.identities.getValue(video.id) }
        return when (input.filter) {
            VideoFilter.All -> sortVideos(videos, input.videoSort, input.lengths, unknownTitle)
            VideoFilter.Movies -> sortVideos(
                videos.mapNotNull { video ->
                    identityOf(video).takeIf { filterOf(it) == VideoFilter.Movies }?.let { displayVideo(video, it) }
                },
                input.movieSort,
                input.lengths,
                unknownTitle,
            )
            VideoFilter.Personal -> sortVideos(
                videos.filter { filterOf(identityOf(it)) == VideoFilter.Personal },
                input.videoSort,
                input.lengths,
                unknownTitle,
            )
            VideoFilter.Tv -> SortedList(input.videoSort, library.shows.flatMap { show -> show.episodes.map { it.video } }, emptyList())
        }
    }

    /** Works out Continue for the published shows; dropped if a publish or a newer save came meanwhile. */
    private suspend fun refreshContinue(progress: Map<String, VideoProgress>) {
        val shows = _uiState.value.shows
        if (shows.isEmpty()) return
        val next = withContext(sortDispatcher) { continueByShow(shows, progress) }
        _uiState.update { current ->
            if (current.shows === shows && savedProgress.value === progress) current.copy(continueByShow = next) else current
        }
    }

    private fun currentAccess(): VideoAccess {
        val sdkInt = Build.VERSION.SDK_INT
        val granted = VideoAccess.permissionsToRequest(sdkInt).filterTo(mutableSetOf()) { permission ->
            ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED
        }
        return VideoAccess.classify(sdkInt, granted)
    }

    /** One scan's list. Compared by identity, so every scan sorts again, even an unchanged one. */
    private class Scan(val videos: List<Video>)

    private class SortInput(
        val scan: Scan,
        val filter: VideoFilter,
        val videoSort: SortSpec,
        val movieSort: SortSpec,
        val lengths: Map<String, Long>,
    ) {
        /** The sort the list is in: Movies has its own. */
        val sortInForce: SortSpec get() = if (filter == VideoFilter.Movies) movieSort else videoSort
    }

    /** One scan, classified: everything the state holds besides the list. */
    private class Library(
        val counts: VideoCounts,
        val shows: List<TvShow>,
        val identities: Map<Long, VideoIdentity>,
        val continueByShow: Map<String, TvEpisode>,
    )

    private companion object {
        const val CHANGE_SETTLE_MS = 500L

        /** The length a row shows (ResumePolicy.displayDuration): MediaStore's, else libVLC's. */
        fun sortVideos(videos: List<Video>, spec: SortSpec, lengths: Map<String, Long>, unknownTitle: String): SortedList<Video> =
            LibrarySorter.videos(videos, spec, Collator.getInstance(), ZoneId.systemDefault(), unknownTitle) {
                it.durationMs ?: lengths[it.contentUri]
            }

        fun continueByShow(shows: List<TvShow>, progress: Map<String, VideoProgress>): Map<String, TvEpisode> =
            buildMap { shows.forEach { show -> continueEpisode(show, progress)?.let { put(show.key, it) } } }
    }
}
