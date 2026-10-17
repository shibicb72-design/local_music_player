package com.shibi.musicplayer.domain

/**
 * How the mini player draws the current output. [UNKNOWN] is the generic
 * Bluetooth glyph used when a Bluetooth route cannot be classified, including
 * when [android.permission.BLUETOOTH_CONNECT] is denied on API 31+.
 */
enum class RouteIcon {
    PHONE,
    HEADPHONES,
    SPEAKER,
    CAR,
    UNKNOWN,
}

data class BondedClass(
    val name: String,
    val majorClass: Int,
    val deviceClass: Int,
)

data class RouteInput(
    val deviceType: Int,
    val productName: String = "",
    val bonded: List<BondedClass> = emptyList(),
    val carMode: Boolean = false,
    val bluetoothConnectGranted: Boolean = true,
)

data class RoutePresentation(
    val icon: RouteIcon,
    val deviceName: String,
    val showDeviceName: Boolean,
    val showBluetoothGlyph: Boolean,
) {
    companion object {
        fun phone(label: String = ""): RoutePresentation = RoutePresentation(
            icon = RouteIcon.PHONE,
            deviceName = label,
            showDeviceName = true,
            showBluetoothGlyph = false,
        )
    }
}

data class ListedDevice(
    val id: Int,
    val type: Int,
    val productName: String,
)

object OutputRoute {
    const val TYPE_BUS = 21

    /** [android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO]. */
    const val MAJOR_AUDIO_VIDEO = 0x0400

    const val CLASS_WEARABLE_HEADSET = 0x0404
    const val CLASS_HANDSFREE = 0x0408
    const val CLASS_LOUDSPEAKER = 0x0414
    const val CLASS_HEADPHONES = 0x0418
    const val CLASS_PORTABLE_AUDIO = 0x041C
    const val CLASS_CAR_AUDIO = 0x0420
    const val CLASS_HIFI_AUDIO = 0x0428

    fun present(input: RouteInput): RoutePresentation {
        val icon = classify(input)
        val name = input.productName.trim()
        val bluetooth = isBluetoothTransport(input.deviceType)
        return RoutePresentation(
            icon = icon,
            deviceName = name,
            showDeviceName = true,
            showBluetoothGlyph = bluetooth && icon != RouteIcon.PHONE,
        )
    }

    fun classify(input: RouteInput): RouteIcon {
        if (input.deviceType == TYPE_BUS || input.carMode) return RouteIcon.CAR

        val bonded = if (input.bluetoothConnectGranted) {
            matchBonded(input.productName, input.bonded)
        } else {
            null
        }
        val bluetooth = isBluetoothTransport(input.deviceType)
        if (bluetooth && bonded != null && isCarClass(bonded)) return RouteIcon.CAR
        if (bluetooth && !input.bluetoothConnectGranted && isAmbiguousBluetooth(input.deviceType)) {
            return RouteIcon.UNKNOWN
        }

        return when (input.deviceType) {
            AudioOutputs.TYPE_BUILTIN_SPEAKER,
            AudioOutputs.TYPE_BUILTIN_EARPIECE,
            -> RouteIcon.PHONE

            AudioOutputs.TYPE_WIRED_HEADSET,
            AudioOutputs.TYPE_WIRED_HEADPHONES,
            AudioOutputs.TYPE_USB_HEADSET,
            AudioOutputs.TYPE_BLE_HEADSET,
            AudioOutputs.TYPE_HEARING_AID,
            -> RouteIcon.HEADPHONES

            AudioOutputs.TYPE_BLE_SPEAKER,
            AudioOutputs.TYPE_USB_DEVICE,
            -> RouteIcon.SPEAKER

            AudioOutputs.TYPE_BLUETOOTH_SCO,
            AudioOutputs.TYPE_BLUETOOTH_A2DP,
            -> classifyBluetoothClass(bonded)

            else -> RouteIcon.UNKNOWN
        }
    }

    fun activeDevice(
        devices: List<ListedDevice>,
        preferredId: Int?,
        routedIds: List<Int>,
    ): ListedDevice? {
        preferredId?.let { id -> devices.firstOrNull { it.id == id } }?.let { return it }
        for (id in routedIds) {
            devices.firstOrNull { it.id == id }?.let { return it }
        }
        return devices.firstOrNull { it.type == AudioOutputs.TYPE_BUILTIN_SPEAKER }
            ?: devices.firstOrNull()
    }

    private fun classifyBluetoothClass(bonded: BondedClass?): RouteIcon {
        if (bonded == null) return RouteIcon.UNKNOWN
        if (isCarClass(bonded)) return RouteIcon.CAR
        return when (bonded.deviceClass) {
            CLASS_WEARABLE_HEADSET,
            CLASS_HEADPHONES,
            -> RouteIcon.HEADPHONES

            CLASS_LOUDSPEAKER,
            CLASS_HIFI_AUDIO,
            CLASS_PORTABLE_AUDIO,
            -> RouteIcon.SPEAKER

            else -> RouteIcon.UNKNOWN
        }
    }

    private fun isCarClass(bonded: BondedClass): Boolean {
        val major = if (bonded.majorClass != 0) {
            bonded.majorClass
        } else {
            bonded.deviceClass and MAJOR_MASK
        }
        if (major != MAJOR_AUDIO_VIDEO) return false
        return bonded.deviceClass == CLASS_HANDSFREE || bonded.deviceClass == CLASS_CAR_AUDIO
    }

    private fun matchBonded(productName: String, bonded: List<BondedClass>): BondedClass? {
        val key = productName.trim()
        if (key.isEmpty()) return null
        return bonded.firstOrNull { it.name.trim().equals(key, ignoreCase = true) }
    }

    private fun isBluetoothTransport(type: Int): Boolean = isAmbiguousBluetooth(type) ||
        type == AudioOutputs.TYPE_BLE_HEADSET ||
        type == AudioOutputs.TYPE_BLE_SPEAKER ||
        type == AudioOutputs.TYPE_HEARING_AID

    private fun isAmbiguousBluetooth(type: Int): Boolean =
        type == AudioOutputs.TYPE_BLUETOOTH_SCO || type == AudioOutputs.TYPE_BLUETOOTH_A2DP

    private const val MAJOR_MASK = 0x1F00
}
