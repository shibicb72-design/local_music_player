package com.shibi.musicplayer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.diagnostics.CrashSafetyNet
import com.shibi.musicplayer.diagnostics.ErrorLog
import com.shibi.musicplayer.domain.AppFacts
import com.shibi.musicplayer.domain.DeviceFacts
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CrashSafetyNetTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val device = DeviceFacts("samsung", "SM-F966B", "16", 36, listOf("arm64-v8a"), 4096L, "folded")
    private val facts = { device to AppFacts("Theatrum", "1.0", 1L, "3.7.7") }

    private var original: Thread.UncaughtExceptionHandler? = null

    @Before
    fun saveDefaultHandler() {
        original = Thread.getDefaultUncaughtExceptionHandler()
        CrashSafetyNet.resetForTest()
    }

    @After
    fun restoreDefaultHandler() {
        Thread.setDefaultUncaughtExceptionHandler(original)
        CrashSafetyNet.resetForTest()
    }

    @Test
    fun anUncaughtErrorReachesTheLogAndThePreviousHandlerStillRuns() {
        val handled = mutableListOf<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler(
            Thread.UncaughtExceptionHandler { _, error -> handled += error },
        )
        CrashSafetyNet.install(app)

        val boom = RuntimeException("boom")
        val thread = Thread("crash-thread")
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        requireNotNull(handler)
        handler.uncaughtException(thread, boom)

        assertEquals(listOf<Throwable>(boom), handled)

        // A new instance reading the same file sees what the dying process wrote.
        val restarted = ErrorLog(
            file = File(app.filesDir, "error_log.txt"),
            facts = facts,
            io = Dispatchers.Unconfined,
        )
        val entry = restarted.entries.value.firstOrNull()
        requireNotNull(entry)
        assertTrue(entry.step.startsWith("App crashed last time"))
        assertTrue(entry.step.contains("crash-thread"))
        assertTrue(entry.text.contains("boom"))
    }

    @Test
    fun installIsIdempotent() {
        Thread.setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler { _, _ -> })
        CrashSafetyNet.install(app)
        val first = Thread.getDefaultUncaughtExceptionHandler()
        CrashSafetyNet.install(app)
        assertSame(first, Thread.getDefaultUncaughtExceptionHandler())
    }
}
