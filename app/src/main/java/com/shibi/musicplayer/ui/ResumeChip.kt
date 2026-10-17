package com.shibi.musicplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.ui.background.ContrastSurface

/** Test tag of the "Resumed at 12:34 · Start over" chip. */
internal const val ResumeChipTag = "resume_chip"

/**
 * "Resumed at 12:34 · Start over" on the same solid dark pill as the gesture chips, so its
 * white text and button read the same over any frame, in the box, the pane and fullscreen.
 */
@Composable
internal fun ResumeChip(positionMs: Long, onStartOver: () -> Unit, modifier: Modifier = Modifier) {
    ContrastSurface(
        color = ChipFill,
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier.testTag(ResumeChipTag),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.video_resumed_at, elapsedLabel(positionMs)) + " ·",
                style = MaterialTheme.typography.labelLarge,
                color = LocalContentColor.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(
                onClick = onStartOver,
                colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
            ) {
                Text(
                    text = stringResource(R.string.video_start_over),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Fades the chip in and out, keeping its last position while it fades. */
@Composable
internal fun ResumeChipSlot(
    resumedAtMs: Long?,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf(resumedAtMs) }
    if (resumedAtMs != null) shown = resumedAtMs
    AnimatedVisibility(
        visible = resumedAtMs != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        shown?.let { position -> ResumeChip(positionMs = position, onStartOver = onStartOver) }
    }
}
