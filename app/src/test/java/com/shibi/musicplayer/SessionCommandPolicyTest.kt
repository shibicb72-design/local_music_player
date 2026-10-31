package com.shibi.musicplayer

import com.shibi.musicplayer.playback.SessionCommandPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCommandPolicyTest {

    @Test
    fun ownProcessGetsCustomCommands() {
        assertTrue(SessionCommandPolicy.allowsCustomCommands(APP_UID, APP, APP_UID, APP))
    }

    @Test
    fun otherAppKeepsDefaultsOnly() {
        assertFalse(SessionCommandPolicy.allowsCustomCommands(10_201, "com.example.remote", APP_UID, APP))
    }

    @Test
    fun claimingOurPackageFromAnotherUidIsRejected() {
        assertFalse(SessionCommandPolicy.allowsCustomCommands(10_201, APP, APP_UID, APP))
    }

    @Test
    fun systemControllerOnAnotherUidKeepsDefaultsOnly() {
        assertFalse(SessionCommandPolicy.allowsCustomCommands(1_000, "com.android.systemui", APP_UID, APP))
    }

    @Test
    fun ourUidWithAnotherPackageIsRejected() {
        assertFalse(SessionCommandPolicy.allowsCustomCommands(APP_UID, "com.example.shared", APP_UID, APP))
    }

    private companion object {
        const val APP = "com.shibi.musicplayer"
        const val APP_UID = 10_123
    }
}
