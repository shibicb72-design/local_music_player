package com.shibi.musicplayer

import com.shibi.musicplayer.domain.PositionThrottle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionThrottleTest {

    @Test
    fun aSmallForwardMoveIsNotPublished() {
        assertFalse(PositionThrottle.shouldPublish(publishedMs = 1_000L, newMs = 1_100L, otherFieldsChanged = false))
    }

    @Test
    fun aFullStepForwardIsPublished() {
        assertTrue(PositionThrottle.shouldPublish(publishedMs = 1_000L, newMs = 1_250L, otherFieldsChanged = false))
    }

    @Test
    fun aSeekBackwardsIsPublishedAtOnce() {
        assertTrue(PositionThrottle.shouldPublish(publishedMs = 1_000L, newMs = 700L, otherFieldsChanged = false))
    }

    @Test
    fun aTinyMoveIsPublishedWhenSomethingElseChanged() {
        assertTrue(PositionThrottle.shouldPublish(publishedMs = 1_000L, newMs = 1_010L, otherFieldsChanged = true))
    }

    @Test
    fun anUnchangedPositionIsNotPublished() {
        assertFalse(PositionThrottle.shouldPublish(publishedMs = 1_000L, newMs = 1_000L, otherFieldsChanged = false))
    }
}
