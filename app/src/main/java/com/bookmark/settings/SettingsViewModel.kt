package com.bookmark.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookmark.core.model.ThemeMode
import com.bookmark.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = settingsRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsRepository.setThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDynamicColor(enabled)
    }

    fun setTrueBlack(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setTrueBlack(enabled)
    }

    fun setFetchPreviews(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setFetchPreviews(enabled)
    }
}
