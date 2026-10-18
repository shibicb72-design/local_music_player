package com.shibi.musicplayer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** V20 trash wording: counts, kinds, and SD/USB mentioned only for removable storage. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TrashMessagesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun plural(id: Int, n: Int) = context.resources.getQuantityString(id, n, n)

    @Test
    fun movedAndRestoredMessages() {
        assertEquals("Moved 5 videos to trash", plural(R.plurals.trash_moved_videos, 5))
        assertEquals("Moved 1 video to trash", plural(R.plurals.trash_moved_videos, 1))
        assertEquals("Moved 1 song to trash", plural(R.plurals.trash_moved_songs, 1))
        assertEquals("Moved 3 of 5 to trash", context.getString(R.string.trash_moved_partial, 3, 5))
        assertEquals("Restored 5 videos", plural(R.plurals.trash_restored_videos, 5))
    }

    @Test
    fun onlyRemovableReasonMentionsSdCard() {
        for (n in listOf(1, 3)) {
            assertTrue(plural(R.plurals.trash_reason_removable, n).contains("SD"))
            assertFalse(plural(R.plurals.trash_reason_permission, n).contains("SD"))
            assertFalse(plural(R.plurals.trash_reason_not_trashed, n).contains("SD"))
        }
    }
}
