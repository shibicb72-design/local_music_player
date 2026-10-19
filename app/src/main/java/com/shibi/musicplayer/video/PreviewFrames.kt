package com.shibi.musicplayer.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import java.io.Closeable
import java.util.concurrent.Executors
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The seek preview's frame source. It has nothing to do with the playback engine: frames come
 * from their own [MediaMetadataRetriever] on one low-priority thread, so scrubbing never
 * touches libVLC and never competes with the main thread.
 */
interface FrameExtractor : Closeable {
    /** Blocking; only called on the preview thread. False if the file cannot give frames. */
    fun open(uri: String): Boolean

    /** Blocking; only called on the preview thread. Null when no frame could be made. */
    fun frameAt(positionMs: Long, maxWidthPx: Int): Bitmap?
}

/** [FrameExtractor] over [MediaMetadataRetriever]; formats it cannot read simply get no preview. */
class MmrFrameExtractor(private val context: Context) : FrameExtractor {

    private var retriever: MediaMetadataRetriever? = null
    private var displayWidth = 0
    private var displayHeight = 0

    override fun open(uri: String): Boolean {
        close()
        val created = MediaMetadataRetriever()
        return try {
            created.setDataSource(context, Uri.parse(uri))
            val width = created.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = created.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = created.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val turned = rotation == 90 || rotation == 270
            displayWidth = if (turned) height else width
            displayHeight = if (turned) width else height
            retriever = created
            true
        } catch (error: RuntimeException) {
            runCatching { created.release() }
            false
        }
    }

    override fun frameAt(positionMs: Long, maxWidthPx: Int): Bitmap? {
        val source = retriever ?: return null
        val timeUs = positionMs.coerceAtLeast(0L) * 1_000L
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && displayWidth > 0 && displayHeight > 0) {
                val scale = (maxWidthPx.toFloat() / displayWidth).coerceAtMost(1f)
                val width = (displayWidth * scale).toInt().coerceAtLeast(1)
                val height = (displayHeight * scale).toInt().coerceAtLeast(1)
                source.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, height)
            } else {
                val full = source.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (full != null && full.width > maxWidthPx) {
                    val height = (full.height * (maxWidthPx.toFloat() / full.width)).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(full, maxWidthPx, height, true)
                    if (scaled !== full) full.recycle()
                    scaled
                } else {
                    full
                }
            }
        } catch (error: RuntimeException) {
            null
        }
    }

    override fun close() {
        val old = retriever ?: return
        retriever = null
        runCatching { old.release() }
    }
}

/** One preview picture; [bucketMs] is the whole second it stands for. */
data class PreviewFrame(val bucketMs: Long, val bitmap: Bitmap)

/** The one thread all preview extraction runs on; created on first use. */
val PreviewDispatcher: CoroutineDispatcher by lazy {
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "theatrum-preview").apply {
            isDaemon = true
            priority = 3
        }
    }.asCoroutineDispatcher()
}

/**
 * Seek preview frames, cheap to ask for at drag rate. Nothing is created until the first
 * [request] (opening a video costs nothing). Requests are bucketed to whole seconds; a cached
 * second is published at once, a miss is extracted on [dispatcher] one at a time, and while one
 * extraction runs only the newest request is kept. [frame] holds the previous picture while the
 * next loads.
 */
class PreviewFrameLoader(
    private val scope: CoroutineScope,
    private val extractorFactory: () -> FrameExtractor,
    private val dispatcher: CoroutineDispatcher = PreviewDispatcher,
    private val maxWidthPx: Int = 320,
    private val cacheSize: Int = 24,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val onTiming: (Long) -> Unit = {},
) {

    private val _frame = MutableStateFlow<PreviewFrame?>(null)
    val frame: StateFlow<PreviewFrame?> = _frame.asStateFlow()

    /** The newest wanted bucket; the worker conflates to it while it is busy. */
    private val wanted = MutableStateFlow<Long?>(null)

    private val cache = object : LinkedHashMap<Long, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>?): Boolean =
            size > cacheSize
    }

    @Volatile
    private var uri: String? = null

    @Volatile
    private var generation = 0

    /** True from just before the factory runs until the close task has run. */
    @Volatile
    private var extractorExists = false

    private var worker: Job? = null

    // Touched only on the preview thread.
    private var extractor: FrameExtractor? = null
    private var extractorUri: String? = null
    private var failedGeneration = -1

    /** A new video: forgets everything, closes the old extractor, opens nothing. */
    fun setVideo(uri: String?) {
        reset(forceClose = false)
        this.uri = uri
    }

    /** Non-blocking; call at drag rate. */
    fun request(positionMs: Long) {
        if (uri == null) return
        val bucket = positionMs.coerceAtLeast(0L) / 1_000L * 1_000L
        val cached = synchronized(cache) { cache[bucket] }
        if (cached != null) {
            wanted.value = null
            _frame.value = PreviewFrame(bucket, cached)
            return
        }
        ensureWorker()
        wanted.value = bucket
    }

    fun close() {
        reset(forceClose = true)
        uri = null
        worker?.cancel()
        worker = null
    }

    /**
     * [forceClose] always posts the close task: on [close] an extraction may be between reading
     * its generation and flagging the extractor, and nothing would release what it opens.
     */
    private fun reset(forceClose: Boolean) {
        generation++
        wanted.value = null
        _frame.value = null
        synchronized(cache) { cache.clear() }
        if (forceClose || extractorExists) {
            // Posted straight to the dispatcher, so it still runs when the scope is gone.
            dispatcher.dispatch(EmptyCoroutineContext, Runnable { closeExtractor() })
        }
    }

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch(dispatcher) {
            wanted.filterNotNull().collect { bucket -> extract(bucket) }
        }
    }

    private fun extract(bucket: Long) {
        val forGeneration = generation
        val forUri = uri ?: return
        if (failedGeneration == forGeneration) return
        if (extractor == null || extractorUri != forUri) {
            closeExtractor()
            extractorExists = true
            val created = try {
                extractorFactory()
            } catch (error: RuntimeException) {
                extractorExists = false
                failedGeneration = forGeneration
                return
            }
            val opened = try {
                created.open(forUri)
            } catch (error: RuntimeException) {
                false
            }
            if (!opened) {
                runCatching { created.close() }
                extractorExists = false
                failedGeneration = forGeneration
                return
            }
            extractor = created
            extractorUri = forUri
            if (forGeneration != generation) {
                closeExtractor()
                return
            }
        }
        val started = clock()
        val bitmap = try {
            extractor?.frameAt(bucket, maxWidthPx)
        } catch (error: RuntimeException) {
            null
        }
        onTiming(clock() - started)
        if (bitmap == null) {
            // Let the same second be asked for again: an equal StateFlow value would be dropped.
            if (wanted.value == bucket) wanted.value = null
            return
        }
        if (forGeneration != generation) return
        synchronized(cache) { cache[bucket] = bitmap }
        // Only the newest wish is shown; an older one is kept in the cache for later.
        if (wanted.value == bucket) _frame.value = PreviewFrame(bucket, bitmap)
    }

    private fun closeExtractor() {
        val old = extractor
        extractor = null
        extractorUri = null
        extractorExists = false
        if (old != null) runCatching { old.close() }
    }
}
