package com.shibi.musicplayer

import com.shibi.musicplayer.domain.AudioOutputs
import com.shibi.musicplayer.domain.BondedClass
import com.shibi.musicplayer.domain.ListedDevice
import com.shibi.musicplayer.domain.OutputRoute
import com.shibi.musicplayer.domain.RouteIcon
import com.shibi.musicplayer.domain.RouteInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputRouteTest {

    @Test
    fun classifiesCarAudioOnA2dpAsCar() {
        val icon = OutputRoute.classify(
            RouteInput(
                deviceType = AudioOutputs.TYPE_BLUETOOTH_A2DP,
                productName = "HandsFreeLink",
                bonded = listOf(carAudio("HandsFreeLink")),
            ),
        )
        assertEquals(RouteIcon.CAR, icon)
    }

    @Test
    fun classifiesHandsfreeScoAsCar() {
        val icon = OutputRoute.classify(
            RouteInput(
                deviceType = AudioOutputs.TYPE_BLUETOOTH_SCO,
                productName = "HandsFreeLink",
                bonded = listOf(
                    BondedClass(
                        name = "handsfreelink",
                        majorClass = OutputRoute.MAJOR_AUDIO_VIDEO,
                        deviceClass = OutputRoute.CLASS_HANDSFREE,
                    ),
                ),
            ),
        )
        assertEquals(RouteIcon.CAR, icon)
    }

    @Test
    fun classifiesTypeBusAsCar() {
        val icon = OutputRoute.classify(
            RouteInput(
                deviceType = OutputRoute.TYPE_BUS,
                productName = "Android Auto",
                bluetoothConnectGranted = false,
            ),
        )
        assertEquals(RouteIcon.CAR, icon)
    }

    @Test
    fun classifiesCarModeAsCar() {
        val icon = OutputRoute.classify(
            RouteInput(
                deviceType = AudioOutputs.TYPE_BUILTIN_SPEAKER,
                productName = "Phone",
                carMode = true,
            ),
        )
        assertEquals(RouteIcon.CAR, icon)
    }

    @Test
    fun classifiesAHeadset() {
        assertEquals(
            RouteIcon.HEADPHONES,
            OutputRoute.classify(RouteInput(AudioOutputs.TYPE_WIRED_HEADPHONES, "Headphones")),
        )
        assertEquals(
            RouteIcon.HEADPHONES,
            OutputRoute.classify(RouteInput(AudioOutputs.TYPE_WIRED_HEADSET, "Headset")),
        )
        assertEquals(
            RouteIcon.HEADPHONES,
            OutputRoute.classify(
                RouteInput(
                    deviceType = AudioOutputs.TYPE_BLUETOOTH_A2DP,
                    productName = "WH-1000XM5",
                    bonded = listOf(
                        BondedClass(
                            name = "WH-1000XM5",
                            majorClass = OutputRoute.MAJOR_AUDIO_VIDEO,
                            deviceClass = OutputRoute.CLASS_HEADPHONES,
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun classifiesEarbudsAsHeadset() {
        val icon = OutputRoute.classify(
            RouteInput(
                deviceType = AudioOutputs.TYPE_BLUETOOTH_A2DP,
                productName = "Buds",
                bonded = listOf(
                    BondedClass(
                        name = "Buds",
                        majorClass = OutputRoute.MAJOR_AUDIO_VIDEO,
                        deviceClass = OutputRoute.CLASS_WEARABLE_HEADSET,
                    ),
                ),
            ),
        )
        assertEquals(RouteIcon.HEADPHONES, icon)
    }

    @Test
    fun classifiesABluetoothSpeaker() {
        assertEquals(
            RouteIcon.SPEAKER,
            OutputRoute.classify(RouteInput(AudioOutputs.TYPE_BLE_SPEAKER, "Living room")),
        )
        assertEquals(
            RouteIcon.SPEAKER,
            OutputRoute.classify(
                RouteInput(
                    deviceType = AudioOutputs.TYPE_BLUETOOTH_A2DP,
                    productName = "SoundLink",
                    bonded = listOf(
                        BondedClass(
                            name = "SoundLink",
                            majorClass = OutputRoute.MAJOR_AUDIO_VIDEO,
                            deviceClass = OutputRoute.CLASS_LOUDSPEAKER,
                        ),
                    ),
                ),
            ),
        )
        assertEquals(
            RouteIcon.SPEAKER,
            OutputRoute.classify(
                RouteInput(
                    deviceType = AudioOutputs.TYPE_BLUETOOTH_A2DP,
                    productName = "HiFi",
                    bonded = listOf(
                        BondedClass(
                            name = "HiFi",
                            majorClass = OutputRoute.MAJOR_AUDIO_VIDEO,
                            deviceClass = OutputRoute.CLASS_HIFI_AUDIO,
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun classifiesThePhoneSpeaker() {
        assertEquals(
            RouteIcon.PHONE,
            OutputRoute.classify(RouteInput(AudioOutputs.TYPE_BUILTIN_SPEAKER, "Phone")),
        )
    }

    @Test
    fun classifiesUnknownWhenBluetoothConnectIsDenied() {
        val presentation = OutputRoute.present(
            RouteInput(
                deviceType = AudioOutputs.TYPE_BLUETOOTH_A2DP,
                productName = "HandsFreeLink",
                bonded = listOf(carAudio("HandsFreeLink")),
                bluetoothConnectGranted = false,
            ),
        )
        assertEquals(RouteIcon.UNKNOWN, presentation.icon)
        assertEquals("HandsFreeLink", presentation.deviceName)
        assertTrue(presentation.showDeviceName)
        assertTrue(presentation.showBluetoothGlyph)
    }

    @Test
    fun classifiesUnknownWhenTheBondedClassDoesNotMatch() {
        val icon = OutputRoute.classify(
            RouteInput(
                deviceType = AudioOutputs.TYPE_BLUETOOTH_A2DP,
                productName = "Mystery",
                bonded = listOf(carAudio("HandsFreeLink")),
            ),
        )
        assertEquals(RouteIcon.UNKNOWN, icon)
    }

    @Test
    fun rejectsACarDeviceClassWhoseMajorIsNotAudioVideo() {
        val icon = OutputRoute.classify(
            RouteInput(
                deviceType = AudioOutputs.TYPE_BLUETOOTH_A2DP,
                productName = "HandsFreeLink",
                bonded = listOf(
                    BondedClass(
                        name = "HandsFreeLink",
                        majorClass = 0x0200,
                        deviceClass = OutputRoute.CLASS_CAR_AUDIO,
                    ),
                ),
            ),
        )
        assertEquals(RouteIcon.UNKNOWN, icon)
    }

    @Test
    fun carNameShowsUnderTheArtistWithABluetoothGlyph() {
        val presentation = OutputRoute.present(
            RouteInput(
                deviceType = AudioOutputs.TYPE_BLUETOOTH_A2DP,
                productName = "HandsFreeLink",
                bonded = listOf(carAudio("HandsFreeLink")),
            ),
        )
        assertEquals(RouteIcon.CAR, presentation.icon)
        assertTrue(presentation.showDeviceName)
        assertTrue(presentation.showBluetoothGlyph)
        assertEquals("HandsFreeLink", presentation.deviceName)
    }

    @Test
    fun phoneShowsTheDeviceNameWithoutABluetoothGlyph() {
        val presentation = OutputRoute.present(
            RouteInput(AudioOutputs.TYPE_BUILTIN_SPEAKER, "This phone"),
        )
        assertEquals(RouteIcon.PHONE, presentation.icon)
        assertTrue(presentation.showDeviceName)
        assertFalse(presentation.showBluetoothGlyph)
        assertEquals("This phone", presentation.deviceName)
    }

    @Test
    fun wiredHeadsetShowsItsNameWithoutABluetoothGlyph() {
        val presentation = OutputRoute.present(
            RouteInput(AudioOutputs.TYPE_WIRED_HEADPHONES, "Headphones"),
        )
        assertEquals(RouteIcon.HEADPHONES, presentation.icon)
        assertTrue(presentation.showDeviceName)
        assertFalse(presentation.showBluetoothGlyph)
    }

    @Test
    fun bleHeadsetStaysHeadphonesWhenBluetoothConnectIsDenied() {
        assertEquals(
            RouteIcon.HEADPHONES,
            OutputRoute.classify(
                RouteInput(
                    deviceType = AudioOutputs.TYPE_BLE_HEADSET,
                    productName = "Buds",
                    bluetoothConnectGranted = false,
                ),
            ),
        )
    }

    @Test
    fun activeDevicePrefersTheChosenIdThenTheRoutedIdThenThePhone() {
        val phone = ListedDevice(1, AudioOutputs.TYPE_BUILTIN_SPEAKER, "Phone")
        val car = ListedDevice(2, AudioOutputs.TYPE_BLUETOOTH_A2DP, "HandsFreeLink")
        val buds = ListedDevice(3, AudioOutputs.TYPE_BLE_HEADSET, "Buds")
        val devices = listOf(phone, car, buds)

        assertEquals(car, OutputRoute.activeDevice(devices, preferredId = 2, routedIds = listOf(1)))
        assertEquals(buds, OutputRoute.activeDevice(devices, preferredId = 9, routedIds = listOf(3, 1)))
        assertEquals(phone, OutputRoute.activeDevice(devices, preferredId = null, routedIds = emptyList()))
        assertNull(OutputRoute.activeDevice(emptyList(), preferredId = 1, routedIds = listOf(1)))
    }

    private fun carAudio(name: String) = BondedClass(
        name = name,
        majorClass = OutputRoute.MAJOR_AUDIO_VIDEO,
        deviceClass = OutputRoute.CLASS_CAR_AUDIO,
    )
}
