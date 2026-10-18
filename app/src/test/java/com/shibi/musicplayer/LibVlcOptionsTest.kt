package com.shibi.musicplayer

import com.shibi.musicplayer.video.SubtitleTextSize
import com.shibi.musicplayer.video.VlcVideoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LibVlcOptionsTest {

    @Test
    fun optionsAcceptAppendsLikeLibVlcDoes() {
        SubtitleTextSize.entries.forEach { size ->
            val list: MutableList<String> = VlcVideoEngine.libVlcOptions(size)
            val before = list.size
            list.add("--aout=opensles")
            list.add("--android-display-chroma")
            assertEquals(before + 2, list.size)
        }
    }

    @Test
    fun optionsKeepFileCaching() {
        SubtitleTextSize.entries.forEach { size ->
            val list = VlcVideoEngine.libVlcOptions(size)
            assertEquals("--file-caching=300", list.first())
        }
    }

    @Test
    fun eachCallIsAFreshList() {
        SubtitleTextSize.entries.forEach { size ->
            val first = VlcVideoEngine.libVlcOptions(size)
            val second = VlcVideoEngine.libVlcOptions(size)
            assertNotSame(first, second)
        }
    }

    @Test
    fun fontSizeOptionOnlyWhenSet() {
        SubtitleTextSize.entries.forEach { size ->
            val list = VlcVideoEngine.libVlcOptions(size)
            if (size.relativeFontSize == null) {
                assertEquals(1, list.size)
            } else {
                assertEquals(2, list.size)
                assertTrue(list[1].startsWith("--freetype-rel-fontsize="))
            }
        }
    }
}
