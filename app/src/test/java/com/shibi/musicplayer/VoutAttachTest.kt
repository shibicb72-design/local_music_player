package com.shibi.musicplayer

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.video.VoutAttach
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.videolan.libvlc.AWindow
import org.videolan.libvlc.interfaces.IVLCVout

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VoutAttachTest {

    @Test
    fun libVlcAttachViewsThrowsWithTwoRawSurfaces() {
        val window = AWindow(null)
        window.setVideoSurface(Surface(SurfaceTexture(false)), null)
        window.setSubtitlesSurface(Surface(SurfaceTexture(false)), null)

        val error = assertThrows(IllegalArgumentException::class.java) { window.attachViews(null) }

        assertEquals("invalid state", error.message)
    }

    @Test
    fun attachRawSurfacesLeavesTheVoutAttached() {
        val window = AWindow(null)
        var surfacesCreated = 0
        window.addCallback(object : IVLCVout.Callback {
            override fun onSurfacesCreated(vlcVout: IVLCVout) {
                surfacesCreated++
            }

            override fun onSurfacesDestroyed(vlcVout: IVLCVout) = Unit
        })

        VoutAttach.attachRawSurfaces(window, Surface(SurfaceTexture(false)), Surface(SurfaceTexture(false)), null)

        assertTrue(window.areViewsAttached())
        assertEquals(1, surfacesCreated)
    }

    @Test
    fun otherAttachErrorsStillThrow() {
        val window = AWindow(null)
        VoutAttach.attachRawSurfaces(window, Surface(SurfaceTexture(false)), Surface(SurfaceTexture(false)), null)

        assertThrows(IllegalStateException::class.java) {
            VoutAttach.attachRawSurfaces(window, Surface(SurfaceTexture(false)), Surface(SurfaceTexture(false)), null)
        }
    }
}
