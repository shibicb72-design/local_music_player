package com.shibi.musicplayer.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.system.Os
import android.system.OsConstants
import androidx.core.content.pm.PackageInfoCompat
import com.shibi.musicplayer.R
import com.shibi.musicplayer.domain.AppFacts
import com.shibi.musicplayer.domain.DeviceFacts
import com.shibi.musicplayer.domain.ErrorLogEntries
import com.shibi.musicplayer.domain.ErrorLogEntry
import com.shibi.musicplayer.domain.ErrorReport
import com.shibi.musicplayer.domain.MediaFacts
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Where every error goes. Screens show a short message only; the details are written here
 * and read in Settings > Debugging / Error log.
 */
fun interface ErrorLogger {
    /** [step] says what failed (`Open video (fullscreen)`); [shown] is the message the user saw. */
    fun log(step: String, error: Throwable?, shown: String?, media: MediaFacts?)

    companion object {
        /** Tests and previews that do not care about the log. */
        val None = ErrorLogger { _, _, _, _ -> }
    }
}

/**
 * The persistent error log: a small file in the app's private storage, newest first and
 * capped at [ErrorLogEntries.Cap] entries, so it survives restarts and stays small. Logging
 * never throws: a log that cannot be written must not take the app down with it.
 */
class ErrorLog internal constructor(
    private val file: File,
    private val facts: () -> Pair<DeviceFacts, AppFacts>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    io: CoroutineDispatcher = Dispatchers.IO,
) : ErrorLogger {

    private val scope = CoroutineScope(SupervisorJob() + io)
    private val writeLock = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val _entries = MutableStateFlow<List<ErrorLogEntry>>(emptyList())

    /** Newest first. */
    val entries: StateFlow<List<ErrorLogEntry>> = _entries.asStateFlow()

    init {
        scope.launch {
            val stored = runCatching { read() }.getOrDefault(emptyList())
            // Anything logged while the file was being read is newer than what it held.
            _entries.update { newer -> ErrorLogEntries.merge(newer, stored) }
            loaded.complete(Unit)
        }
    }

    override fun log(step: String, error: Throwable?, shown: String?, media: MediaFacts?) {
        val entry = runCatching {
            val (device, app) = facts()
            val report = ErrorReport(
                timeMillis = clock(),
                step = step,
                shown = shown,
                causes = ErrorReport.causesOf(error),
                media = media,
                device = device,
                app = app,
            )
            ErrorLogEntry.of(report, zone())
        }.getOrNull() ?: return
        _entries.update { current -> ErrorLogEntries.prepend(current, entry) }
        scope.launch {
            loaded.await()
            writeLock.withLock { runCatching { write(_entries.value) } }
        }
    }

    private fun read(): List<ErrorLogEntry> {
        if (!file.exists()) return emptyList()
        return ErrorLogEntries.decode(file.readText(Charsets.UTF_8))
    }

    /** A whole new file, then one atomic replace: a crash mid-write never leaves half a log. */
    private fun write(entries: List<ErrorLogEntry>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".new")
        temporary.writeText(ErrorLogEntries.encode(entries), Charsets.UTF_8)
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    companion object {
        private const val FileName = "error_log.txt"
        private val instances = mutableMapOf<String, ErrorLog>()

        /** One log per app (per storage directory, so each Robolectric app gets its own). */
        fun get(context: Context): ErrorLog {
            val app = context.applicationContext
            val file = File(app.filesDir, FileName)
            return synchronized(instances) {
                instances.getOrPut(file.absolutePath) {
                    // The version never changes while the app runs; the fold state does.
                    val appFacts by lazy { ErrorFacts.app(app) }
                    ErrorLog(file = file, facts = { ErrorFacts.device(app) to appFacts })
                }
            }
        }
    }
}

/** The device and app lines of every entry; nothing that identifies the user. */
internal object ErrorFacts {

    /** The unfolded inner screen is at least 600dp both ways; phones and the cover screen are not. */
    private const val UnfoldedSmallestWidthDp = 600

    fun device(context: Context): DeviceFacts {
        val configuration: Configuration = context.resources.configuration
        return DeviceFacts(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            pageSizeBytes = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrNull()?.takeIf { it > 0L },
            foldState = if (configuration.smallestScreenWidthDp >= UnfoldedSmallestWidthDp) "unfolded" else "folded",
        )
    }

    fun app(context: Context): AppFacts {
        val info = runCatching {
            val manager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                manager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()
        return AppFacts(
            appName = context.getString(R.string.app_name),
            versionName = info?.versionName ?: "?",
            versionCode = info?.let(PackageInfoCompat::getLongVersionCode) ?: 0L,
            libVlcVersion = context.getString(R.string.libvlc_version),
        )
    }
}
