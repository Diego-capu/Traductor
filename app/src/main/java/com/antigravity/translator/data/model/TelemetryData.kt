package com.antigravity.translator.data.model

/**
 * Telemetry model tracking DeepL server quota and local session statistics.
 */
data class TelemetryData(
    val serverUsedCharacters: Long = 0L,
    val serverCharacterLimit: Long = 0L,
    val sessionCharactersSent: Long = 0L,
    val sessionCharactersSavedByCache: Long = 0L,
    val totalRequests: Int = 0,
    val failedRequests: Int = 0,
    val isOfflineQuota: Boolean = false
)
