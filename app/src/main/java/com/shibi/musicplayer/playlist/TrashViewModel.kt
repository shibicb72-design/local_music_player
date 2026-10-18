package com.shibi.musicplayer.playlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shibi.musicplayer.domain.TrashItem
import com.shibi.musicplayer.library.MediaTrash
import com.shibi.musicplayer.video.VideoProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Holds the trash flow across rotation and folding; the UI launches the system popup. */
class TrashViewModel internal constructor(
    application: Application,
    controllerFactory: (CoroutineScope, (List<TrashItem>) -> Unit) -> TrashController,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, { scope, onTrashed ->
        val cleanup = TrashCleanup(
            PlaylistDatabase.get(application).playlistDao(),
            VideoProgressRepository.get(application),
        )
        TrashController(
            scope = scope,
            supported = MediaTrash::isSupported,
            stillVisible = { uris -> withContext(Dispatchers.IO) { MediaTrash.stillVisible(application, uris) } },
            capture = { items -> withContext(Dispatchers.IO) { cleanup.snapshot(items) } },
            cleanup = { items -> withContext(Dispatchers.IO) { cleanup.remove(items) } },
            restore = { snapshot -> withContext(Dispatchers.IO) { cleanup.restore(snapshot) } },
            onTrashed = onTrashed,
        )
    })

    /** Set by the UI: drops trashed items from the queue and stops a trashed video. */
    var onTrashed: (List<TrashItem>) -> Unit = {}

    private val controller = controllerFactory(viewModelScope) { items -> onTrashed(items) }

    val state: StateFlow<TrashUiState> = controller.state

    fun request(items: List<TrashItem>, wholeList: Boolean) = controller.request(items, wholeList)

    fun confirmGroup() = controller.confirmGroup()

    fun dismiss() = controller.dismiss()

    fun onSystemResult(ok: Boolean) = controller.onSystemResult(ok)

    fun undo() = controller.undo()

    fun noticeShown() = controller.noticeShown()

    fun failedShown() = controller.failedShown()

    fun unsupportedShown() = controller.unsupportedShown()
}
