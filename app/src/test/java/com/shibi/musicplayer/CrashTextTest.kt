package com.shibi.musicplayer

import com.shibi.musicplayer.domain.CrashText
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashTextTest {

    @Test
    fun printableRunsKeepsTextRunsAndDropsBinaryNoise() {
        val bytes = byteArrayOf(0, 1, 2, 3) +
            "abort message".toByteArray(StandardCharsets.US_ASCII) +
            byteArrayOf(0, 0x7F, 0x1B) +
            "libhwui.so".toByteArray(StandardCharsets.US_ASCII) +
            byteArrayOf(0)
        assertEquals("abort message\nlibhwui.so", CrashText.printableRuns(bytes))
    }

    @Test
    fun printableRunsDropsRunsShorterThanMinRun() {
        val bytes = "abc".toByteArray(StandardCharsets.US_ASCII) +
            byteArrayOf(0) +
            "abcdef".toByteArray(StandardCharsets.US_ASCII)
        assertEquals("abcdef", CrashText.printableRuns(bytes))
        assertEquals("abort", CrashText.printableRuns("abort".toByteArray(StandardCharsets.US_ASCII), minRun = 4))
    }

    @Test
    fun printableRunsRespectsMaxChars() {
        val bytes = "x".repeat(100).toByteArray(StandardCharsets.US_ASCII)
        assertEquals(20, CrashText.printableRuns(bytes, maxChars = 20).length)
    }

    @Test
    fun newSinceOnAFirstRunOnlyKeepsTheWindow() {
        val now = 1_000_000_000L
        val tooOld = now - 2 * 86_400_000L
        val recent = now - 3_600_000L
        assertEquals(listOf(recent), CrashText.newSince(listOf(tooOld, recent), seenMs = null, nowMs = now))
    }

    @Test
    fun newSinceFiltersWhatWasSeenAndOrdersOldestFirst() {
        assertEquals(
            listOf(100L, 200L, 300L),
            CrashText.newSince(listOf(300L, 100L, 200L, 50L), seenMs = 50L, nowMs = 1_000L),
        )
        assertEquals(emptyList<Long>(), CrashText.newSince(listOf(50L), seenMs = 50L, nowMs = 1_000L))
    }
}
