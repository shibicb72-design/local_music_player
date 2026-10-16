package com.shibi.musicplayer

import com.shibi.musicplayer.domain.AudioOutput
import com.shibi.musicplayer.domain.AudioOutputs
import com.shibi.musicplayer.domain.OutputKind
import com.shibi.musicplayer.domain.RawOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioOutputsTest {

    private val phone = "Phone speaker"

    @Test
    fun kindOf_mapsSupportedTypes() {
        assertEquals(OutputKind.PHONE_SPEAKER, AudioOutputs.kindOf(AudioOutputs.TYPE_BUILTIN_SPEAKER))
        assertEquals(OutputKind.WIRED, AudioOutputs.kindOf(AudioOutputs.TYPE_WIRED_HEADSET))
        assertEquals(OutputKind.WIRED, AudioOutputs.kindOf(AudioOutputs.TYPE_WIRED_HEADPHONES))
        assertEquals(OutputKind.USB, AudioOutputs.kindOf(AudioOutputs.TYPE_USB_DEVICE))
        assertEquals(OutputKind.USB, AudioOutputs.kindOf(AudioOutputs.TYPE_USB_HEADSET))
        assertEquals(OutputKind.BLUETOOTH, AudioOutputs.kindOf(AudioOutputs.TYPE_BLUETOOTH_A2DP))
        assertEquals(OutputKind.BLUETOOTH, AudioOutputs.kindOf(AudioOutputs.TYPE_HEARING_AID))
        assertEquals(OutputKind.BLUETOOTH, AudioOutputs.kindOf(AudioOutputs.TYPE_BLE_HEADSET))
        assertEquals(OutputKind.BLUETOOTH, AudioOutputs.kindOf(AudioOutputs.TYPE_BLE_SPEAKER))
    }

    @Test
    fun kindOf_dropsTypesUnfitForMusic() {
        assertNull(AudioOutputs.kindOf(AudioOutputs.TYPE_BUILTIN_EARPIECE))
        assertNull(AudioOutputs.kindOf(AudioOutputs.TYPE_BLUETOOTH_SCO))
        assertNull(AudioOutputs.kindOf(AudioOutputs.TYPE_TELEPHONY))
    }

    @Test
    fun kindOf_dropsUnknownTypes() {
        assertNull(AudioOutputs.kindOf(0))
        assertNull(AudioOutputs.kindOf(999))
    }

    @Test
    fun buildList_filtersUnsupportedTypes() {
        val list = AudioOutputs.buildList(
            listOf(
                RawOutput(1, AudioOutputs.TYPE_BUILTIN_EARPIECE, "Earpiece"),
                RawOutput(2, AudioOutputs.TYPE_BUILTIN_SPEAKER, "Built-in speaker"),
                RawOutput(3, AudioOutputs.TYPE_TELEPHONY, "Call"),
                RawOutput(4, 999, "Mystery"),
            ),
            phone,
        )
        assertEquals(listOf(AudioOutput(2, OutputKind.PHONE_SPEAKER, phone)), list)
    }

    @Test
    fun buildList_renamesSpeakerToPhoneLabel() {
        val list = AudioOutputs.buildList(
            listOf(RawOutput(5, AudioOutputs.TYPE_BUILTIN_SPEAKER, "Built-in speaker")),
            phone,
        )
        assertEquals(phone, list.single().name)
    }

    @Test
    fun buildList_removesDuplicateKindAndNameKeepingFirstId() {
        val list = AudioOutputs.buildList(
            listOf(
                RawOutput(10, AudioOutputs.TYPE_WIRED_HEADPHONES, "Headphones"),
                RawOutput(11, AudioOutputs.TYPE_WIRED_HEADSET, "Headphones"),
            ),
            phone,
        )
        assertEquals(listOf(AudioOutput(10, OutputKind.WIRED, "Headphones")), list)
    }

    @Test
    fun buildList_keepsSameKindWithDifferentNames() {
        val list = AudioOutputs.buildList(
            listOf(
                RawOutput(10, AudioOutputs.TYPE_BLUETOOTH_A2DP, "Buds"),
                RawOutput(11, AudioOutputs.TYPE_BLUETOOTH_A2DP, "Car"),
            ),
            phone,
        )
        assertEquals(listOf(10, 11), list.map { it.id })
    }

    @Test
    fun buildList_ordersSpeakerWiredUsbThenBluetooth() {
        val list = AudioOutputs.buildList(
            listOf(
                RawOutput(1, AudioOutputs.TYPE_BLUETOOTH_A2DP, "Buds"),
                RawOutput(2, AudioOutputs.TYPE_USB_HEADSET, "Dac"),
                RawOutput(3, AudioOutputs.TYPE_BUILTIN_SPEAKER, "Built-in speaker"),
                RawOutput(4, AudioOutputs.TYPE_WIRED_HEADPHONES, "Headphones"),
            ),
            phone,
        )
        assertEquals(listOf(3, 4, 2, 1), list.map { it.id })
    }

    @Test
    fun selectedId_returnsPreferredWhenStillPresent() {
        val list = listOf(AudioOutput(7, OutputKind.BLUETOOTH, "Buds"))
        assertEquals(7, AudioOutputs.selectedId(list, 7))
    }

    @Test
    fun selectedId_fallsBackToNullWhenDeviceDisappears() {
        val list = listOf(AudioOutput(7, OutputKind.BLUETOOTH, "Buds"))
        assertNull(AudioOutputs.selectedId(list, 8))
    }

    @Test
    fun selectedId_nullPreferenceIsNull() {
        val list = listOf(AudioOutput(7, OutputKind.BLUETOOTH, "Buds"))
        assertNull(AudioOutputs.selectedId(list, null))
    }
}
