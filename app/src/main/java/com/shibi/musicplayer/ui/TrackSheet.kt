package com.shibi.musicplayer.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.ui.background.ContrastSurface
import com.shibi.musicplayer.ui.background.LocalBackgroundChrome
import com.shibi.musicplayer.video.ExternalTrackKind
import com.shibi.musicplayer.video.SubtitleFiles
import com.shibi.musicplayer.video.SubtitleTextSize
import com.shibi.musicplayer.video.TrackDelay
import com.shibi.musicplayer.video.TrackMessage
import com.shibi.musicplayer.video.TrackOption
import com.shibi.musicplayer.video.TrackSheetState

/** Test tag of the tracks sheet's content. */
internal const val TrackSheetTag = "track_sheet"

/** Everything the sheet can ask the player to do. */
internal class TrackSheetActions(
    val onSelectAudio: (Int) -> Unit = {},
    val onSelectSubtitle: (Int) -> Unit = {},
    val onStepAudioDelay: (Int) -> Unit = {},
    val onStepSubtitleDelay: (Int) -> Unit = {},
    val onTextSize: (SubtitleTextSize) -> Unit = {},
    val onHardwareDecoding: (Boolean) -> Unit = {},
    val onLoadFile: (ExternalTrackKind, String) -> Unit = { _, _ -> },
    /** The system picker covers the app (true) or returned (false); the video plays on under it. */
    val onPickerShown: (Boolean) -> Unit = {},
)

/**
 * Audio and subtitles for the open video, over the inline box, the pane or fullscreen. The
 * files come from the system picker: audio files only for audio; any file for subtitles,
 * and the view model loads only subtitle extensions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackSheet(
    state: TrackSheetState,
    actions: TrackSheetActions,
    onDismiss: () -> Unit,
) {
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        actions.onPickerShown(false)
        uri?.let { actions.onLoadFile(ExternalTrackKind.Audio, it.toString()) }
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        actions.onPickerShown(false)
        uri?.let { actions.onLoadFile(ExternalTrackKind.Subtitle, it.toString()) }
    }
    // Marked before the launch (the activity stops during it), unmarked if it never opened.
    fun pick(picker: ActivityResultLauncher<Array<String>>, types: String) {
        actions.onPickerShown(true)
        try {
            picker.launch(arrayOf(types))
        } catch (missing: ActivityNotFoundException) {
            actions.onPickerShown(false)
        }
    }
    val sheetColor = LocalBackgroundChrome.current.sheet
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetColor,
        scrimColor = OutputScrim,
        shape = SheetShape,
        dragHandle = null,
    ) {
        ContrastSurface(color = sheetColor) {
            TrackSheetContent(
                state = state,
                actions = actions,
                onPickAudio = { pick(audioPicker, "audio/*") },
                onPickSubtitle = { pick(subtitlePicker, "*/*") },
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * The sheet's body, without the sheet window, so screenshots can draw it. Every colour is
 * the container's own `onSurface` roles (the caller wraps it in a [ContrastSurface]), so it
 * reads on the dark sheet and on every pastel sheet alike. Delays are steppers, not sliders.
 */
