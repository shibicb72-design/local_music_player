package com.shibi.musicplayer.library

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VideoLibraryUiState(
    val access: VideoAccess = VideoAccess.Denied,
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)

/**
 * The video list and the current access level. Create it only from the Videos section (V3):
 * nothing here runs at app start, so music-only users never see a video permission prompt
 * and MediaStore's video table is never queried for them.
 *
 * Call [refresh] on every resume and after a permission result; access is re-checked each
 * time. While the screen is alive, MediaStore changes (new downloads) reload the list too.
 */
class VideoLibraryViewModel internal constructor(
    application: Application,
    private val repository: VideoRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, VideoRepository(application), Dispatchers.IO)

    private val _uiState = MutableStateFlow(VideoLibraryUiState())
    val uiState: StateFlow<VideoLibraryUiState> = _uiState.asStateFlow()

    init {
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
            _uiState.value = VideoLibraryUiState(access = access)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(access = access, isLoading = true, hasError = false) }
            val result = withContext(ioDispatcher) {
                runCatching { repository.loadVideos() }
            }
            _uiState.update { current ->
                result.fold(
                    onSuccess = { videos -> current.copy(videos = videos, isLoading = false, hasError = false) },
                    onFailure = { current.copy(isLoading = false, hasError = true) },
                )
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

    private companion object {
        const val CHANGE_SETTLE_MS = 500L
    }
}
