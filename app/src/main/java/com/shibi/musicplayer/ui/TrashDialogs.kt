package com.shibi.musicplayer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.MediaKind
import com.shibi.musicplayer.domain.TrashItem
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

/** Lists what the system would not move. */
@Composable
internal fun TrashFailedDialog(
    items: List<TrashItem>,
    onDismiss: () -> Unit,
) {
    val dialogColor = LocalBackgroundChrome.current.dialog
    val onDialog = contentColorForContainer(dialogColor)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogColor,
        titleContentColor = onDialog,
        textContentColor = onDialog,
        title = { Text(pluralStringResource(R.plurals.trash_failed_title, items.size, items.size)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.trash_failed_body))
                items.take(MAX_LISTED_TITLES).forEach { Text(it.title) }
                val more = items.size - MAX_LISTED_TITLES
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
