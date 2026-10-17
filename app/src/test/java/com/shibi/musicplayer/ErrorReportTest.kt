package com.shibi.musicplayer

import com.shibi.musicplayer.domain.AppFacts
import com.shibi.musicplayer.domain.DeviceFacts
import com.shibi.musicplayer.domain.ErrorLogEntries
import com.shibi.musicplayer.domain.ErrorLogEntry
import com.shibi.musicplayer.domain.ErrorRedaction
import com.shibi.musicplayer.domain.ErrorReport
import com.shibi.musicplayer.domain.ErrorReportFormat
import com.shibi.musicplayer.domain.MediaFacts
import java.io.FileNotFoundException
import java.io.IOException
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorReportTest {

    private val zone = ZoneId.of("America/New_York")

    /** 2026-10-17 14:32:05 EDT. */
    private val time = 1_792_261_925_000L

    private val device = DeviceFacts(
        manufacturer = "samsung",
        model = "SM-F966B",
        androidRelease = "16",
        sdkInt = 36,
        abis = listOf("arm64-v8a"),
        pageSizeBytes = 4096L,
        foldState = "unfolded",
    )
    private val app = AppFacts("Theatrum", "1.0", 1L, "3.7.7")

    private fun report(
        error: Throwable? = null,
        step: String = "Open video (fullscreen)",
        shown: String? = "Couldn't open this video.",
        media: MediaFacts? = null,
    ) = ErrorReport(
        timeMillis = time,
        step = step,
        shown = shown,
        causes = ErrorReport.causesOf(error),
        media = media,
        device = device,
        app = app,
    )

    @Test
    fun theCauseChainIsOutermostFirstWithTrimmedStacks() {
        val root = IllegalStateException("ENOENT")
        val outer = IOException("open failed", root)
        val causes = ErrorReport.causesOf(outer, maxFrames = 3)
        assertEquals(listOf("java.io.IOException", "java.lang.IllegalStateException"), causes.map { it.className })
        assertEquals(listOf("open failed", "ENOENT"), causes.map { it.message })
        assertTrue(causes.all { it.frames.size <= 3 && it.frames.isNotEmpty() })
    }

    @Test
    fun aCauseLoopOrADeepChainStops() {
        val a = Exception("a")
        val b = Exception("b", a)
        a.initCause(b)
        assertEquals(2, ErrorReport.causesOf(b).size)

        var deep: Throwable = Exception("0")
        repeat(20) { deep = Exception("${it + 1}", deep) }
        assertEquals(ErrorReport.MaxCauses, ErrorReport.causesOf(deep).size)
        assertTrue(ErrorReport.causesOf(null).isEmpty())
    }

    @Test
    fun theTextHasEveryFactAReportNeeds() {
        val text = ErrorReportFormat.format(
            report(
                error = FileNotFoundException("open failed: ENOENT (No such file or directory)"),
                media = MediaFacts(
                    fileName = "Concert encore.mkv",
                    mimeType = "video/x-matroska",
                    codecs = listOf("video h264 1920x1080", "audio a52 6ch 48000 Hz eng"),
                    width = 1920,
                    height = 1080,
                    durationMs = 3_723_000L,
                    sizeBytes = 1_412_000_000L,
                ),
            ),
            zone,
        )
        val lines = text.lines()
        assertEquals("=== 2026-10-17 14:32:05 -04:00 ===", lines[0])
        assertEquals("Step: Open video (fullscreen)", lines[1])
        assertEquals("Shown: Couldn't open this video.", lines[2])
        assertEquals(
            "Error: java.io.FileNotFoundException: open failed: ENOENT (No such file or directory)",
            lines[3],
        )
        assertTrue(lines[4].startsWith("    at "))
        assertTrue(
            text,
            text.contains("File: Concert encore.mkv · video/x-matroska · MKV · 1920×1080 · 1:02:03 · 1.4 GB"),
        )
        assertTrue(text.contains("Codecs: video h264 1920x1080; audio a52 6ch 48000 Hz eng"))
        assertTrue(text.contains("Device: samsung SM-F966B · Android 16 (API 36) · arm64-v8a · page 4 KB · unfolded"))
        assertTrue(text.endsWith("App: Theatrum 1.0 (1) · libVLC 3.7.7"))
    }

    @Test
    fun anErrorWithNoExceptionStillSaysSo() {
        val text = ErrorReportFormat.format(report(error = null), zone)
        assertTrue(text.contains("Error: (no exception)"))
        assertEquals("Couldn't open this video.", ErrorReportFormat.summary(report(error = null)))
    }

    @Test
    fun redactionKeepsOnlyFileNamesAndDropsQueriesAndTokens() {
        val secret = "open failed: /storage/emulated/0/Movies/Holiday/beach.mkv " +
            "from content://com.android.externalstorage.documents/document/primary%3AMovies%2Fbeach.mkv?token=abc123&x=1 " +
            "and file:///data/user/0/com.shibi.musicplayer/cache/external_tracks/Movie.en.srt " +
            "with https://example.com/sub?api_key=SECRETKEY&sig=deadbeef Authorization: Bearer eyJhbGciOi.payload.sig"
        val clean = ErrorRedaction.scrub(secret)
        assertEquals(
            "open failed: …/beach.mkv from content://com.android.externalstorage.documents/… " +
                "and …/Movie.en.srt with https://example.com/sub?api_key=<redacted>&sig=<redacted> " +
                "Authorization: Bearer <redacted>",
            clean,
        )
        listOf("storage/emulated", "Holiday", "primary%3A", "abc123", "SECRETKEY", "deadbeef", "eyJhbGciOi", "data/user")
            .forEach { leaked -> assertFalse("leaked $leaked in $clean", clean.contains(leaked)) }
    }

    @Test
    fun foldersWithSpacesAndDocumentIdsLeaveNoFolderNames() {
        val clean = ErrorRedaction.scrub(
            "/storage/emulated/0/Download/Shibi CB/Private Stuff/x.srt: open failed: EACCES (Permission denied)",
        )
        assertEquals("…/x.srt: open failed: EACCES (Permission denied)", clean)

        val saf = ErrorRedaction.scrub(
            "Missing file for primary:Movies/Private Stuff/clip.srt at /storage/emulated/0/Movies/Private Stuff/clip.srt",
        )
        // Folders may hold spaces, so the id and the path run together; dropping too much is the safe side.
        assertEquals("Missing file for …/clip.srt", saf)
        assertEquals("…/a.srt", ErrorRedaction.scrub("1A2B-3C4D:Family/Kids/a.srt"))
        listOf(clean, saf).forEach { text ->
            listOf("Shibi", "Private", "Movies", "Download", "primary").forEach { leaked ->
                assertFalse("leaked $leaked in $text", text.contains(leaked))
            }
        }
    }

    @Test
    fun moreSecretShapesAreRedacted() {
        assertEquals(
            "…/x.srt",
            ErrorRedaction.scrub("file:/data/user/0/com.shibi.musicplayer/cache/x.srt"),
        )
        assertEquals(
            "?refresh_token=<redacted>&id_token=<redacted>&client_secret=<redacted>&page=2",
            ErrorRedaction.scrub("?refresh_token=a&id_token=b&client_secret=c&page=2"),
        )
        assertEquals(
            "{\"token\":\"<redacted>\", \"name\":\"clip\"}",
            ErrorRedaction.scrub("{\"token\":\"abc\", \"name\":\"clip\"}"),
        )
        assertEquals("Authorization: Basic <redacted>", ErrorRedaction.scrub("Authorization: Basic dXNlcjpwYXNz"))
    }

    @Test
    fun redactionLeavesOrdinaryTextAlone() {
        val text = "video/x-matroska 1920x1080 16/9 at com.shibi.Foo.bar(Foo.kt:12) / done"
        assertEquals(text, ErrorRedaction.scrub(text))
    }

    @Test
    fun everyStoredFieldIsRedactedAndTheFileIsANameOnly() {
        val entry = ErrorLogEntry.of(
            report(
                error = IOException("cannot read /sdcard/Download/private/clip.mp4?x=1"),
                step = "Load subtitle file for /sdcard/Movies/a.mkv",
                media = MediaFacts(fileName = "/sdcard/Download/private/clip.srt"),
            ),
            zone,
        )
        assertEquals("Load subtitle file for …/a.mkv", entry.step)
        assertEquals("IOException: cannot read …/clip.mp4", entry.summary)
        assertEquals("clip.srt", entry.fileName)
        assertFalse(entry.text, entry.text.contains("/sdcard"))
        assertFalse(entry.text, entry.text.contains("private"))
    }

    @Test
    fun theFileNameDropsDirectoriesQueriesAndFragments() {
        assertEquals("clip.mkv", ErrorRedaction.fileName("/a/b/clip.mkv"))
        assertEquals("clip.mkv", ErrorRedaction.fileName("content://x/y/clip.mkv?token=1#t"))
        assertEquals("clip.mkv", ErrorRedaction.fileName("C:\\videos\\clip.mkv"))
        assertEquals("MKV", MediaFacts(fileName = "clip.mkv").container)
        assertEquals(null, MediaFacts(fileName = "README").container)
    }

    @Test
    fun theLogIsNewestFirstAndCapped() {
        var entries = emptyList<ErrorLogEntry>()
        repeat(ErrorLogEntries.Cap + 5) { index ->
            entries = ErrorLogEntries.prepend(entries, entry(index.toLong()))
        }
        assertEquals(ErrorLogEntries.Cap, entries.size)
        assertEquals(ErrorLogEntries.Cap + 4L, entries.first().timeMillis)
        assertEquals(5L, entries.last().timeMillis)

        val merged = ErrorLogEntries.merge(listOf(entry(9L)), listOf(entry(2L), entry(1L)), cap = 2)
        assertEquals(listOf(9L, 2L), merged.map { it.timeMillis })
    }

    @Test
    fun theStoredFormatRoundTripsAndSkipsWhatItCannotRead() {
        val entries = listOf(
            entry(2L).copy(fileName = null, text = "line one\nline two"),
            entry(1L).copy(step = "a\u001Fb\u001Ec"),
        )
        val decoded = ErrorLogEntries.decode(ErrorLogEntries.encode(entries))
        assertEquals(entries[0], decoded[0])
        assertEquals("a b c", decoded[1].step)
        assertTrue(ErrorLogEntries.decode("").isEmpty())
        assertTrue(ErrorLogEntries.decode("v9\u001E1\u001Fx").isEmpty())
        assertEquals(1, ErrorLogEntries.decode("v1\u001Ebroken\u001E3\u001Fs\u001Fm\u001F\u001Ft").size)
    }

    @Test
    fun copyAllJoinsEntriesWithABlankLine() {
        assertEquals("a\n\nb", ErrorReportFormat.formatAll(listOf("a", "b")))
    }

    private fun entry(time: Long) = ErrorLogEntry(
        timeMillis = time,
        step = "Step $time",
        summary = "Summary",
        fileName = "clip.mkv",
        text = "Text $time",
    )
}
