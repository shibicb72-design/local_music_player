package com.shibi.musicplayer.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shibi.musicplayer.domain.AlbumSort
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.MovieSortDefault
import com.shibi.musicplayer.domain.MusicView
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.VideoFilter
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Each list's own sort: Videos under `video_sort`, Songs under `music_sort`, the Movies filter
 * under `movie_sort`; the Videos filter under `video_filter`; the Songs screen's view under
 * `music_view`; the Albums grid's sort under `album_sort`. Values that are not one of the
 * list's options (an old build, a corrupt file) read as its default.
 */
interface SortPrefs {
    val videoSort: Flow<SortSpec>
    val musicSort: Flow<SortSpec>

    val videoFilter: Flow<VideoFilter>
    val movieSort: Flow<SortSpec>
    val musicView: Flow<MusicView>
    val albumSort: Flow<AlbumSort>

    suspend fun setVideoSort(spec: SortSpec)
    suspend fun setMusicSort(spec: SortSpec)
    suspend fun setVideoFilter(filter: VideoFilter)
    suspend fun setMovieSort(spec: SortSpec)
    suspend fun setMusicView(view: MusicView)
    suspend fun setAlbumSort(sort: AlbumSort)

    companion object {
        /** The app's store, shared by both lists' view models. */
        fun get(context: Context): SortPrefs = DataStoreSortPrefs(context.applicationContext.libraryDataStore)
    }
}

// Its own file: a second DataStore over "video" (VideoPrefs) would crash at the first read.
private val Context.libraryDataStore: DataStore<Preferences> by preferencesDataStore(name = "library")

internal class DataStoreSortPrefs(private val dataStore: DataStore<Preferences>) : SortPrefs {

    private val prefs: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override val videoSort: Flow<SortSpec> = prefs.map { prefs ->
        SortSpec.decode(prefs[VideoSortKey], LibrarySorts.VideoOptions, LibrarySorts.VideoDefault)
    }

    override val musicSort: Flow<SortSpec> = prefs.map { prefs ->
        SortSpec.decode(prefs[MusicSortKey], LibrarySorts.MusicOptions, LibrarySorts.MusicDefault)
    }

    override val videoFilter: Flow<VideoFilter> = prefs.map { prefs -> VideoFilter.fromKey(prefs[VideoFilterKey]) }

    override val movieSort: Flow<SortSpec> = prefs.map { prefs ->
        SortSpec.decode(prefs[MovieSortKey], LibrarySorts.VideoOptions, MovieSortDefault)
    }

    override val musicView: Flow<MusicView> = prefs.map { prefs -> MusicView.fromKey(prefs[MusicViewKey]) }

    override val albumSort: Flow<AlbumSort> = prefs.map { prefs -> AlbumSort.fromKey(prefs[AlbumSortKey]) }

    override suspend fun setVideoSort(spec: SortSpec) {
        dataStore.edit { prefs -> prefs[VideoSortKey] = spec.encode() }
    }

    override suspend fun setMusicSort(spec: SortSpec) {
        dataStore.edit { prefs -> prefs[MusicSortKey] = spec.encode() }
    }

    override suspend fun setVideoFilter(filter: VideoFilter) {
        dataStore.edit { prefs -> prefs[VideoFilterKey] = filter.key }
    }

    override suspend fun setMovieSort(spec: SortSpec) {
        dataStore.edit { prefs -> prefs[MovieSortKey] = spec.encode() }
    }

    override suspend fun setMusicView(view: MusicView) {
        dataStore.edit { prefs -> prefs[MusicViewKey] = view.key }
    }

    override suspend fun setAlbumSort(sort: AlbumSort) {
        dataStore.edit { prefs -> prefs[AlbumSortKey] = sort.key }
    }

    private companion object {
        val VideoSortKey = stringPreferencesKey("video_sort")
        val MusicSortKey = stringPreferencesKey("music_sort")
        val VideoFilterKey = stringPreferencesKey("video_filter")
        val MovieSortKey = stringPreferencesKey("movie_sort")
        val MusicViewKey = stringPreferencesKey("music_view")
        val AlbumSortKey = stringPreferencesKey("album_sort")
    }
}

/** Kept in memory only: the view models' test seam default. */
internal class InMemorySortPrefs(
    video: SortSpec = LibrarySorts.VideoDefault,
    music: SortSpec = LibrarySorts.MusicDefault,
    filter: VideoFilter = VideoFilter.All,
    movie: SortSpec = MovieSortDefault,
    view: MusicView = MusicView.Songs,
    albumSort: AlbumSort = AlbumSort.Title,
) : SortPrefs {
    override val videoSort = MutableStateFlow(video)
    override val musicSort = MutableStateFlow(music)
    override val videoFilter = MutableStateFlow(filter)
    override val movieSort = MutableStateFlow(movie)
    override val musicView = MutableStateFlow(view)
    override val albumSort = MutableStateFlow(albumSort)

    override suspend fun setVideoSort(spec: SortSpec) {
        videoSort.value = spec
    }

    override suspend fun setMusicSort(spec: SortSpec) {
        musicSort.value = spec
    }

    override suspend fun setVideoFilter(filter: VideoFilter) {
        videoFilter.value = filter
    }

    override suspend fun setMovieSort(spec: SortSpec) {
        movieSort.value = spec
    }

    override suspend fun setMusicView(view: MusicView) {
        musicView.value = view
    }

    override suspend fun setAlbumSort(sort: AlbumSort) {
        albumSort.value = sort
    }
}
