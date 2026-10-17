package com.shibi.musicplayer

import com.shibi.musicplayer.domain.OpenSourceNotices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceNoticesTest {

    @Test
    fun sourcesJarPointsAtTheExactVersionOnMavenCentral() {
        assertEquals(
            "https://repo1.maven.org/maven2/org/videolan/android/libvlc-all/3.7.7/libvlc-all-3.7.7-sources.jar",
            OpenSourceNotices.libVlcSourcesJarUrl("3.7.7"),
        )
    }

    @Test
    fun upstreamSourceLinksAreHttps() {
        listOf(OpenSourceNotices.LibVlcJniUrl, OpenSourceNotices.VlcUrl, OpenSourceNotices.ApacheUrl).forEach {
            assertTrue(it, it.startsWith("https://"))
        }
    }

    /** Hard-wrapped licence lines reflow so narrow screens do not wrap them twice. */
    @Test
    fun paragraphsJoinWrappedLinesAndSplitOnBlankLines() {
        val text = "  GNU LESSER\n  Version 2.1\n\nThe licenses for most  software\r\nare designed.\n \n\n  a) First item\n"
        assertEquals(
            listOf("GNU LESSER Version 2.1", "The licenses for most software are designed.", "a) First item"),
            OpenSourceNotices.paragraphs(text),
        )
    }

    @Test
    fun paragraphsOfBlankTextIsEmpty() {
        assertEquals(emptyList<String>(), OpenSourceNotices.paragraphs(" \n\n \r\n"))
    }
}
