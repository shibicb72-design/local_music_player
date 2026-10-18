package com.shibi.musicplayer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.video.FontConfigSetup
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FontConfigSetupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val base = File(context.noBackupFilesDir, "fontconfig")
    private val conf = File(base, "fonts.conf")

    @Test
    fun prepareWritesConfigAndEnvironment() {
        FontConfigSetup.prepare(context, setEnvironment = false)
        val first = conf.readText()
        assertTrue(first.contains(File(base, "cache").absolutePath))
        FontConfigSetup.prepare(context, setEnvironment = false)
        assertEquals(first, conf.readText())
    }

    @Test
    fun cacheReadyFollowsCacheFiles() {
        val cache = File(base, "cache")
        cache.mkdirs()
        cache.listFiles()?.forEach { it.delete() }
        assertFalse(FontConfigSetup.cacheReady(context))
        File(cache, "x-aarch64.cache-7").writeText("x")
        assertTrue(FontConfigSetup.cacheReady(context))
    }
}
