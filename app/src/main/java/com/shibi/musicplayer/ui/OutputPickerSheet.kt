package com.shibi.musicplayer.ui

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.AudioOutput
import com.shibi.musicplayer.domain.AudioOutputs
import com.shibi.musicplayer.domain.OutputKind
import com.shibi.musicplayer.domain.RawOutput

@Composable
fun rememberAudioOutputs(): State<List<AudioOutput>> {
    val context = LocalContext.current
    val phoneLabel = stringResource(R.string.output_this_phone)
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val state = remember(audioManager, phoneLabel) {
        mutableStateOf(readOutputs(audioManager, phoneLabel))
    }
    DisposableEffect(audioManager, phoneLabel) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                state.value = readOutputs(audioManager, phoneLabel)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                state.value = readOutputs(audioManager, phoneLabel)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputPickerSheet(
    outputs: List<AudioOutput>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        OutputPickerContent(
            outputs = outputs,
            selectedId = selectedId,
            onSelect = { id ->
                onSelect(id)
                onDismiss()
            },
        )
    }
}

@Composable
internal fun OutputPickerContent(
    outputs: List<AudioOutput>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val effective = effectiveOutput(outputs, selectedId)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.choose_output),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        outputs.forEach { output ->
            val selected = output.id == effective?.id
            ListItem(
                headlineContent = {
                    Text(
                        text = output.name,
                        color = if (selected) OutputAccent else Color.Unspecified,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = iconFor(output.kind),
                        contentDescription = null,
                    )
                },
                trailingContent = if (selected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = OutputAccent,
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.clickable {
                    onSelect(output.id)
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

internal val OutputAccent = Color(0xFFB9A8FF)

internal fun effectiveOutput(outputs: List<AudioOutput>, selectedId: Int?): AudioOutput? =
    outputs.firstOrNull { it.id == AudioOutputs.selectedId(outputs, selectedId) }
        ?: outputs.firstOrNull {
            it.kind == OutputKind.BLUETOOTH ||
                it.kind == OutputKind.WIRED ||
                it.kind == OutputKind.USB
        }
        ?: outputs.firstOrNull { it.kind == OutputKind.PHONE_SPEAKER }

private fun iconFor(kind: OutputKind): ImageVector = when (kind) {
    OutputKind.PHONE_SPEAKER -> Icons.Filled.Smartphone
    OutputKind.WIRED -> Icons.Filled.Headphones
    OutputKind.USB -> Icons.Filled.Usb
    OutputKind.BLUETOOTH -> Icons.Filled.Bluetooth
}

private fun readOutputs(audioManager: AudioManager, phoneLabel: String): List<AudioOutput> {
    val raw = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { device ->
        RawOutput(device.id, device.type, device.productName.toString())
    }
    return AudioOutputs.buildList(raw, phoneLabel)
}
