package com.shibi.musicplayer.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shibi.musicplayer.domain.LibrarySorts
import com.shibi.musicplayer.domain.SortSpec
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Each list's own sort: Videos under `video_sort`, Songs under `music_sort`. Values that are not
 * one of the list's options (an old build, a corrupt file) read as its default.
 */
interface SortPrefs {
    val videoSort: Flow<SortSpec>
    val musicSort: Flow<SortSpec>

    suspend fun setVideoSort(spec: SortSpec)
    suspend fun setMusicSort(spec: SortSpec)

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

    override suspend fun setVideoSort(spec: SortSpec) {
        dataStore.edit { prefs -> prefs[VideoSortKey] = spec.encode() }
    }

    override suspend fun setMusicSort(spec: SortSpec) {
        dataStore.edit { prefs -> prefs[MusicSortKey] = spec.encode() }
    }

    private companion object {
        val VideoSortKey = stringPreferencesKey("video_sort")
        val MusicSortKey = stringPreferencesKey("music_sort")
    }
}

/** Kept in memory only: the view models' test seam default. */
internal class InMemorySortPrefs(
    video: SortSpec = LibrarySorts.VideoDefault,
    music: SortSpec = LibrarySorts.MusicDefault,
) : SortPrefs {
    override val videoSort = MutableStateFlow(video)
    override val musicSort = MutableStateFlow(music)

    override suspend fun setVideoSort(spec: SortSpec) {
        videoSort.value = spec
    }

    override suspend fun setMusicSort(spec: SortSpec) {
        musicSort.value = spec
    }
}