@Composable
internal fun TrackSheetContent(
    state: TrackSheetState,
    actions: TrackSheetActions,
    onPickAudio: () -> Unit,
    onPickSubtitle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 24.dp)
            .testTag(TrackSheetTag),
    ) {
        Text(
            text = stringResource(R.string.video_tracks),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        state.message?.let { message -> MessageLine(message) }

        SectionHeading(stringResource(R.string.tracks_audio))
        if (state.audio.isEmpty()) {
            Text(
                text = stringResource(R.string.tracks_no_audio),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        state.audio.forEachIndexed { index, option ->
            TrackRow(option = option, number = index + 1, onClick = { actions.onSelectAudio(option.id) })
        }
        LoadRow(
            icon = Icons.Rounded.AudioFile,
            label = stringResource(R.string.tracks_load_audio),
            loading = state.loading == ExternalTrackKind.Audio,
            onClick = onPickAudio,
        )
        DelayStepper(
            title = stringResource(R.string.tracks_audio_delay),
            delay = TrackDelay.Audio,
            delayMs = state.audioDelayMs,
            onStep = actions.onStepAudioDelay,
        )

        SectionHeading(stringResource(R.string.tracks_subtitles))
        // "Off" is first; the rest are numbered from 1 when libVLC gives no name.
        state.subtitles.forEachIndexed { index, option ->
            TrackRow(option = option, number = index, onClick = { actions.onSelectSubtitle(option.id) })
        }
        LoadRow(
            icon = Icons.Rounded.Subtitles,
            label = stringResource(R.string.tracks_load_subtitle),
            loading = state.loading == ExternalTrackKind.Subtitle,
            onClick = onPickSubtitle,
        )
        DelayStepper(
            title = stringResource(R.string.tracks_subtitle_delay),
            delay = TrackDelay.Subtitle,
            delayMs = state.subtitleDelayMs,
            onStep = actions.onStepSubtitleDelay,
        )
        ChoiceSetting(
            title = stringResource(R.string.tracks_text_size),
            hint = stringResource(R.string.tracks_text_size_hint),
            choices = SubtitleTextSize.entries.map { size -> size to size.label() },
            selected = state.textSize,
            onSelect = actions.onTextSize,
        )
        ChoiceSetting(
            title = stringResource(R.string.tracks_decoding),
            hint = stringResource(R.string.tracks_decoding_hint),
            choices = listOf(
                true to Choice(stringResource(R.string.tracks_decoding_auto)),
                false to Choice(stringResource(R.string.tracks_decoding_off)),
            ),
            selected = state.hardwareDecoding,
            onSelect = actions.onHardwareDecoding,
        )
    }
}

private class Choice(val text: String, val description: String = text)

@Composable
private fun SubtitleTextSize.label(): Choice = when (this) {
    SubtitleTextSize.Small -> Choice(
        stringResource(R.string.tracks_size_small),
        stringResource(R.string.tracks_size_small_description),
    )
    SubtitleTextSize.Medium -> Choice(
        stringResource(R.string.tracks_size_medium),
        stringResource(R.string.tracks_size_medium_description),
    )
    SubtitleTextSize.Large -> Choice(
        stringResource(R.string.tracks_size_large),
        stringResource(R.string.tracks_size_large_description),
    )
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** Short, on the sheet's own colours; the details are in Settings > Debugging / Error log. */
@Composable
private fun MessageLine(message: TrackMessage) {
    val text = when (message) {
        TrackMessage.NotASubtitle -> stringResource(R.string.tracks_error_not_subtitle, SubtitleFiles.extensionsLabel())
        TrackMessage.SubtitleFailed -> stringResource(R.string.tracks_error_subtitle)
        TrackMessage.AudioFailed -> stringResource(R.string.tracks_error_audio)
    }
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.onSurface.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = scheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
    }
}

@Composable
private fun TrackRow(option: TrackOption, number: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = when {
        option.isOff -> stringResource(R.string.tracks_off)
        option.label != null -> option.label
        else -> stringResource(R.string.tracks_unnamed, number)
    }
    val detail = listOfNotNull(
        option.detail,
        if (option.external) stringResource(R.string.tracks_from_file) else null,
    ).joinToString(" · ").takeIf { it.isNotEmpty() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (option.selected) scheme.onSurface.copy(alpha = 0.10f) else Color.Transparent)
            .selectable(selected = option.selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (option.selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = scheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LoadRow(icon: ImageVector, label: String, loading: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    color = scheme.onSurface,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(imageVector = icon, contentDescription = null, tint = scheme.onSurface)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (loading) stringResource(R.string.tracks_loading) else label,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
        )
    }
}

/** − value +; whole steps only, so a stray drag can never smear the sync. */
@Composable
private fun DelayStepper(title: String, delay: TrackDelay, delayMs: Long, onStep: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val buttonColors = IconButtonDefaults.iconButtonColors(
        containerColor = scheme.onSurface.copy(alpha = 0.10f),
        contentColor = scheme.onSurface,
        disabledContainerColor = scheme.onSurface.copy(alpha = 0.05f),
        disabledContentColor = scheme.onSurface.copy(alpha = 0.38f),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onStep(-1) },
            enabled = delay.canStep(delayMs, -1),
            colors = buttonColors,
            shape = CircleShape,
        ) {
            Icon(imageVector = Icons.Rounded.Remove, contentDescription = stringResource(R.string.tracks_earlier))
        }
        Text(
            text = delay.label(delayMs),
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
            modifier = Modifier
                .widthIn(min = 72.dp)
                .padding(horizontal = 4.dp)
                .semantics { contentDescription = "$title ${delay.label(delayMs)}" },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(
            onClick = { onStep(1) },
            enabled = delay.canStep(delayMs, 1),
            colors = buttonColors,
            shape = CircleShape,
        ) {
            Icon(imageVector = Icons.Rounded.Add, contentDescription = stringResource(R.string.tracks_later))
        }
    }
}

/** Pills: the chosen one is filled with the content colour, its text in the container colour. */
@Composable
private fun <T> ChoiceSetting(
    title: String,
    hint: String,
    choices: List<Pair<T, Choice>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(start = 12.dp, top = 12.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (value, choice) ->
                val chosen = value == selected
                Box(
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .widthIn(min = 56.dp)
                        .clip(CircleShape)
                        .background(if (chosen) scheme.onSurface else Color.Transparent)
                        .border(BorderStroke(1.dp, scheme.onSurface.copy(alpha = 0.5f)), CircleShape)
                        .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(value) })
                        .semantics { contentDescription = choice.description }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = choice.text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (chosen) scheme.surface else scheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(text = hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}
