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
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.domain.VideoProgress
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

    /** The sort asked for: the stored one, then the user's picks. Null until the store answers. */
    private val requestedSort = MutableStateFlow<SortSpec?>(null)

    /** libVLC's saved lengths by URI, for the rows MediaStore gave no length. */
    private val savedLengths = MutableStateFlow<Map<String, Long>>(emptyMap())

    init {
        viewModelScope.launch {
            val stored = sortPrefs.videoSort.first()
            // A pick made while the store was still reading wins.
            if (requestedSort.value == null) requestedSort.value = stored
        }
        viewModelScope.launch {
            combine(scan.filterNotNull(), requestedSort.filterNotNull(), savedLengths, ::SortInput)
                .distinctUntilChanged { old, new ->
                    old.scan === new.scan && old.sort == new.sort &&
                        (new.sort.field != SortField.Length || old.lengths == new.lengths)
                }
                .collectLatest(::publish)
        }
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
            // The list empties; the chosen sort stays for when access comes back.
            _uiState.update { current -> VideoLibraryUiState(access = access, sort = current.sort) }
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

    /** The user picked [spec] in the menu: the list re-sorts, and the pick is stored. */
    fun setSort(spec: SortSpec) {
        if (spec !in LibrarySorts.VideoOptions) return
        requestedSort.value = spec
        viewModelScope.launch {
            try {
                sortPrefs.setVideoSort(spec)
            } catch (_: IOException) {
                // Kept for this session; the next start falls back to the stored sort.
            }
        }
    }

    /** The saved resume entries: a row with no MediaStore length shows libVLC's, and sorts by it. */
    fun setSavedProgress(progress: Map<String, VideoProgress>) {
        savedLengths.value = buildMap {
            progress.forEach { (uri, saved) -> if (saved.durationMs > 0L) put(uri, saved.durationMs) }
        }
    }

    private suspend fun publish(input: SortInput) {
        val unknownTitle = getApplication<Application>().getString(R.string.unknown_title)
        val sorted = withContext(sortDispatcher) {
            // The length a row shows (ResumePolicy.displayDuration): MediaStore's, else libVLC's.
            LibrarySorter.videos(input.scan.videos, input.sort, Collator.getInstance(), ZoneId.systemDefault(), unknownTitle) {
                it.durationMs ?: input.lengths[it.contentUri]
            }
        }
        _uiState.update { current ->
            // Access went away while this sorted: the denied state stays empty.
            if (current.access == VideoAccess.Denied) {
                current
            } else {
                current.copy(videos = sorted.items, sort = sorted.spec, runs = sorted.runs, isLoading = false, hasError = false)
            }
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

    private class SortInput(val scan: Scan, val sort: SortSpec, val lengths: Map<String, Long>)

    private companion object {
        const val CHANGE_SETTLE_MS = 500L
    }
}
