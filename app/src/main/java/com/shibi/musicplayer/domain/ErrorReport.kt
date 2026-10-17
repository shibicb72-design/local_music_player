package com.shibi.musicplayer.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One throwable in a cause chain: class, message and the first frames of its stack. */
data class ErrorCause(
    val className: String,
    val message: String?,
    val frames: List<String>,
)

/** What is known about the file that failed; every value is optional. Never a path. */
data class MediaFacts(
    val fileName: String? = null,
    val mimeType: String? = null,
    val codecs: List<String> = emptyList(),
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val sizeBytes: Long? = null,
) {
    /** `MKV` from `clip.mkv`; null without an extension. */
    val container: String?
        get() = fileName?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotEmpty() && it.length <= 5 }
            ?.uppercase(Locale.ROOT)
}

data class DeviceFacts(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val abis: List<String>,
    val pageSizeBytes: Long?,
    /** `folded` (phone or cover screen) or `unfolded` (inner screen). */
    val foldState: String,
)

data class AppFacts(
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val libVlcVersion: String,
)

/**
 * One error for Settings > Debugging / Error log. The screen that failed shows only a
 * short message; everything a bug report needs is here, with no secrets: [ErrorRedaction]
 * runs over every free-text value, file locations are reduced to the file name, and
 * content-URI queries and token-like parameters never leave the device in a copy.
 */
data class ErrorReport(
    val timeMillis: Long,
    /** The step that failed, such as `Open video (fullscreen)`. */
    val step: String,
    /** The short message the user saw, if any. */
    val shown: String? = null,
    val causes: List<ErrorCause> = emptyList(),
    val media: MediaFacts? = null,
    val device: DeviceFacts,
    val app: AppFacts,
) {
    companion object {
        const val MaxCauses = 6
        const val MaxFrames = 8

        /** The cause chain, outermost first, cut at [maxCauses] and [maxFrames] frames each. */
        fun causesOf(
            error: Throwable?,
            maxCauses: Int = MaxCauses,
            maxFrames: Int = MaxFrames,
        ): List<ErrorCause> {
            val chain = mutableListOf<ErrorCause>()
            val seen = HashSet<Throwable>()
            var current = error
            while (current != null && chain.size < maxCauses && seen.add(current)) {
                chain += ErrorCause(
                    className = current.javaClass.name,
                    message = current.message,
                    frames = current.stackTrace.take(maxFrames).map { it.toString() },
                )
                current = current.cause
            }
            return chain
        }
    }
}

/** Strips anything private from text that is about to be stored or copied. */
object ErrorRedaction {

    private val ContentUri = Regex("""content://[^\s"'<>]+""")
    /** `file:///…` and Java's `file:/…` (`File.toURI()`). */
    private val FileUri = Regex("""file:/+[^\s"'<>]+""")

    /**
     * One folder name: spaces allowed (`Shibi CB`), but never a `://` or a `: ` (where a
     * message goes on), so a path ends before `: open failed` or the next URI.
     */
    private const val Folder = """(?:[^/\n"'<>:]|:(?!//|\s))+"""

    /** The file name after the last folder; any query or fragment on it is dropped. */
    private const val Name = """([^\s/"'<>?#:]*)(?:[?#][^\s"'<>]*)?"""

    /** At least one folder, so `/` alone or a `w/h` ratio is left alone. */
    private val AbsolutePath = Regex("""(?<![\w.:/])/(?:$Folder/)+$Name""")

    /** Storage Access Framework document ids: `primary:Movies/clip.srt`, `1A2B-3C4D:Folder/x.srt`. */
    private val DocumentId = Regex("""\b(?:primary|home|[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}):(?:$Folder/)+$Name""")

