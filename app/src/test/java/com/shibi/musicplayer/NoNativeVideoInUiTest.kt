package com.shibi.musicplayer

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * libVLC is a native library that Robolectric cannot load. Only the engine may name it;
 * composables, view models and the domain go through [com.shibi.musicplayer.video.VideoEngine].
 */
class NoNativeVideoInUiTest {

    private val sources = File("src/main/java/com/shibi/musicplayer")

    private fun filesNaming(pattern: Regex): Set<String> = sources.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filter { pattern.containsMatchIn(it.readText()) }
        .map { it.relativeTo(sources).invariantSeparatorsPath }
        .toSet()

    @Test
    fun onlyTheEngineNamesLibVlc() {
        assertTrue("run from the app module", sources.isDirectory)
        assertEquals(setOf("video/VlcVideoEngine.kt", "video/VoutAttach.kt"), filesNaming(Regex("""org\.videolan""")))
    }

    @Test
    fun composablesNeverNameTheVlcEngine() {
        // The app path builds it in VideoViewModel's constructor lambda, nowhere else.
        assertEquals(
            setOf("video/VlcVideoEngine.kt", "video/VideoViewModel.kt"),
            filesNaming(Regex("""\bVlcVideoEngine\(""")),
        )
        val ui = filesNaming(Regex("""org\.videolan|VlcVideoEngine|LibVLC|VLCVideoLayout"""))
            .filter { it.startsWith("ui/") }
        assertTrue("ui files naming libVLC: $ui", ui.isEmpty())
    }
}
