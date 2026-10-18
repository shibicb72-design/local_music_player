package com.shibi.musicplayer

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.diagnostics.ErrorLog
import com.shibi.musicplayer.domain.AppFacts
import com.shibi.musicplayer.domain.DeviceFacts
import com.shibi.musicplayer.domain.ErrorLogEntries
import com.shibi.musicplayer.domain.ErrorLogEntry
import com.shibi.musicplayer.domain.MediaFacts
import com.shibi.musicplayer.ui.CopyAllTag
import com.shibi.musicplayer.ui.ErrorLogContent
import com.shibi.musicplayer.ui.ErrorLogScreen
import com.shibi.musicplayer.ui.theme.MusicPlayerTheme
import java.io.File
import java.io.FileNotFoundException
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ErrorLogTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val device = DeviceFacts("samsung", "SM-F966B", "16", 36, listOf("arm64-v8a"), 4096L, "folded")
    private val facts = { device to AppFacts("Theatrum", "1.0", 1L, "3.7.7") }

    private fun log(file: File, clock: () -> Long = { 1_000L }) = ErrorLog(
        file = file,
        facts = facts,
        clock = clock,
        zone = { ZoneId.of("UTC") },
        io = Dispatchers.Unconfined,
    )

    @Test
    fun entriesSurviveARestartNewestFirst() {
        val file = File(app.filesDir, "test_error_log.txt").apply { delete() }
        var now = 1_000L
        val first = log(file) { now++ }
        first.log("Open video (inline box)", FileNotFoundException("ENOENT"), "Couldn't open", MediaFacts(fileName = "a.mkv"))
        first.log("Scan videos", SecurityException("revoked"), null, null)
        assertEquals(listOf("Scan videos", "Open video (inline box)"), first.entries.value.map { it.step })

        // A new process reads the same file.
        val restarted = log(file)
        assertEquals(listOf("Scan videos", "Open video (inline box)"), restarted.entries.value.map { it.step })
        assertEquals("a.mkv", restarted.entries.value.last().fileName)
        assertTrue(restarted.entries.value.last().text.contains("Device: samsung SM-F966B"))
        file.delete()
    }

    @Test
    fun theStoredLogIsCapped() {
        val file = File(app.filesDir, "test_error_log_cap.txt").apply { delete() }
        val log = log(file)
        repeat(ErrorLogEntries.Cap + 10) { index -> log.log("Step $index", null, null, null) }
        assertEquals(ErrorLogEntries.Cap, log.entries.value.size)
        assertEquals(ErrorLogEntries.Cap, log(file).entries.value.size)
        assertEquals("Step ${ErrorLogEntries.Cap + 9}", log(file).entries.value.first().step)
        file.delete()
    }

    @Test
    fun aBrokenFileStartsAnEmptyLogInsteadOfCrashing() {
        val file = File(app.filesDir, "test_error_log_broken.txt").apply { writeText("not a log") }
        assertTrue(log(file).entries.value.isEmpty())
        file.delete()
    }

    /** The same failure twice in one millisecond gives two equal entries; the list must still draw. */
    @Test
    fun twoIdenticalEntriesDoNotCrashTheList() {
        val entry = ErrorLogEntry(1_000L, "Scan videos", "SecurityException: revoked", null, "text")
        compose.setContent {
            MusicPlayerTheme {
                ErrorLogContent(
                    entries = listOf(entry, entry),
                    copied = false,
                    onCopyAll = {},
                    onBack = {},
                    contentBottomPadding = 0.dp,
                )
            }
        }
        compose.onAllNodesWithText("Scan videos").assertCountEquals(2)
    }

    @Test
    fun anEmptyLogHasNoCopyButton() {
        compose.setContent {
            MusicPlayerTheme {
                ErrorLogContent(
                    entries = emptyList(),
                    copied = false,
                    onCopyAll = {},
                    onBack = {},
                    contentBottomPadding = 0.dp,
                )
            }
        }
        compose.onNodeWithText("No errors").assertExists()
        compose.onNodeWithTag(CopyAllTag).assertDoesNotExist()
    }

    @Test
    fun copyAllPutsTheWholeLogOnTheClipboardAndSaysCopied() {
        val log = ErrorLog.get(app)
        log.log("Open video (fullscreen)", FileNotFoundException("open failed"), "Couldn't open this video.", null)
        log.log("Load subtitle file for clip.mkv (fullscreen)", null, "Couldn't load that subtitle file.", null)
        compose.setContent {
            MusicPlayerTheme {
                ErrorLogScreen(onBack = {})
            }
        }
        compose.waitUntil { log.entries.value.size >= 2 }
        compose.onNodeWithText("Load subtitle file for clip.mkv (fullscreen)").assertExists()
        // One button on the whole screen: no copy button per entry.
        compose.onAllNodesWithText("Copy all").assertCountEquals(1)
        compose.onNodeWithTag(CopyAllTag).performClick()
        compose.waitUntil { compose.onAllNodesWithText("Copied").fetchSemanticsNodes().isNotEmpty() }

        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = clipboard.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(copied.startsWith("==="))
        assertTrue(copied.indexOf("Load subtitle file") < copied.indexOf("Open video"))
        assertTrue(copied.contains("Error: java.io.FileNotFoundException: open failed"))
        assertFalse(copied.contains("/storage"))
        assertEquals("Theatrum error log", clipboard.primaryClip!!.description.label.toString())
    }
}
