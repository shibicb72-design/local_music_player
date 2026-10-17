package com.shibi.musicplayer.background

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shibi.musicplayer.domain.AppBackground
import com.shibi.musicplayer.domain.BackgroundCatalog
import com.shibi.musicplayer.domain.BackgroundPersistence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.backgroundDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "backgrounds",
)

class BackgroundRepository(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.backgroundDataStore)

    val selection: Flow<AppBackground> = dataStore.data.map { prefs ->
        BackgroundPersistence.decode(prefs[BackgroundIdKey])
    }

    val customColors: Flow<Map<String, List<Long>>> = dataStore.data.map { prefs ->
        buildMap {
            BackgroundCatalog.scenes.forEach { scene ->
                val raw = prefs[colorsKey(scene.id)] ?: return@forEach
                put(scene.id, BackgroundPersistence.decodeColors(raw, scene.defaults))
            }
        }
    }

    suspend fun setBackground(background: AppBackground) {
        dataStore.edit { prefs ->
            prefs[BackgroundIdKey] = BackgroundPersistence.encode(background)
        }
    }

    suspend fun setColors(sceneId: String, colors: List<Long>) {
        val scene = BackgroundCatalog.scene(sceneId) ?: return
        val resolved = scene.defaults.indices.map { index ->
            colors.getOrElse(index) { scene.defaults[index] }
        }
        dataStore.edit { prefs ->
            prefs[colorsKey(sceneId)] = BackgroundPersistence.encodeColors(resolved)
        }
    }

    suspend fun resetColors(sceneId: String) {
        dataStore.edit { prefs ->
            prefs.remove(colorsKey(sceneId))
        }
    }

    private companion object {
        val BackgroundIdKey = stringPreferencesKey(BackgroundPersistence.KEY_BACKGROUND)

        fun colorsKey(sceneId: String) =
            stringPreferencesKey(BackgroundPersistence.colorsKey(sceneId))
    }
}
