package com.shibi.musicplayer

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.video.FrameExtractor
import com.shibi.musicplayer.video.PreviewFrameLoader
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PreviewFrameLoaderTest {

    private val scope = CoroutineScope(SupervisorJob())

    @After
    fun tearDown() {
        scope.cancel()
    }

    private class FakeExtractor(
        private val extractMs: Long = 0L,
        private val nullAt: Set<Long> = emptySet(),
        private val openGate: CountDownLatch? = null,
        private val opening: CountDownLatch? = null,
    ) : FrameExtractor {
        val threads: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        val extractions = AtomicInteger()
        val opens = AtomicInteger()

        @Volatile
        var closed = false

        override fun open(uri: String): Boolean {
            threads += Thread.currentThread().name
            opens.incrementAndGet()
            opening?.countDown()
            openGate?.await(5, TimeUnit.SECONDS)
            return true
        }

        override fun frameAt(positionMs: Long, maxWidthPx: Int): Bitmap? {
            threads += Thread.currentThread().name
            extractions.incrementAndGet()
            if (extractMs > 0L) Thread.sleep(extractMs)
            return if (positionMs in nullAt) null else Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        }

        override fun close() {
            threads += Thread.currentThread().name
            closed = true
        }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for $what")
            Thread.sleep(2)
        }
    }

    private fun loader(
        extractors: MutableList<FakeExtractor>,
        make: () -> FakeExtractor = { FakeExtractor() },
        cacheSize: Int = 24,
    ) = PreviewFrameLoader(
        scope = scope,
        extractorFactory = { make().also { extractors += it } },
        cacheSize = cacheSize,
        clock = { System.nanoTime() / 1_000_000L },
    )

    @Test
    fun settingAVideoNeverCreatesAnExtractor() {
        val extractors = mutableListOf<FakeExtractor>()
        val loader = loader(extractors)
        loader.setVideo("content://video/1")
        loader.setVideo("content://video/2")
        assertTrue(extractors.isEmpty())
        assertNull(loader.frame.value)
    }

    @Test
    fun aDragBurstIsCoalescedAndEndsOnTheLastSecond() {
        val extractors = mutableListOf<FakeExtractor>()
        val loader = loader(extractors, make = { FakeExtractor(extractMs = 30L) })
        loader.setVideo("content://video/1")
        for (second in 1..100) {
            loader.request(second * 1_000L)
            val until = System.nanoTime() + 1_000_000L
            while (System.nanoTime() < until) {
                Thread.onSpinWait()
            }
        }
        waitUntil("the last frame") { loader.frame.value?.bucketMs == 100_000L }
        assertEquals(1, extractors.size)
        assertTrue("extractions: ${extractors[0].extractions.get()}", extractors[0].extractions.get() <= 6)
    }

    @Test
    fun aCachedSecondIsPublishedWithoutExtracting() {
        val extractors = mutableListOf<FakeExtractor>()
        val loader = loader(extractors)
        loader.setVideo("content://video/1")
        loader.request(3_000L)
        waitUntil("3 s") { loader.frame.value?.bucketMs == 3_000L }
        loader.request(4_200L)
        waitUntil("4 s") { loader.frame.value?.bucketMs == 4_000L }
        val before = extractors[0].extractions.get()
        loader.request(3_900L)
        assertEquals(3_000L, loader.frame.value?.bucketMs)
        assertEquals(before, extractors[0].extractions.get())
    }

    @Test
    fun theCacheHoldsAtMostItsSize() {
        val extractors = mutableListOf<FakeExtractor>()
        val loader = loader(extractors, cacheSize = 3)
        loader.setVideo("content://video/1")
        for (second in 1..5) {
            loader.request(second * 1_000L)
            waitUntil("$second s") { loader.frame.value?.bucketMs == second * 1_000L }
        }
        val extracted = extractors[0].extractions.get()
        assertEquals(5, extracted)
        // 5 s is cached; 1 s was evicted and must be extracted again.
        loader.request(5_000L)
        assertEquals(extracted, extractors[0].extractions.get())
        loader.request(1_000L)
        waitUntil("1 s again") { extractors[0].extractions.get() == extracted + 1 }
    }

    @Test
    fun everyExtractorCallRunsOnThePreviewThread() {
        val extractors = mutableListOf<FakeExtractor>()
        val loader = loader(extractors)
        loader.setVideo("content://video/1")
        loader.request(1_000L)
        waitUntil("1 s") { loader.frame.value != null }
        loader.close()
        waitUntil("closed") { extractors[0].closed }
        // Coroutine debug mode (on in tests) appends " @coroutine#n" to the thread name.
        val names = extractors[0].threads.toSet()
        assertTrue("threads: $names", names.isNotEmpty() && names.all { it.startsWith("theatrum-preview") })
        assertFalse(Thread.currentThread().name.startsWith("theatrum-preview"))
    }

    @Test
    fun anotherVideoAndCloseCloseTheExtractor() {
        val extractors = mutableListOf<FakeExtractor>()
        val loader = loader(extractors)
        loader.setVideo("content://video/1")
        loader.request(1_000L)
        waitUntil("1 s") { loader.frame.value != null }
        loader.setVideo("content://video/2")
        waitUntil("first closed") { extractors[0].closed }
        assertNull("the old picture is gone", loader.frame.value)
        loader.request(2_000L)
        waitUntil("second opened") { extractors.size == 2 && loader.frame.value != null }
        loader.close()
        waitUntil("second closed") { extractors[1].closed }
    }

    @Test
    fun aMissingBitmapKeepsThePreviousFrame() {
        val extractors = mutableListOf<FakeExtractor>()
        val loader = loader(extractors, make = { FakeExtractor(nullAt = setOf(2_000L)) })
        loader.setVideo("content://video/1")
        loader.request(1_000L)
        waitUntil("1 s") { loader.frame.value?.bucketMs == 1_000L }
        val kept = loader.frame.value
        loader.request(2_000L)
        waitUntil("2 s tried") { extractors[0].extractions.get() == 2 }
        Thread.sleep(50)
        assertNotNull(kept)
        assertEquals(kept, loader.frame.value)
        // The failed second is asked for again, not swallowed as an equal StateFlow value.
        loader.request(2_000L)
        waitUntil("2 s retried") { extractors[0].extractions.get() == 3 }
    }

    @Test
    fun closingWhileTheExtractorIsOpeningStillClosesIt() {
        val extractors = mutableListOf<FakeExtractor>()
        val opening = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val loader = loader(extractors, make = { FakeExtractor(openGate = gate, opening = opening) })
        loader.setVideo("content://video/1")
        loader.request(1_000L)
        assertTrue(opening.await(5, TimeUnit.SECONDS))
        loader.close()
        gate.countDown()
        waitUntil("the late extractor closed") { extractors[0].closed }
    }
}
