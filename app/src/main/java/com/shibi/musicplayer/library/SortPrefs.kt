package com.shibi.musicplayer.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.MovieSortDefault
import com.shibi.musicplayer.domain.SortSpec
import com.shibi.musicplayer.domain.VideoFilter
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Each list's own sort: Videos under `video_sort`, Songs under `music_sort`, the Movies filter
 * under `movie_sort`; the Videos filter under `video_filter`. Values that are not one of the
 * list's options (an old build, a corrupt file) read as its default.
 */
interface SortPrefs {
    val videoSort: Flow<SortSpec>
    val musicSort: Flow<SortSpec>

    suspend fun setVideoSort(spec: SortSpec)
    suspend fun setMusicSort(spec: SortSpec)

    // Defaults only for test stores that keep just the two sorts; the app's stores keep these.
    val videoFilter: Flow<VideoFilter> get() = flowOf(VideoFilter.All)
    val movieSort: Flow<SortSpec> get() = flowOf(MovieSortDefault)

    suspend fun setVideoFilter(filter: VideoFilter) = Unit
    suspend fun setMovieSort(spec: SortSpec) = Unit

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

    private companion object {
        val VideoSortKey = stringPreferencesKey("video_sort")
        val MusicSortKey = stringPreferencesKey("music_sort")
        val VideoFilterKey = stringPreferencesKey("video_filter")
        val MovieSortKey = stringPreferencesKey("movie_sort")
    }
}

/** Kept in memory only: the view models' test seam default. */
internal class InMemorySortPrefs(
    video: SortSpec = LibrarySorts.VideoDefault,
    music: SortSpec = LibrarySorts.MusicDefault,
    filter: VideoFilter = VideoFilter.All,
    movie: SortSpec = MovieSortDefault,
) : SortPrefs {
    override val videoSort = MutableStateFlow(video)
    override val musicSort = MutableStateFlow(music)
    override val videoFilter = MutableStateFlow(filter)
    override val movieSort = MutableStateFlow(movie)

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
}
