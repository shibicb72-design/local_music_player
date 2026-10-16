package com.shibi.musicplayer

import com.shibi.musicplayer.domain.AudioOutput
import com.shibi.musicplayer.domain.OutputKind
import com.shibi.musicplayer.ui.effectiveOutput
import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveOutputTest {
    private val phone = AudioOutput(1, OutputKind.PHONE_SPEAKER, "This phone")
    private val usb = AudioOutput(3, OutputKind.USB, "USB-C headphones")
    private val bluetooth = AudioOutput(2, OutputKind.BLUETOOTH, "MAJOR V")
    private val outputs = listOf(phone, usb, bluetooth)

    @Test
    fun prefersThePreferredIdWhenPresent() {
        assertEquals(bluetooth, effectiveOutput(outputs, 2))
        assertEquals(usb, effectiveOutput(outputs, 3))
    }

    @Test
    fun fallsBackToThePhoneWhenNothingIsPreferred() {
        assertEquals(phone, effectiveOutput(outputs, null))
        assertEquals(phone, effectiveOutput(outputs, 99))
    }
}
