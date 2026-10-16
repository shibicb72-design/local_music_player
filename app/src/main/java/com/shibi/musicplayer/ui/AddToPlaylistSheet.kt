package com.shibi.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.containsTamil
import com.shibi.musicplayer.ui.theme.Accent
import com.shibi.musicplayer.ui.theme.Base0
import com.shibi.musicplayer.ui.theme.NotoTamil
import com.shibi.musicplayer.ui.theme.Outline
import com.shibi.musicplayer.ui.theme.Secondary
import com.shibi.musicplayer.ui.theme.TextHigh
import com.shibi.musicplayer.ui.theme.TextMed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    trackTitle: String,
    playlists: List<PlaylistCardUi>,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = TextHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        AddToPlaylistContent(
            trackTitle = trackTitle,
            playlists = playlists,
            creating = creating,
            draftName = draft,
            onDraftChange = { draft = it },
            onStartCreate = { creating = true },
            onConfirmCreate = {
                if (draft.isNotBlank()) {
                    onCreate(draft)
                    onDismiss()
                }
            },
            onPick = { id ->
                onPick(id)
                onDismiss()
            },
        )
    }
}

@Composable
internal fun AddToPlaylistContent(
    trackTitle: String,
    playlists: List<PlaylistCardUi>,
    creating: Boolean,
    draftName: String,
    onDraftChange: (String) -> Unit,
    onStartCreate: () -> Unit,
    onConfirmCreate: () -> Unit,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.add_to_playlist),
            style = MaterialTheme.typography.titleLarge,
            color = TextHigh,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(
            text = trackTitle,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (containsTamil(trackTitle)) NotoTamil else null,
            color = TextMed,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 12.dp),
        )
        if (creating) {
            OutlinedTextField(
                value = draftName,
                onValueChange = onDraftChange,
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_name)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextHigh,
                    unfocusedTextColor = TextHigh,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Outline,
                    cursorColor = Secondary,
                    focusedLabelColor = Secondary,
                    unfocusedLabelColor = TextMed,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onConfirmCreate,
                enabled = draftName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Base0,
                    disabledContainerColor = Color.White.copy(alpha = 0.12f),
                    disabledContentColor = TextMed,
                ),
            ) {
                Text(stringResource(R.string.create_playlist))
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Accent.copy(alpha = 0.16f))
                    .clickable(onClick = onStartCreate)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(icon = Icons.Rounded.Add, filled = true)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.new_playlist),
                    style = MaterialTheme.typography.titleSmall,
                    color = Secondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (playlists.isEmpty() && !creating) {
            Text(
                text = stringResource(R.string.playlists_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMed,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        playlists.forEach { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPick(playlist.id) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(icon = Icons.AutoMirrored.Rounded.QueueMusic, filled = false)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = if (containsTamil(playlist.name)) NotoTamil else null,
                        color = TextHigh,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.song_count,
                            playlist.songCount,
                            playlist.songCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMed,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun IconBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
) {
    BoxBadge(filled = filled) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (filled) Color.White else TextHigh,
            modifier = Modifier.size(22.dp),
        )
    }
}

private val BadgeAccent = Color(0xFF5B3EE0)

@Composable
private fun BoxBadge(
    filled: Boolean,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                if (filled) BadgeAccent else Color.White.copy(alpha = 0.08f),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
