package com.shibi.musicplayer.video

import android.content.Context
import android.system.Os
import com.shibi.musicplayer.domain.FontConfigXml
import java.io.File

/**
 * Points libVLC's built-in fontconfig at a real config with a persistent cache folder, so the
 * font scan happens once per install instead of once per process. Must run before any LibVLC exists.
 */
internal object FontConfigSetup {

    private val fontDirs = listOf("/system/fonts", "/product/fonts", "/system_ext/fonts")
    private var done = false

    /** True under Robolectric, where native libVLC and process environment changes must not run. */
    internal fun isRobolectric(): Boolean = android.os.Build.FINGERPRINT == "robolectric"

    fun prepare(context: Context) {
        if (isRobolectric()) return
        prepare(context, setEnvironment = true)
    }

    @Synchronized
    internal fun prepare(context: Context, setEnvironment: Boolean) {
        if (done) return
        runCatching {
            val base = File(context.noBackupFilesDir, "fontconfig")
            val cache = File(base, "cache")
            cache.mkdirs()
            base.mkdirs()
            val conf = File(base, "fonts.conf")
            val xml = FontConfigXml.build(fontDirs.filter { File(it).isDirectory }, cache.absolutePath)
            if (!conf.isFile || conf.readText() != xml) conf.writeText(xml)
            if (setEnvironment) {
                Os.setenv("FONTCONFIG_FILE", conf.absolutePath, true)
                Os.setenv("FONTCONFIG_PATH", base.absolutePath, true)
                done = true
            }
        }
    }

    fun cacheReady(context: Context): Boolean =
        FontConfigXml.hasCache(File(File(context.noBackupFilesDir, "fontconfig"), "cache").list()?.toList())
}
