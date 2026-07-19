package com.wearsic.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearsic.app.data.local.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    val backendUrl: StateFlow<String> = settingsDataStore.backendUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setBackendUrl(url: String) {
        viewModelScope.launch {
            settingsDataStore.setBackendUrl(url)
        }
    }
}
