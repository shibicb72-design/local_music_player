package com.shibi.musicplayer.playlist

import com.shibi.musicplayer.domain.HistoryType
import com.shibi.musicplayer.domain.MediaKind
import com.shibi.musicplayer.domain.TrashItem
import com.shibi.musicplayer.domain.TrashPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the move-to-trash flow is. */
sealed interface TrashStep {
    data object Idle : TrashStep

    /** Waiting for the user to confirm trashing a whole list. */
    data class ConfirmGroup(val items: List<TrashItem>) : TrashStep

    /** The system popup is up: [trash] true moves [items] to the trash, false brings them back. */
    data class AwaitSystem(val items: List<TrashItem>, val trash: Boolean) : TrashStep
}

/** The snackbar after a move: "Moved N to trash" with an optional Undo. */
data class TrashNotice(val movedCount: Int, val canUndo: Boolean)

data class TrashUiState(
    val step: TrashStep = TrashStep.Idle,
    val notice: TrashNotice? = null,
    val failed: List<TrashItem> = emptyList(),
    val unsupported: Boolean = false,
)

/**
 * The trash and undo state machine, free of Android. The cleanup snapshot is captured before the
 * system popup, because the library watcher prunes missing songs soon after the system trash and
 * Undo could no longer restore playlist membership.
 */
class TrashController(
    private val scope: CoroutineScope,
    private val supported: () -> Boolean,
    private val stillVisible: suspend (List<String>) -> Set<String>,
    private val capture: suspend (List<TrashItem>) -> CleanupSnapshot,
    private val cleanup: suspend (List<TrashItem>) -> CleanupSnapshot,
    private val restore: suspend (CleanupSnapshot) -> Unit,
    private val onTrashed: (List<TrashItem>) -> Unit,
) {
    private val _state = MutableStateFlow(TrashUiState())
    val state: StateFlow<TrashUiState> = _state.asStateFlow()

    private var captured = CleanupSnapshot()
    private var undoSnapshot: CleanupSnapshot? = null
    private var lastTrashed: List<TrashItem> = emptyList()
    private var capturing = false

    fun request(items: List<TrashItem>, wholeList: Boolean) {
        if (items.isEmpty() || _state.value.step != TrashStep.Idle) return
        if (!supported()) {
            _state.update { it.copy(unsupported = true) }
            return
        }
        if (TrashPlan.needsGroupWarning(items.size, wholeList)) {
            _state.update { it.copy(step = TrashStep.ConfirmGroup(items)) }
        } else {
            beginSystem(items)
        }
    }

    fun confirmGroup() {
        val step = _state.value.step as? TrashStep.ConfirmGroup ?: return
        beginSystem(step.items)
    }

    fun dismiss() {
        if (_state.value.step is TrashStep.ConfirmGroup) {
            _state.update { it.copy(step = TrashStep.Idle) }
        }
    }

    fun onSystemResult(ok: Boolean) {
        val step = _state.value.step as? TrashStep.AwaitSystem ?: return
        _state.update { it.copy(step = TrashStep.Idle) }
        if (step.trash) {
            if (ok) finishTrash(step.items) else captured = CleanupSnapshot()
        } else {
            val snapshot = undoSnapshot
            undoSnapshot = null
            _state.update { it.copy(notice = null) }
            if (ok && snapshot != null) scope.launch { restore(snapshot) }
        }
    }

    fun undo() {
        if (undoSnapshot == null || _state.value.step != TrashStep.Idle) return
        _state.update { it.copy(step = TrashStep.AwaitSystem(lastTrashed, trash = false)) }
    }

    fun noticeShown() {
        _state.update { it.copy(notice = null) }
    }

    fun failedShown() {
        _state.update { it.copy(failed = emptyList()) }
    }

    private fun beginSystem(items: List<TrashItem>) {
        // The popup opens only after the capture is done.
        if (capturing) return
        capturing = true
        scope.launch {
            try {
                captured = capture(items)
            } finally {
                capturing = false
            }
            _state.update { it.copy(step = TrashStep.AwaitSystem(items, trash = true)) }
        }
    }

    private fun finishTrash(items: List<TrashItem>) {
        scope.launch {
            val visible = stillVisible(items.map { it.uri })
            val failed = TrashPlan.failed(items, visible)
            val trashed = items - failed.toSet()
            if (trashed.isNotEmpty()) {
                onTrashed(trashed)
                val taken = cleanup(trashed)
                undoSnapshot = captured.only(trashed) + taken
                lastTrashed = trashed
            }
            captured = CleanupSnapshot()
            _state.update {
                it.copy(
                    notice = if (trashed.isEmpty()) it.notice else TrashNotice(trashed.size, canUndo = true),
                    failed = failed,
                )
            }
        }
    }

    private fun CleanupSnapshot.only(items: List<TrashItem>): CleanupSnapshot {
        val songIds = items.filter { it.kind == MediaKind.Song }.map { it.id }.toSet()
        val videoIds = items.filter { it.kind == MediaKind.Video }.map { it.id }.toSet()
        val videoUris = items.filter { it.kind == MediaKind.Video }.map { it.uri }.toSet()
        return CleanupSnapshot(
            playlistSongs = playlistSongs.filter { it.trackId in songIds },
            recentPlays = recentPlays.filter { it.trackId in songIds },
            history = history.filter {
                (it.type == HistoryType.Song.name && it.mediaId in songIds) ||
                    (it.type == HistoryType.Video.name && it.mediaId in videoIds)
            },
            videoProgress = videoProgress.filter { it.uri in videoUris },
        )
    }
}
