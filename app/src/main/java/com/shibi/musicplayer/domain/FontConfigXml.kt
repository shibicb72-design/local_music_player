package com.shibi.musicplayer.domain

/** Builds the fontconfig file that gives libVLC's built-in fontconfig a persistent cache folder. */
object FontConfigXml {

    fun build(fontDirs: List<String>, cacheDir: String): String = buildString {
        append("<?xml version=\"1.0\"?>\n")
        append("<!DOCTYPE fontconfig SYSTEM \"fonts.dtd\">\n")
        append("<fontconfig>\n")
        fontDirs.forEach { append("    <dir>").append(escape(it)).append("</dir>\n") }
        append("    <cachedir>").append(escape(cacheDir)).append("</cachedir>\n")
        append("</fontconfig>\n")
    }

    fun hasCache(fileNames: List<String>?): Boolean = fileNames?.any { it.contains(".cache-") } == true

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