    /** `token=`, `refresh_token=`, `client_secret=`, `api_key=`, `sig=`… */
    private const val SecretName = """[\w-]*(?:token|auth|authorization|key|secret|password|passwd|sig|signature|session)"""
    private val SecretParam = Regex("""(?i)\b($SecretName)=[^&\s"'<>]+""")
    private val SecretJson = Regex("""(?i)"($SecretName)"\s*:\s*"[^"]*"""")
    private val Bearer = Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]+""")
    private val Basic = Regex("""(?i)\bbasic\s+[A-Za-z0-9+/=]{8,}""")

    /**
     * Content URIs keep only the authority (`content://media/…`); file URIs, absolute
     * paths (folders with spaces too) and document ids keep only the file name
     * (`…/clip.mkv`); token-like parameters
     * and bearer tokens become `<redacted>`.
     */
    fun scrub(text: String): String {
        var out = ContentUri.replace(text) { match -> contentUri(match.value) }
        out = FileUri.replace(out) { match -> "…/" + fileName(match.value) }
        out = AbsolutePath.replace(out) { match -> "…/" + match.groupValues[1] }
        out = DocumentId.replace(out) { match -> "…/" + match.groupValues[1] }
        out = SecretParam.replace(out) { match -> match.groupValues[1] + "=<redacted>" }
        out = SecretJson.replace(out) { match -> "\"" + match.groupValues[1] + "\":\"<redacted>\"" }
        out = Bearer.replace(out, "Bearer <redacted>")
        out = Basic.replace(out, "Basic <redacted>")
        return out
    }

    /** The last path segment of a path or URI, without query or fragment. */
    fun fileName(location: String): String {
        val withoutQuery = location.substringBefore('?').substringBefore('#')
        val trimmed = withoutQuery.trimEnd('/', '\\')
        return trimmed.substringAfterLast('/').substringAfterLast('\\')
    }

    private fun contentUri(uri: String): String {
        val authority = uri.removePrefix("content://").substringBefore('/').substringBefore('?')
        return "content://$authority/…"
    }
}

/** Plain-text rendering of an [ErrorReport], the way "Copy all" puts it on the clipboard. */
object ErrorReportFormat {

    private val Time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx", Locale.ROOT)

    fun time(timeMillis: Long, zone: ZoneId): String =
        Time.format(Instant.ofEpochMilli(timeMillis).atZone(zone))

    /** One line for the list: `FileNotFoundException: open failed`, already redacted. */
    fun summary(report: ErrorReport): String {
        val first = report.causes.firstOrNull() ?: return report.shown?.let(ErrorRedaction::scrub).orEmpty()
        val simple = first.className.substringAfterLast('.')
        val message = first.message?.let(ErrorRedaction::scrub)?.lineSequence()?.firstOrNull()?.trim()
        return if (message.isNullOrEmpty()) simple else "$simple: $message"
    }

    fun format(report: ErrorReport, zone: ZoneId): String = buildString {
        appendLine("=== ${time(report.timeMillis, zone)} ===")
        appendLine("Step: ${ErrorRedaction.scrub(report.step)}")
        report.shown?.let { appendLine("Shown: ${ErrorRedaction.scrub(it)}") }
        if (report.causes.isEmpty()) {
            appendLine("Error: (no exception)")
        }
        report.causes.forEachIndexed { index, cause ->
            val label = if (index == 0) "Error" else "Caused by"
            val message = cause.message?.let { ErrorRedaction.scrub(it).replace('\n', ' ') }
            appendLine(if (message.isNullOrEmpty()) "$label: ${cause.className}" else "$label: ${cause.className}: $message")
            cause.frames.forEach { frame -> appendLine("    at ${ErrorRedaction.scrub(frame)}") }
        }
        report.media?.let { media ->
            fileLine(media)?.let { appendLine("File: $it") }
            if (media.codecs.isNotEmpty()) {
                appendLine("Codecs: " + media.codecs.joinToString("; ") { ErrorRedaction.scrub(it) })
            }
        }
        appendLine("Device: ${deviceLine(report.device)}")
        append("App: ${appLine(report.app)}")
    }

    /** Every entry, newest first, separated by a blank line. */
    fun formatAll(texts: List<String>): String = texts.joinToString(separator = "\n\n")

    private fun fileLine(media: MediaFacts): String? = listOfNotNull(
        media.fileName?.let { ErrorRedaction.fileName(ErrorRedaction.scrub(it)) }?.takeIf { it.isNotEmpty() },
        media.mimeType?.takeIf { it.isNotBlank() },
        media.container,
        if ((media.width ?: 0) > 0 && (media.height ?: 0) > 0) "${media.width}×${media.height}" else null,
        media.durationMs?.takeIf { it > 0L }?.let(VideoFormat::duration),
        media.sizeBytes?.takeIf { it > 0L }?.let(VideoFormat::size),
    ).joinToString(" · ").takeIf { it.isNotEmpty() }

    private fun deviceLine(device: DeviceFacts): String = listOfNotNull(
        "${device.manufacturer} ${device.model}".trim(),
        "Android ${device.androidRelease} (API ${device.sdkInt})",
        device.abis.joinToString(",").takeIf { it.isNotEmpty() },
        device.pageSizeBytes?.let { "page ${it / 1024} KB" },
        device.foldState,
    ).joinToString(" · ")

    private fun appLine(app: AppFacts): String =
        "${app.appName} ${app.versionName} (${app.versionCode}) · libVLC ${app.libVlcVersion}"
}

/** One stored entry: what the list shows, plus the full text "Copy all" copies. */
data class ErrorLogEntry(
    val timeMillis: Long,
    val step: String,
    val summary: String,
    val fileName: String?,
    val text: String,
) {
    companion object {
        fun of(report: ErrorReport, zone: ZoneId): ErrorLogEntry = ErrorLogEntry(
            timeMillis = report.timeMillis,
            step = ErrorRedaction.scrub(report.step),
            summary = ErrorReportFormat.summary(report),
            fileName = report.media?.fileName
                ?.let { ErrorRedaction.fileName(ErrorRedaction.scrub(it)) }
                ?.takeIf { it.isNotEmpty() },
            text = ErrorReportFormat.format(report, zone),
        )
    }
}

/** The stored log: newest first, capped, in a plain-text format that needs no library. */
object ErrorLogEntries {
    const val Cap = 200

    fun prepend(entries: List<ErrorLogEntry>, entry: ErrorLogEntry, cap: Int = Cap): List<ErrorLogEntry> =
        (listOf(entry) + entries).take(cap)

    /** Older (or newly read) entries after the ones logged since; still newest first. */
    fun merge(newer: List<ErrorLogEntry>, older: List<ErrorLogEntry>, cap: Int = Cap): List<ErrorLogEntry> =
        (newer + older).take(cap)

    // Unit and record separators never appear in redacted text; they are stripped anyway.
    private const val Field = '\u001F'
    private const val Record = '\u001E'
    private const val Version = "v1"

    fun encode(entries: List<ErrorLogEntry>): String = buildString {
        append(Version)
        entries.forEach { entry ->
            append(Record)
            append(entry.timeMillis)
            append(Field).append(clean(entry.step))
            append(Field).append(clean(entry.summary))
            append(Field).append(clean(entry.fileName.orEmpty()))
            append(Field).append(clean(entry.text))
        }
    }

    /** Unknown versions and broken records are skipped, never thrown. */
    fun decode(stored: String): List<ErrorLogEntry> {
        val records = stored.split(Record)
        if (records.firstOrNull() != Version) return emptyList()
        return records.drop(1).mapNotNull { record ->
            val fields = record.split(Field)
            if (fields.size != 5) return@mapNotNull null
            val time = fields[0].toLongOrNull() ?: return@mapNotNull null
            ErrorLogEntry(
                timeMillis = time,
                step = fields[1],
                summary = fields[2],
                fileName = fields[3].ifEmpty { null },
                text = fields[4],
            )
        }
    }

    private fun clean(value: String): String = value.replace(Field, ' ').replace(Record, ' ')
}
