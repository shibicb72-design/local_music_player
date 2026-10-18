package com.shibi.musicplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.MediaKind
import com.shibi.musicplayer.domain.TrashFailReason
import com.shibi.musicplayer.domain.TrashFailure
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.background.contentColorForContainer

private const val MAX_LISTED_TITLES = 20

/** Asks before a whole list goes to the trash. */
@Composable
internal fun GroupTrashWarning(
    count: Int,
    kind: MediaKind,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogColor = LocalBackgroundChrome.current.dialog
    val onDialog = contentColorForContainer(dialogColor)
    val body = when (kind) {
        MediaKind.Song -> pluralStringResource(R.plurals.trash_group_songs, count, count)
        MediaKind.Video -> pluralStringResource(R.plurals.trash_group_videos, count, count)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        titleContentColor = onDialog,
        textContentColor = onDialog,
        title = { Text(stringResource(R.string.trash_group_title)) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.move_to_trash), color = onDialog)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = onDialog)
            }
        },
    )
}

/** Says how many items moved and, per reason, which ones did not. */
@Composable
internal fun TrashFailedDialog(
    moved: Int,
    requested: Int,
    failures: List<TrashFailure>,
    onDismiss: () -> Unit,
) {
    val dialogColor = LocalBackgroundChrome.current.dialog
    val onDialog = contentColorForContainer(dialogColor)
    val failedCount = failures.sumOf { it.items.size }
    val title = if (moved > 0) {
        stringResource(R.string.trash_partial_title, moved, requested)
    } else {
        pluralStringResource(R.plurals.trash_none_title, failedCount, failedCount)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        titleContentColor = onDialog,
        textContentColor = onDialog,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                var budget = MAX_LISTED_TITLES
                failures.forEach { failure ->
                    val count = failure.items.size
                    Text(pluralStringResource(reasonPlural(failure.reason), count, count))
                    val shown = failure.items.take(budget)
                    budget -= shown.size
                    shown.forEach { Text(it.title) }
                    Spacer(Modifier.height(8.dp))
                }
                val more = failedCount - MAX_LISTED_TITLES
                if (more > 0) Text(stringResource(R.string.trash_failed_more, more))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok), color = onDialog)
            }
        },
    )
}

private fun reasonPlural(reason: TrashFailReason): Int = when (reason) {
    TrashFailReason.Removable -> R.plurals.trash_reason_removable
    TrashFailReason.NoPermission -> R.plurals.trash_reason_permission
    TrashFailReason.NotTrashed -> R.plurals.trash_reason_not_trashed
}
