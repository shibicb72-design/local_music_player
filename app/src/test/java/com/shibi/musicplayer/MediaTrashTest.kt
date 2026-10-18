package com.shibi.musicplayer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.library.MediaTrash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29])
class MediaTrashTest {
    private val application = RuntimeEnvironment.getApplication()
    private lateinit var provider: IdProvider

    @Before
    fun setUp() {
        provider = Robolectric.buildContentProvider(IdProvider::class.java)
            .create(MediaStore.AUTHORITY)
            .get()
    }

    @Test
    fun notSupportedBelowApi30() {
        assertFalse(MediaTrash.isSupported())
    }

    @Test
    fun requestIsNullBelowApi30() {
        assertNull(MediaTrash.request(application, listOf("content://media/external/audio/media/1"), true))
    }

    @Test
    fun stillVisibleReturnsOnlyUrisThatAreStillPresent() {
        provider.present += "1"
        provider.present += "3"
        val uris = listOf(1L, 2L, 3L).map { "content://media/external/audio/media/$it" }

        val visible = MediaTrash.stillVisible(application, uris)

        assertEquals(setOf(uris[0], uris[2]), visible)
    }

    @Test
    fun stillVisibleTreatsAFailingQueryAsNotVisible() {
        provider.failure = IllegalStateException("boom")
        val uri = "content://media/external/audio/media/1"

        assertTrue(MediaTrash.stillVisible(application, listOf(uri)).isEmpty())
    }
}

class IdProvider : ContentProvider() {
    val present = mutableSetOf<String>()
    var failure: RuntimeException? = null

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        failure?.let { throw it }
        val cursor = MatrixCursor(arrayOf(MediaStore.MediaColumns._ID))
        val id = uri.lastPathSegment
        if (id in present) cursor.addRow(arrayOf<Any?>(id?.toLong()))
        return cursor
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0
}
