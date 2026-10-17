package com.shibi.musicplayer.domain

enum class OutputKind {
    PHONE_SPEAKER,
    WIRED,
    BLUETOOTH,
    USB,
}

data class RawOutput(
    val id: Int,
    val type: Int,
    val name: String,
)

data class AudioOutput(
    val id: Int,
    val kind: OutputKind,
    val name: String,
)

object AudioOutputs {
    /** True when there is nothing to choose: no outputs, or only the phone itself. */
    fun onlyThisPhone(outputs: List<AudioOutput>): Boolean =
        outputs.none { it.kind != OutputKind.PHONE_SPEAKER }

    const val TYPE_BUILTIN_EARPIECE = 1
    const val TYPE_BUILTIN_SPEAKER = 2
    const val TYPE_WIRED_HEADSET = 3
    const val TYPE_WIRED_HEADPHONES = 4
    const val TYPE_BLUETOOTH_SCO = 7
    const val TYPE_BLUETOOTH_A2DP = 8
    const val TYPE_USB_DEVICE = 11
    const val TYPE_TELEPHONY = 18
    const val TYPE_USB_HEADSET = 22
    const val TYPE_HEARING_AID = 23
    const val TYPE_BLE_HEADSET = 26
    const val TYPE_BLE_SPEAKER = 27

    fun kindOf(type: Int): OutputKind? = when (type) {
        TYPE_BUILTIN_SPEAKER -> OutputKind.PHONE_SPEAKER
        TYPE_WIRED_HEADSET, TYPE_WIRED_HEADPHONES -> OutputKind.WIRED
        TYPE_USB_DEVICE, TYPE_USB_HEADSET -> OutputKind.USB
        TYPE_BLUETOOTH_A2DP,
        TYPE_HEARING_AID,
        TYPE_BLE_HEADSET,
        TYPE_BLE_SPEAKER,
        -> OutputKind.BLUETOOTH

        else -> null
    }

    fun buildList(raw: List<RawOutput>, phoneLabel: String): List<AudioOutput> {
        val seen = mutableSetOf<Pair<OutputKind, String>>()
        return raw.mapNotNull { output ->
            val kind = kindOf(output.type) ?: return@mapNotNull null
            val name = if (kind == OutputKind.PHONE_SPEAKER) phoneLabel else output.name
            if (!seen.add(kind to name)) return@mapNotNull null
            AudioOutput(output.id, kind, name)
        }.sortedBy { it.kind.orderRank }
    }

    fun selectedId(list: List<AudioOutput>, preferredId: Int?): Int? =
        list.firstOrNull { it.id == preferredId }?.id

    private val OutputKind.orderRank: Int
        get() = when (this) {
            OutputKind.PHONE_SPEAKER -> 0
            OutputKind.WIRED -> 1
            OutputKind.USB -> 2
            OutputKind.BLUETOOTH -> 3
        }
}
