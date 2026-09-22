package com.antigravity.translator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.translator.TranslatorApplication
import com.antigravity.translator.data.model.TelemetryData
import com.antigravity.translator.data.pref.AppPreferences
import com.antigravity.translator.data.repository.DeepLRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.antigravity.translator.domain.model.ReadingProfile

data class MainUiState(
    val apiKey: String = "",
    val sourceLanguage: String = "",
    val targetLanguage: String = "ES",
    val readingProfile: ReadingProfile = ReadingProfile.MANGA,
    val isOverlayPermissionGranted: Boolean = false,
    val isNotificationPermissionGranted: Boolean = false,
    val isServiceRunning: Boolean = false,
    val isProAccount: Boolean = false,
    val isManualMode: Boolean = true
)

class MainViewModel(
    private val appPreferences: AppPreferences = TranslatorApplication.instance.appPreferences,
    private val deepLRepository: DeepLRepository = TranslatorApplication.instance.deepLRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            apiKey = appPreferences.apiKey,
            sourceLanguage = appPreferences.sourceLanguage,
            targetLanguage = appPreferences.targetLanguage,
            readingProfile = appPreferences.readingProfile,
            isProAccount = appPreferences.isProAccount,
            isManualMode = appPreferences.isManualMode
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Live telemetry state from repository
    val telemetryState: StateFlow<TelemetryData> = deepLRepository.telemetryState

    private val _isRefreshingUsage = MutableStateFlow(false)
    val isRefreshingUsage: StateFlow<Boolean> = _isRefreshingUsage.asStateFlow()

    init {
        // Query initial quota on startup if an API key is present
        if (appPreferences.apiKey.isNotBlank()) {
            refreshUsage()
        }
    }

    val availableLanguages = listOf(
        "" to "Detectar automáticamente",
        "EN" to "Inglés",
        "ES" to "Español",
        "FR" to "Francés",
        "DE" to "Alemán",
        "IT" to "Italiano",
        "PT" to "Portugués",
        "JA" to "Japonés",
        "ZH" to "Chino",
        "KO" to "Coreano",
        "RU" to "Ruso"
    )

    fun refreshUsage() {
        viewModelScope.launch {
            _isRefreshingUsage.value = true
            try {
                deepLRepository.fetchRemoteUsage()
            } finally {
                _isRefreshingUsage.value = false
            }
        }
    }

    fun onApiKeyChanged(newKey: String) {
        appPreferences.apiKey = newKey
        _uiState.update {
            it.copy(
                apiKey = newKey,
                isProAccount = appPreferences.isProAccount
            )
        }
        if (newKey.isNotBlank()) {
            refreshUsage()
        }
    }

    fun onReadingProfileChanged(profile: ReadingProfile) {
        appPreferences.readingProfile = profile
        _uiState.update {
            it.copy(readingProfile = profile)
        }
    }

    fun onSourceLanguageSelected(code: String) {
        appPreferences.sourceLanguage = code
        _uiState.update { it.copy(sourceLanguage = code) }
    }

    fun onTargetLanguageSelected(code: String) {
        appPreferences.targetLanguage = code
        _uiState.update { it.copy(targetLanguage = code) }
    }

    fun onModeToggled(isManual: Boolean) {
        appPreferences.isManualMode = isManual
        _uiState.update { it.copy(isManualMode = isManual) }
    }

    fun setOverlayPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(isOverlayPermissionGranted = granted) }
    }

    fun setNotificationPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(isNotificationPermissionGranted = granted) }
    }

    fun setServiceRunning(running: Boolean) {
        _uiState.update { it.copy(isServiceRunning = running) }
    }
}
