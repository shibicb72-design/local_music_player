package com.shibi.musicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.ui.background.ContrastSurface

/** Test tag of the transient fullscreen chip. */
internal const val GestureChipTag = "gesture_chip"

/** How long a chip stays after the last change before it fades. */
internal const val GestureChipHoldMs = 800L

/** Solid, never see-through: the chip reads the same over any frame of the video. */
private val ChipFill = Color(0xFF1C1C22)

/**
 * The only feedback for the fullscreen swipes, double-taps and aspect button: an icon and a
 * short text (`8 / 15`, `60%`, `+0:30`, `Fill / crop`) on a solid dark pill. There is no bar
 * or slider; the content colour comes from the pill itself.
 */
@Composable
internal fun GestureChip(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    ContrastSurface(
        color = ChipFill,
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier.testTag(GestureChipTag),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 20.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalContentColor.current,
            )
        }
    }
}
