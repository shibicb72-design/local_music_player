package com.shibi.musicplayer.background

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shibi.musicplayer.domain.AppBackground
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BackgroundViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BackgroundRepository(application)

    val selection: StateFlow<AppBackground> = repository.selection.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppBackground.Default,
    )

    val customColors: StateFlow<Map<String, List<Long>>> = repository.customColors.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyMap(),
    )

    fun select(background: AppBackground) {
        viewModelScope.launch {
            repository.setBackground(background)
        }
    }

    fun setColors(sceneId: String, colors: List<Long>) {
        viewModelScope.launch {
            repository.setColors(sceneId, colors)
        }
    }

    fun resetColors(sceneId: String) {
        viewModelScope.launch {
            repository.resetColors(sceneId)
        }
    }
}
