package com.shibi.musicplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R

/**
 * Play all and Shuffle side by side, as on the artist, album and playlist pages. Shuffle's outline
 * and text take the page's own content colour, so they read on a dark backdrop and on a pastel one alike.
 */
@Composable
internal fun PlayShuffleButtons(
    enabled: Boolean,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    sideMargin: Dp = ListRowSideMargin,
) {
    val content = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sideMargin, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayAllButton(onClick = onPlayAll, enabled = enabled, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onShuffle,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = CircleShape,
            border = BorderStroke(1.5.dp, content.copy(alpha = if (enabled) 0.72f else 0.24f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = content,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.shuffle), maxLines = 1)
        }
    }
}
