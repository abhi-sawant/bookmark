package com.bookmark.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookmark.core.model.ThemeMode
import com.bookmark.core.model.UserPreferences
import com.bookmark.metadata.work.MetadataEnqueuer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val metadataEnqueuer: MetadataEnqueuer,
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
        // Spec 11 promises the app is *100% network-silent* with this off, not
        // merely that it stops scheduling new work -- anything already queued
        // would otherwise still run the next time there is a network.
        if (!enabled) metadataEnqueuer.cancelAllAutomatic()
    }
}
