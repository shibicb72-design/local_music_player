package com.shibi.musicplayer.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.ui.background.ContrastSurface
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.ui.background.contentColorForContainer

/**
 * The bar that replaces a list's header while a selection is active: Cancel, the count,
 * Select all, then the screen's own [actions]. A solid [ContrastSurface], so its text and
 * icons take their colour from the bar itself, never from the window.
 */
@Composable
fun SelectionTopBar(
    count: Int,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val chrome = LocalBackgroundChrome.current
    val barColor = if (chrome.isLight) {
        chrome.frost.copy(alpha = 1f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    ContrastSurface(
        color = barColor,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        val content = MaterialTheme.colorScheme.onSurface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cancel_selection),
                    tint = content,
                )
            }
            Text(
                text = pluralStringResource(R.plurals.selection_count, count, count),
                style = MaterialTheme.typography.titleMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onSelectAll,
                colors = ButtonDefaults.textButtonColors(contentColor = content),
            ) {
                Text(stringResource(R.string.select_all), maxLines = 1)
            }
            actions()
        }
    }
}

/**
 * A round check for a selectable row. It springs in (scale 0.6 to 1 plus alpha) when
 * [selected] and out when not; under reduce motion it snaps. The ring is a fixed white on a
 * dark scrim so it reads over any artwork; the filled disc takes its tick colour from itself.
 */
@Composable
fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val progress = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
        },
        label = "selectionCheck",
    )
    val disc = MaterialTheme.colorScheme.primary
    val tick = contentColorForContainer(disc)
    val description = stringResource(if (selected) R.string.selected else R.string.not_selected)
    Box(
        modifier = modifier
            .size(24.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    val value = progress.value
                    val scale = 0.6f + 0.4f * value
                    scaleX = scale
                    scaleY = scale
                    alpha = value.coerceIn(0f, 1f)
                }
                .background(disc, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = tick,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
