package com.shibi.musicplayer.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.app.UiModeManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shibi.musicplayer.domain.BondedClass
import com.shibi.musicplayer.domain.ListedDevice
import com.shibi.musicplayer.domain.OutputRoute
import com.shibi.musicplayer.domain.RouteInput
import com.shibi.musicplayer.domain.RoutePresentation

@Composable
fun rememberPlaybackRoute(preferredDeviceId: Int?): State<RoutePresentation> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember(context) {
        context.getSystemService(AudioManager::class.java)
    }
    val state = remember(audioManager) {
        mutableStateOf(readPlaybackRoute(context, audioManager, preferredDeviceId))
    }
    DisposableEffect(audioManager, preferredDeviceId, lifecycleOwner) {
        fun refresh() {
            state.value = readPlaybackRoute(context, audioManager, preferredDeviceId)
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) = refresh()
        }
        val filter = IntentFilter().apply {
            addAction(UiModeManager.ACTION_ENTER_CAR_MODE)
            addAction(UiModeManager.ACTION_EXIT_CAR_MODE)
        }
        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }.isSuccess
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refresh()
        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (registered) {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }
    }
    return state
}

internal fun readPlaybackRoute(
    context: Context,
    audioManager: AudioManager,
    preferredDeviceId: Int?,
): RoutePresentation {
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { device ->
        ListedDevice(
            id = device.id,
            type = device.type,
            productName = device.productName?.toString().orEmpty(),
        )
    }
    val active = OutputRoute.activeDevice(
        devices = devices,
        preferredId = preferredDeviceId,
        routedIds = routedDeviceIds(audioManager),
    ) ?: return RoutePresentation.phone()
    val granted = bluetoothConnectGranted(context)
    val bonded = if (granted) bondedClasses(context) else null
    return OutputRoute.present(
        RouteInput(
            deviceType = active.type,
            productName = active.productName,
            bonded = bonded.orEmpty(),
            carMode = carMode(context),
            bluetoothConnectGranted = granted && bonded != null,
        ),
    )
}

private fun bluetoothConnectGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun bondedClasses(context: Context): List<BondedClass>? {
    return try {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        adapter.bondedDevices.orEmpty().map { device ->
            val clazz = device.bluetoothClass
            BondedClass(
                name = device.name.orEmpty(),
                majorClass = clazz?.majorDeviceClass ?: 0,
                deviceClass = clazz?.deviceClass ?: 0,
            )
        }
    } catch (_: SecurityException) {
        null
    }
}

private fun carMode(context: Context): Boolean {
    val uiMode = context.getSystemService(UiModeManager::class.java) ?: return false
    return uiMode.currentModeType == Configuration.UI_MODE_TYPE_CAR
}

private fun routedDeviceIds(audioManager: AudioManager): List<Int> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return emptyList()
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    return audioManager.getAudioDevicesForAttributes(attributes).map { it.id }
}
