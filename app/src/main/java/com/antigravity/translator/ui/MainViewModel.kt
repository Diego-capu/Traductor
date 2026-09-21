package com.antigravity.translator.ui

import androidx.lifecycle.ViewModel
import com.antigravity.translator.TranslatorApplication
import com.antigravity.translator.data.pref.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MainUiState(
    val apiKey: String = "",
    val sourceLanguage: String = "",
    val targetLanguage: String = "ES",
    val isOverlayPermissionGranted: Boolean = false,
    val isNotificationPermissionGranted: Boolean = false,
    val isServiceRunning: Boolean = false,
    val isProAccount: Boolean = false,
    val isManualMode: Boolean = true
)

class MainViewModel(
    private val appPreferences: AppPreferences = TranslatorApplication.instance.appPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            apiKey = appPreferences.apiKey,
            sourceLanguage = appPreferences.sourceLanguage,
            targetLanguage = appPreferences.targetLanguage,
            isProAccount = appPreferences.isProAccount,
            isManualMode = appPreferences.isManualMode
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

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
        "RU" to "Ruso"
    )

    fun onApiKeyChanged(newKey: String) {
        appPreferences.apiKey = newKey
        _uiState.update {
            it.copy(
                apiKey = newKey,
                isProAccount = appPreferences.isProAccount
            )
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
