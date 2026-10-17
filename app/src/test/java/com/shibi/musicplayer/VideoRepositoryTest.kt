package com.shibi.musicplayer

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shibi.musicplayer.domain.Video
import com.shibi.musicplayer.domain.VideoAccess
import com.shibi.musicplayer.library.VideoLibraryViewModel
import com.shibi.musicplayer.library.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class VideoRepositoryTest {
    private val application = RuntimeEnvironment.getApplication()
    private lateinit var provider: FakeMediaProvider

    @Before
    fun setUp() {
        provider = Robolectric.buildContentProvider(FakeMediaProvider::class.java)
            .create(MediaStore.AUTHORITY)
            .get()
    }

    @Test
    fun loadVideosQueriesTheExternalVideoCollectionWithTheSpecProjectionAndSort() {
        VideoRepository(application).loadVideos()

        assertEquals(Uri.parse("content://media/external/video/media"), provider.lastUri)
        assertArrayEquals(
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            ),
            provider.lastProjection,
        )
        assertEquals("${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC", provider.lastSortOrder)
    }

    @Test
    fun loadVideosMapsAFullRow() {
        provider.rows += row(
            id = 7L,
            displayName = "Trip.mkv",
            title = "Trip",
            duration = 3_723_000L,
            size = 1_400_000_000L,
            width = 1920,
            height = 1080,
            mime = "video/x-matroska",
            dateAdded = 1_700_000_000L,
            bucket = "Camera",
        )

        val videos = VideoRepository(application).loadVideos()

        assertEquals(
            listOf(
                Video(
                    id = 7L,
                    title = "Trip",
                    displayName = "Trip.mkv",
                    durationMs = 3_723_000L,
                    sizeBytes = 1_400_000_000L,
                    width = 1920,
                    height = 1080,
                    mimeType = "video/x-matroska",
                    dateAddedSeconds = 1_700_000_000L,
                    folder = "Camera",
                    contentUri = "content://media/external/video/media/7",
                ),
            ),
            videos,
        )
    }

    @Test
    fun loadVideosTurnsNullAndZeroColumnsIntoMissingValues() {
        provider.rows += row(id = 8L, displayName = "clip.wmv", size = 0L, height = 0, bucket = " ")
        provider.rows += row(id = 9L, title = MediaStore.UNKNOWN_STRING, duration = -1L, width = 0)

        val (clip, unnamed) = VideoRepository(application).loadVideos()

        assertEquals("clip", clip.title)
        assertEquals("clip.wmv", clip.displayName)
        assertNull(clip.durationMs)
        assertNull(clip.sizeBytes)
        assertNull(clip.width)
        assertNull(clip.height)
        assertNull(clip.mimeType)
        assertEquals(0L, clip.dateAddedSeconds)
        assertNull(clip.folder)
        assertEquals("content://media/external/video/media/8", clip.contentUri)

        assertEquals(application.getString(R.string.unknown_title), unnamed.title)
        assertEquals("", unnamed.displayName)
        assertNull(unnamed.durationMs)
        assertNull(unnamed.width)
    }

    @Test
    fun loadVideosReturnsAnEmptyListWhenTheProviderReturnsNoCursor() {
        provider.returnNull = true

        assertEquals(emptyList<Video>(), VideoRepository(application).loadVideos())
    }

    @Test
    fun changesEmitsWhenTheVideoCollectionChangesAndUnregistersOnCancel() {
        val repository = VideoRepository(application)
        val resolver = shadowOf(application.contentResolver)
        var emissions = 0
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            repository.changes().collect { emissions++ }
        }
        idleMain()
        assertEquals(1, resolver.getContentObservers(repository.collection).size)

        application.contentResolver.notifyChange(repository.collection, null)
        idleMain()
        assertTrue(emissions >= 1)

        job.cancel()
        idleMain()
        assertTrue(resolver.getContentObservers(repository.collection).isEmpty())
    }

    @Test
    fun viewModelWithoutPermissionIsDeniedAndNeverQueriesMediaStore() {
        shadowOf(application).denyPermissions(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        provider.rows += row(id = 1L, displayName = "a.mp4")

        val viewModel = newViewModel()
        viewModel.refresh()
        idleMain()

        assertEquals(VideoAccess.Denied, viewModel.uiState.value.access)
        assertEquals(emptyList<Video>(), viewModel.uiState.value.videos)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(provider.lastUri)
    }

    @Test
    fun viewModelWithUserSelectedAccessIsPartialAndLoadsTheChosenVideos() {
        shadowOf(application).denyPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        provider.rows += row(id = 1L, displayName = "a.mp4")

        val viewModel = newViewModel()
        viewModel.refresh()
        idleMain()

        assertEquals(VideoAccess.Partial, viewModel.uiState.value.access)
        assertEquals(listOf("a"), viewModel.uiState.value.videos.map { it.title })
    }

    @Test
    fun viewModelReChecksAccessOnEveryRefresh() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        provider.rows += row(id = 1L, displayName = "a.mp4")
        val viewModel = newViewModel()
        viewModel.refresh()
        idleMain()
        assertEquals(VideoAccess.Full, viewModel.uiState.value.access)
        assertEquals(1, viewModel.uiState.value.videos.size)

        shadowOf(application).denyPermissions(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        viewModel.refresh()
        idleMain()

        assertEquals(VideoAccess.Denied, viewModel.uiState.value.access)
        assertEquals(emptyList<Video>(), viewModel.uiState.value.videos)
    }

    @Test
    fun viewModelReportsAQueryFailureWithoutCrashing() {
        shadowOf(application).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        provider.failure = SecurityException("revoked")

        val viewModel = newViewModel()
        viewModel.refresh()
        idleMain()

        assertTrue(viewModel.uiState.value.hasError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private fun newViewModel() = VideoLibraryViewModel(
        application = application,
        repository = VideoRepository(application),
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun row(
        id: Long,
        displayName: String? = null,
        title: String? = null,
        duration: Long? = null,
        size: Long? = null,
        width: Int? = null,
        height: Int? = null,
        mime: String? = null,
        dateAdded: Long? = null,
        bucket: String? = null,
    ): Array<Any?> = arrayOf(id, displayName, title, duration, size, width, height, mime, dateAdded, bucket)
}

class FakeMediaProvider : ContentProvider() {
    val rows = mutableListOf<Array<Any?>>()
    var returnNull = false
    var failure: RuntimeException? = null
    var lastUri: Uri? = null
    var lastProjection: Array<String>? = null
    var lastSortOrder: String? = null

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        failure?.let { throw it }
        lastUri = uri
        lastProjection = projection
        lastSortOrder = sortOrder
        if (returnNull) return null
        // Fixed column order, deliberately not the projection order, so the mapping must look columns up by name.
        val cursor = MatrixCursor(
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            ).reversedArray(),
        )
        rows.forEach { cursor.addRow(it.reversedArray()) }
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
