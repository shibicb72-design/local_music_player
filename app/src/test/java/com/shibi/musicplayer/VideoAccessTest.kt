package com.shibi.musicplayer

import android.Manifest
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.domain.VideoAccess.Companion.READ_EXTERNAL_STORAGE
import com.shibi.musicplayer.domain.VideoAccess.Companion.READ_MEDIA_VIDEO
import com.shibi.musicplayer.domain.VideoAccess.Companion.READ_MEDIA_VISUAL_USER_SELECTED
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoAccessTest {
    @Test
    fun api34AndUp_videoPermissionIsFull() {
        for (sdk in listOf(34, 35, 36)) {
            assertEquals(VideoAccess.Full, VideoAccess.classify(sdk, setOf(READ_MEDIA_VIDEO)))
            assertEquals(
                VideoAccess.Full,
                VideoAccess.classify(sdk, setOf(READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)),
            )
        }
    }

    @Test
    fun api34AndUp_userSelectedOnlyIsPartial() {
        assertEquals(VideoAccess.Partial, VideoAccess.classify(34, setOf(READ_MEDIA_VISUAL_USER_SELECTED)))
        assertEquals(VideoAccess.Partial, VideoAccess.classify(36, setOf(READ_MEDIA_VISUAL_USER_SELECTED)))
    }

    @Test
    fun api34AndUp_nothingOrLegacyStorageIsDenied() {
        assertEquals(VideoAccess.Denied, VideoAccess.classify(34, emptySet()))
        assertEquals(VideoAccess.Denied, VideoAccess.classify(35, setOf(READ_EXTERNAL_STORAGE)))
    }

    @Test
    fun api33_onlyTheVideoPermissionCounts() {
        assertEquals(VideoAccess.Full, VideoAccess.classify(33, setOf(READ_MEDIA_VIDEO)))
        assertEquals(VideoAccess.Denied, VideoAccess.classify(33, setOf(READ_MEDIA_VISUAL_USER_SELECTED)))
        assertEquals(VideoAccess.Denied, VideoAccess.classify(33, setOf(READ_EXTERNAL_STORAGE)))
        assertEquals(VideoAccess.Denied, VideoAccess.classify(33, emptySet()))
    }

    @Test
    fun api26To32_legacyStorageIsFull() {
        for (sdk in 26..32) {
            assertEquals(VideoAccess.Full, VideoAccess.classify(sdk, setOf(READ_EXTERNAL_STORAGE)))
            assertEquals(VideoAccess.Denied, VideoAccess.classify(sdk, setOf(READ_MEDIA_VIDEO)))
            assertEquals(VideoAccess.Denied, VideoAccess.classify(sdk, emptySet()))
        }
    }

    @Test
    fun permissionsToRequest_perApiLevel() {
        val both = listOf(READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)
        assertEquals(both, VideoAccess.permissionsToRequest(34))
        assertEquals(both, VideoAccess.permissionsToRequest(36))
        assertEquals(listOf(READ_MEDIA_VIDEO), VideoAccess.permissionsToRequest(33))
        assertEquals(listOf(READ_EXTERNAL_STORAGE), VideoAccess.permissionsToRequest(32))
        assertEquals(listOf(READ_EXTERNAL_STORAGE), VideoAccess.permissionsToRequest(26))
    }

    @Test
    fun grantingTheRequestSetAlwaysGivesFullAccess() {
        for (sdk in 26..36) {
            assertEquals(
                VideoAccess.Full,
                VideoAccess.classify(sdk, VideoAccess.permissionsToRequest(sdk).toSet()),
            )
        }
    }

    @Test
    fun permissionNamesMatchTheAndroidConstants() {
        assertEquals(Manifest.permission.READ_MEDIA_VIDEO, READ_MEDIA_VIDEO)
        assertEquals(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED, READ_MEDIA_VISUAL_USER_SELECTED)
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE)
    }
}
