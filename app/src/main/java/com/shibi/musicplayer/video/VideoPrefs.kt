package com.shibi.musicplayer.video

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shibi.musicplayer.domain.AspectMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Video preferences: the last fullscreen aspect mode, the brightness swipe toggle, and the
 * tracks sheet's subtitle text size and hardware decoding.
 */
interface VideoPrefs {
    val aspectMode: Flow<AspectMode>

    /** Left-half brightness swipe in fullscreen; on unless the user turns it off. */
    val brightnessSwipe: Flow<Boolean>

    /** SRT and VTT text size; ASS keeps its own styling. */
    val subtitleTextSize: Flow<SubtitleTextSize>

    /** Auto (hardware first) unless the user turns it off. */
    val hardwareDecoding: Flow<Boolean>

    suspend fun setAspectMode(mode: AspectMode)
    suspend fun setBrightnessSwipe(enabled: Boolean)
    suspend fun setSubtitleTextSize(size: SubtitleTextSize)
    suspend fun setHardwareDecoding(enabled: Boolean)
}

private val Context.videoDataStore: DataStore<Preferences> by preferencesDataStore(name = "video")

internal class DataStoreVideoPrefs(private val dataStore: DataStore<Preferences>) : VideoPrefs {

    constructor(context: Context) : this(context.videoDataStore)

    override val aspectMode: Flow<AspectMode> =
        dataStore.data.map { prefs -> AspectMode.decode(prefs[AspectModeKey]) }

    override val brightnessSwipe: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[BrightnessSwipeKey] ?: true }

    override val subtitleTextSize: Flow<SubtitleTextSize> =
        dataStore.data.map { prefs -> SubtitleTextSize.decode(prefs[SubtitleTextSizeKey]) }

    override val hardwareDecoding: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[HardwareDecodingKey] ?: true }

    override suspend fun setAspectMode(mode: AspectMode) {
        dataStore.edit { prefs -> prefs[AspectModeKey] = mode.encode() }
    }

    override suspend fun setBrightnessSwipe(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[BrightnessSwipeKey] = enabled }
    }

    override suspend fun setSubtitleTextSize(size: SubtitleTextSize) {
        dataStore.edit { prefs -> prefs[SubtitleTextSizeKey] = size.encode() }
    }

    override suspend fun setHardwareDecoding(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[HardwareDecodingKey] = enabled }
    }

    private companion object {
        val AspectModeKey = stringPreferencesKey("aspect_mode")
        val BrightnessSwipeKey = booleanPreferencesKey("brightness_swipe")
        val SubtitleTextSizeKey = stringPreferencesKey("subtitle_text_size")
        val HardwareDecodingKey = booleanPreferencesKey("hardware_decoding")
    }
}

/** Kept in memory only: the view model's test seam default. */
internal class InMemoryVideoPrefs(
    aspect: AspectMode = AspectMode.Default,
    brightness: Boolean = true,
    textSize: SubtitleTextSize = SubtitleTextSize.Default,
    hardware: Boolean = true,
) : VideoPrefs {
    override val aspectMode = MutableStateFlow(aspect)
    override val brightnessSwipe = MutableStateFlow(brightness)
    override val subtitleTextSize = MutableStateFlow(textSize)
    override val hardwareDecoding = MutableStateFlow(hardware)

    override suspend fun setAspectMode(mode: AspectMode) {
        aspectMode.value = mode
    }

    override suspend fun setBrightnessSwipe(enabled: Boolean) {
        brightnessSwipe.value = enabled
    }

    override suspend fun setSubtitleTextSize(size: SubtitleTextSize) {
        subtitleTextSize.value = size
    }

    override suspend fun setHardwareDecoding(enabled: Boolean) {
        hardwareDecoding.value = enabled
    }
}
