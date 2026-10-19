package com.shibi.musicplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.PlaybackSpeed

private val MenuContainer = Color(0xFF262626)

/** Test tag of the speed button. */
internal const val SpeedButtonTag = "speed_button"

/** Test tag of one menu row, from its label (`speed_option_1.5×`). */
internal fun speedOptionTag(rate: Float): String = "speed_option_${PlaybackSpeed.label(rate)}"

/**
 * The speed text button and its menu of [PlaybackSpeed.Options], the current one checked. The
 * menu is a popup that draws its own colours (white on 0xFF262626, because the player scheme
 * makes onSurface white). [onMenuOpenChange] lets the player keep its controls up while the menu
 * is open; it is told "closed" if the button leaves the composition with the menu open.
 */
@Composable
internal fun SpeedButton(
    speed: Float,
    onSetSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier,
    initialOpen: Boolean = false,
    onMenuOpenChange: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(initialOpen) }
    LaunchedEffect(expanded) { onMenuOpenChange(expanded) }
    DisposableEffect(Unit) { onDispose { onMenuOpenChange(false) } }
    val label = PlaybackSpeed.label(speed)
    val description = stringResource(R.string.video_speed, label)
    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.textButtonColors(contentColor = OnVideo),
            modifier = Modifier
                .testTag(SpeedButtonTag)
                .semantics { contentDescription = description },
        ) {
            Text(text = label)
        }
        // The player's scheme makes onSurface white while the popup's surfaceContainer stays
        // themed (light in light mode), so the menu owns both colours: white on near-black.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MenuContainer,
        ) {
            PlaybackSpeed.Options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(PlaybackSpeed.label(option)) },
                    trailingIcon = if (option == speed) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.video_speed_current),
                            )
                        }
                    } else {
                        null
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = OnVideo,
                        trailingIconColor = OnVideo,
                    ),
                    onClick = {
                        onSetSpeed(option)
                        expanded = false
                    },
                    modifier = Modifier.testTag(speedOptionTag(option)),
                )
            }
        }
    }
}
