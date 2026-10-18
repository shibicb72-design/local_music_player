package com.shibi.musicplayer

import com.shibi.musicplayer.domain.FontConfigXml
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontConfigXmlTest {

    @Test
    fun buildListsDirsCacheAndDoctype() {
        val xml = FontConfigXml.build(listOf("/system/fonts", "/product/fonts"), "/data/cache")
        assertTrue(xml.contains("<dir>/system/fonts</dir>"))
        assertTrue(xml.contains("<dir>/product/fonts</dir>"))
        assertTrue(xml.contains("<cachedir>/data/cache</cachedir>"))
        assertTrue(xml.contains("<!DOCTYPE fontconfig SYSTEM \"fonts.dtd\">"))
        assertTrue(xml.trimEnd().endsWith("</fontconfig>"))
    }

    @Test
    fun buildEscapesSpecialCharacters() {
        val xml = FontConfigXml.build(listOf("/a&b"), "/c<d")
        assertTrue(xml.contains("/a&amp;b"))
        assertTrue(xml.contains("/c&lt;d"))
    }

    @Test
    fun hasCacheDetectsCacheFiles() {
        assertTrue(FontConfigXml.hasCache(listOf("abc-le64.cache-7")))
        assertTrue(FontConfigXml.hasCache(listOf("x-aarch64.cache-7")))
        assertFalse(FontConfigXml.hasCache(emptyList()))
        assertFalse(FontConfigXml.hasCache(null))
        assertFalse(FontConfigXml.hasCache(listOf("fonts.conf")))
    }
}
