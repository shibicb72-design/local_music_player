package com.shibi.musicplayer.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.CrashText
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Catches what would otherwise vanish: uncaught exceptions are written to the error log before
 * the process dies, and Android's memory of how the previous run ended is read at startup.
 */
object CrashSafetyNet {

    private const val PrefsName = "crash_safety_net"
    private const val KeyExitSeenMs = "exit_seen_ms"
    private const val ExitHistoryCount = 16
    private const val TraceLimitBytes = 256 * 1024
    private const val AnrTextBytes = 16 * 1024

    private val installed = AtomicBoolean(false)
    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /** Idempotent: only the first caller becomes the default uncaught-exception handler. */
    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                ErrorLog.get(app).logNow(
                    step = app.getString(R.string.crash_last_time_uncaught, thread.name),
                    error = throwable,
                    shown = null,
                )
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** Test hook: lets a test install again after restoring the real default handler. */
    internal fun resetForTest() {
        installed.set(false)
    }

    /** Android's memory of how previous runs ended. Call off the main thread; never throws. */
    fun recordExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val app = context.applicationContext
            val manager = app.getSystemService(ActivityManager::class.java) ?: return@runCatching
            val exits = manager.getHistoricalProcessExitReasons(app.packageName, 0, ExitHistoryCount)
            if (exits.isEmpty()) return@runCatching
            val prefs = app.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            val seenMs = prefs.getLong(KeyExitSeenMs, 0L).takeIf { it > 0L }
            val wanted = CrashText
                .newSince(exits.map { it.timestamp }, seenMs, System.currentTimeMillis())
                .toHashSet()
            exits.asSequence()
                .filter { it.timestamp in wanted }
                .sortedBy { it.timestamp }
                .forEach { record(app, it) }
            exits.maxOfOrNull { it.timestamp }?.let { newest ->
                prefs.edit().putLong(KeyExitSeenMs, newest).apply()
            }
        }
    }

    private fun record(context: Context, info: ApplicationExitInfo) {
        val label = reasonLabel(info.reason) ?: return
        runCatching {
            ErrorLog.get(context).logNow(
                step = context.getString(R.string.crash_last_time_exit, label),
                error = null,
                shown = details(info),
            )
        }
    }

    private fun reasonLabel(reason: Int): String? = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_ANR -> "not responding"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "start failed"
        else -> null
    }

    private fun details(info: ApplicationExitInfo): String = buildString {
        info.description?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
        appendLine("Importance: ${info.importance}")
        appendLine("Time: ${timeFormat.format(Instant.ofEpochMilli(info.timestamp).atZone(ZoneId.systemDefault()))}")
        trace(info)?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            append(it)
        }
    }

    /** Native tombstones are binary protobuf; ANR traces are text. Anything unreadable is left out. */
    private fun trace(info: ApplicationExitInfo): String? = runCatching {
        val stream = info.traceInputStream ?: return@runCatching null
        val bytes = stream.use { readAtMost(it, TraceLimitBytes) }
        when (info.reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE -> CrashText.printableRuns(bytes)
            ApplicationExitInfo.REASON_ANR ->
                bytes.copyOfRange(0, minOf(bytes.size, AnrTextBytes)).toString(Charsets.UTF_8)
            else -> null
        }
    }.getOrNull()

    private fun readAtMost(stream: InputStream, limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var total = 0
        while (total < limit) {
            val read = stream.read(buffer, total, limit - total)
            if (read < 0) break
            total += read
        }
        return buffer.copyOf(total)
    }
}
