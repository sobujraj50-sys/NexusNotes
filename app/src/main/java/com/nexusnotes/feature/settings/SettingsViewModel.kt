package com.nexusnotes.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nexusnotes.appContainer
import com.nexusnotes.data.repository.SettingsRepository
import com.nexusnotes.domain.model.AppSettings
import com.nexusnotes.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    attachments: com.nexusnotes.data.repository.AttachmentRepository,
) : ViewModel() {

    val attachmentBytes: StateFlow<Long> =
        attachments.observeTotalBytes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)


    val uiState: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    fun setAiToolsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setAiToolsEnabled(enabled) }
    }

    fun setSaveSearchHistory(enabled: Boolean) {
        viewModelScope.launch { settings.setSaveSearchHistory(enabled) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer()
                SettingsViewModel(c.settingsRepository, c.attachmentRepository)
            }
        }
    }
}
