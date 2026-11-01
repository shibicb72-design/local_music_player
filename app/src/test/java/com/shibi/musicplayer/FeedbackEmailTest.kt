package com.shibi.musicplayer

import com.shibi.musicplayer.domain.FeedbackEmail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackEmailTest {

    private fun body(manufacturer: String = "samsung", model: String = "SM-F966B") = FeedbackEmail.body(
        prompt = "Please describe the issue below and attach a screenshot.",
        versionName = "1.0.0",
        versionCode = 2L,
        manufacturer = manufacturer,
        model = model,
        androidRelease = "16",
        sdkInt = 36,
    )

    @Test
    fun everyEmailGoesToOneAddressWithOneSubject() {
        assertEquals("shibicb.71@gmail.com", FeedbackEmail.Address)
        assertEquals("Theatrum feedback", FeedbackEmail.Subject)
    }

    @Test
    fun theBodyAsksFirstThenListsTheBuildAndTheDevice() {
        assertEquals(
            """
            Please describe the issue below and attach a screenshot.


            ---
            App version: 1.0.0 (2)
            Device: samsung SM-F966B
            Android: 16 (API 36)
            """.trimIndent(),
            body(),
        )
    }

    @Test
    fun aBlankManufacturerOrModelLeavesNoStraySpace() {
        assertTrue(body(manufacturer = "").contains("\nDevice: SM-F966B\n"))
        assertTrue(body(model = "").contains("\nDevice: samsung\n"))
    }
}
