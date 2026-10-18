package com.shibi.musicplayer.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.shibi.musicplayer.domain.ErrorRedaction
import com.shibi.musicplayer.domain.MediaFacts
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

/** A loaded file, ready for [VideoEngine.addExternalTrack]. */
class PreparedTrack(
    /** `file://` or `fd://N`: a location libVLC opens itself. */
    val location: String,
    val name: String,
    /** Kept open while the video plays (the descriptor behind an `fd://`). */
    val resource: Closeable?,
)

/** A subtitle or audio file the user picked with the system file picker. */
interface ExternalTrackSource {
    /** File name, type and size, for the extension check and the error log. Never throws. */
    fun describe(uri: String): MediaFacts

    /** Makes [uri] openable by libVLC; throws if it cannot. Runs off the main thread. */
    fun prepare(kind: ExternalTrackKind, uri: String, facts: MediaFacts): PreparedTrack

    /** Drops the copies made for the previous video. */
    fun clear()
}

/** Thrown for a picked file that is not one of [SubtitleFiles.Extensions]. */
class UnsupportedTrackFileException(message: String) : Exception(message)

/** Thrown when libVLC refused a file or no track appeared for it. */
class TrackLoadException(message: String) : Exception(message)

/**
 * The picker's `content://` URIs, which libVLC cannot open. Subtitles are small and libVLC
 * recognises their format by the extension, so they are copied into the cache under their
 * own name (`file://`). Audio files can be large, so libVLC reads them through a descriptor
 * (`fd://N`), the way the video itself is opened.
 */
internal class ContentExternalTrackSource(context: Context) : ExternalTrackSource {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val cacheDir = File(appContext.cacheDir, CacheFolder)
    private val copies = AtomicInteger()

    override fun describe(uri: String): MediaFacts {
        val parsed = Uri.parse(uri)
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
        }
        return MediaFacts(
            fileName = name ?: parsed.lastPathSegment?.let(ErrorRedaction::fileName),
            mimeType = runCatching { resolver.getType(parsed) }.getOrNull(),
            sizeBytes = size,
        )
    }

    override fun prepare(kind: ExternalTrackKind, uri: String, facts: MediaFacts): PreparedTrack {
        val parsed = Uri.parse(uri)
        // Keeps the grant across restarts; a provider that offers no persistable grant still reads now.
        runCatching { resolver.takePersistableUriPermission(parsed, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = facts.fileName?.takeIf { it.isNotBlank() } ?: when (kind) {
            ExternalTrackKind.Subtitle -> DefaultName
            ExternalTrackKind.Audio -> DefaultAudioName
        }
        return when (kind) {
            ExternalTrackKind.Subtitle -> {
                val size = facts.sizeBytes
                if (size != null && size > MaxSubtitleBytes) {
                    throw IOException("Subtitle file is $size bytes; the limit is $MaxSubtitleBytes")
                }
                cacheDir.mkdirs()
                // Numbered, so two picks with the same name never overwrite each other; the
                // extension stays last for libVLC's format detection.
                val copy = File(cacheDir, "${copies.incrementAndGet()}-${safeName(name)}")
                val input = resolver.openInputStream(parsed)
                    ?: throw FileNotFoundException("The content resolver returned no stream")
                try {
                    input.use { source ->
                        copy.outputStream().use { target -> copyCapped(source, target) }
                    }
                } catch (error: Exception) {
                    // A refused or broken copy never stays in the cache.
                    copy.delete()
                    throw error
                }
                PreparedTrack(location = Uri.fromFile(copy).toString(), name = name, resource = null)
            }
            ExternalTrackKind.Audio -> {
                val descriptor = resolver.openFileDescriptor(parsed, "r")
                    ?: throw FileNotFoundException("The content resolver returned no file descriptor")
                PreparedTrack(location = "fd://${descriptor.fd}", name = name, resource = descriptor)
            }
        }
    }

    override fun clear() {
        runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
    }

    private companion object {
        const val CacheFolder = "external_tracks"
        const val DefaultName = "subtitle.srt"
        const val DefaultAudioName = "audio"

        /** Subtitle files are text; anything this large is not one. */
        const val MaxSubtitleBytes = 20L * 1024L * 1024L

        /** Stops as soon as the limit is passed: providers often report no size, or a wrong one. */
        fun copyCapped(source: InputStream, target: OutputStream) {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = source.read(buffer)
                if (read < 0) return
                total += read
                if (total > MaxSubtitleBytes) throw IOException("Subtitle file is over $MaxSubtitleBytes bytes")
                target.write(buffer, 0, read)
            }
        }

        /** The name as picked, minus anything that could leave the cache folder. */
        fun safeName(name: String): String =
            name.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").trim('.', ' ').ifEmpty { DefaultName }
    }
}
