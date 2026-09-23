package com.antigravity.translator.data.repository

import android.util.Log
import com.antigravity.translator.data.api.DeepLApiService
import com.antigravity.translator.data.api.DeepLTranslationRequest
import com.antigravity.translator.data.api.DeepLUsageResponse
import com.antigravity.translator.data.api.RetrofitClient
import com.antigravity.translator.data.model.TelemetryData
import com.antigravity.translator.data.pref.AppPreferences
import com.antigravity.translator.domain.model.DetectedTextBlock
import com.antigravity.translator.domain.model.TranslatedBlock
import com.antigravity.translator.telemetry.AppPerformanceTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Repository orchestrating OCR text translation with batching, in-memory LRU caching,
 * error handling, and real-time usage telemetry tracking.
 */
class DeepLRepository(
    private val appPreferences: AppPreferences,
    private val cache: TranslationCache
) {
    private val tag = "DeepLRepository"

    @Volatile
    private var cachedApiService: DeepLApiService? = null
    private var lastUsedProFlag: Boolean? = null

    private val _telemetryState = MutableStateFlow(
        TelemetryData(
            serverUsedCharacters = appPreferences.lastServerUsedChars,
            serverCharacterLimit = appPreferences.lastServerCharLimit,
            sessionCharactersSent = 0L,
            sessionCharactersSavedByCache = 0L,
            totalRequests = appPreferences.cumulativeTotalRequests,
            failedRequests = appPreferences.cumulativeFailedRequests
        )
    )
    val telemetryState: StateFlow<TelemetryData> = _telemetryState.asStateFlow()

    private fun getApiService(): DeepLApiService {
        val isPro = appPreferences.isProAccount
        if (cachedApiService == null || lastUsedProFlag != isPro) {
            cachedApiService = RetrofitClient.create(isPro)
            lastUsedProFlag = isPro
        }
        return cachedApiService!!
    }

    /**
     * Translates a list of detected text blocks in a single batched operation.
     * Hits LRU cache for already translated texts, and sends only cache misses
     * to DeepL API.
     */
    suspend fun translateBlocks(
        blocks: List<DetectedTextBlock>,
        sourceLang: String = appPreferences.sourceLanguage,
        targetLang: String = appPreferences.targetLanguage
    ): Result<List<TranslatedBlock>> = withContext(Dispatchers.IO) {
        if (blocks.isEmpty()) {
            return@withContext Result.success(emptyList())
        }

        AppPerformanceTracker.trace("deepl_batch_translation") { perfTrace ->
            perfTrace.putAttribute("source_lang", sourceLang.ifBlank { "AUTO" })
            perfTrace.putAttribute("target_lang", targetLang)
            perfTrace.putMetric("total_blocks", blocks.size.toLong())

            val apiKey = appPreferences.apiKey.trim()
            if (apiKey.isEmpty()) {
                return@trace Result.failure(IllegalStateException("DeepL API Key is not configured"))
            }

            val authHeader = "DeepL-Auth-Key $apiKey"
            val translatedResultsMap = mutableMapOf<String, String>()
            val uncachedUniqueTexts = mutableListOf<String>()
            var charactersSavedInThisCall = 0L

            // 1. Separate cache hits from misses
            for (block in blocks) {
                val cleanText = block.text.trim()
                if (cleanText.isEmpty()) {
                    translatedResultsMap[cleanText] = ""
                    continue
                }

                val cached = cache.get(sourceLang, targetLang, cleanText)
                if (cached != null) {
                    translatedResultsMap[cleanText] = cached
                    charactersSavedInThisCall += cleanText.length
                } else if (!uncachedUniqueTexts.contains(cleanText)) {
                    uncachedUniqueTexts.add(cleanText)
                }
            }

            perfTrace.putMetric("uncached_texts_count", uncachedUniqueTexts.size.toLong())
            perfTrace.putMetric("cache_hits_count", (blocks.size - uncachedUniqueTexts.size).toLong())

            // Record cache savings telemetry
            if (charactersSavedInThisCall > 0) {
                try {
                    appPreferences.cumulativeCharactersSaved += charactersSavedInThisCall
                    _telemetryState.update { current ->
                        current.copy(
                            sessionCharactersSavedByCache = current.sessionCharactersSavedByCache + charactersSavedInThisCall
                        )
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to update cache telemetry", e)
                }
            }

            // 2. Fetch uncached items in batch if any
            if (uncachedUniqueTexts.isNotEmpty()) {
                val charactersSentInThisCall = uncachedUniqueTexts.sumOf { it.length.toLong() }
                perfTrace.putMetric("characters_sent", charactersSentInThisCall)
                try {
                    appPreferences.cumulativeTotalRequests += 1
                    _telemetryState.update { it.copy(totalRequests = it.totalRequests + 1) }

                    val normalizedTargetLang = normalizeTargetLanguage(targetLang)
                    val supportsFormality = setOf("DE", "FR", "IT", "ES", "NL", "PL", "PT", "PT-BR", "PT-PT", "RU", "JA")
                    val profileFormality = appPreferences.readingProfile.defaultFormality
                    val formalityValue = if (supportsFormality.contains(normalizedTargetLang) && profileFormality != null) {
                        profileFormality
                    } else null

                    val request = DeepLTranslationRequest(
                        text = uncachedUniqueTexts,
                        targetLang = normalizedTargetLang,
                        sourceLang = if (sourceLang.isNotBlank()) sourceLang.trim().uppercase() else null,
                        formality = formalityValue
                    )

                    val response = getApiService().translateText(authHeader, request)

                    if (!response.isSuccessful) {
                        val errorCode = response.code()
                        val errorMessage = when (errorCode) {
                            403 -> "Invalid DeepL API Key (403 Forbidden)"
                            456 -> "DeepL API translation quota exceeded (456)"
                            429 -> "Too many requests. DeepL rate limit reached (429)"
                            else -> "DeepL API error ($errorCode): ${response.errorBody()?.string()}"
                        }
                        Log.e(tag, "DeepL Request Failed: $errorMessage")

                        // Track failed request telemetry
                        appPreferences.cumulativeFailedRequests += 1
                        _telemetryState.update { it.copy(failedRequests = it.failedRequests + 1) }

                        return@trace Result.failure(RuntimeException(errorMessage))
                    }

                    val body = response.body()
                    if (body != null && body.translations.size == uncachedUniqueTexts.size) {
                        for (i in uncachedUniqueTexts.indices) {
                            val originalText = uncachedUniqueTexts[i]
                            val translatedText = body.translations[i].text
                            cache.put(sourceLang, targetLang, originalText, translatedText)
                            translatedResultsMap[originalText] = translatedText
                        }

                        // Record successfully sent characters telemetry
                        appPreferences.cumulativeCharactersSent += charactersSentInThisCall
                        _telemetryState.update { current ->
                            current.copy(
                                sessionCharactersSent = current.sessionCharactersSent + charactersSentInThisCall,
                                serverUsedCharacters = current.serverUsedCharacters + charactersSentInThisCall
                            )
                        }
                    } else {
                        val msg = "DeepL returned mismatched translation item count"
                        Log.w(tag, msg)
                        appPreferences.cumulativeFailedRequests += 1
                        _telemetryState.update { it.copy(failedRequests = it.failedRequests + 1) }
                        return@trace Result.failure(IllegalStateException(msg))
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Exception during DeepL API batch translation", e)
                    try {
                        appPreferences.cumulativeFailedRequests += 1
                        _telemetryState.update { it.copy(failedRequests = it.failedRequests + 1) }
                    } catch (ignored: Exception) {}
                    return@trace Result.failure(e)
                }
            }

            // 3. Assemble final list of TranslatedBlock maintaining coordinate mapping
            val resultList = blocks.map { block ->
                val cleanText = block.text.trim()
                val translatedText = translatedResultsMap[cleanText] ?: block.text
                TranslatedBlock(
                    id = block.id,
                    originalText = block.text,
                    translatedText = translatedText,
                    boundingBox = block.boundingBox,
                    originalTextSizePx = block.originalTextSizePx
                )
            }

            Result.success(resultList)
        }
    }

    /**
     * Queries DeepL server quota (/v2/usage) and updates live telemetry.
     * Fails gracefully with Result.failure to preserve local cache and avoid interrupting capture pipeline.
     * On network error, timeout, or invalid response, preserves last known values and flags isOfflineQuota.
     */
    suspend fun fetchRemoteUsage(): Result<DeepLUsageResponse> = withContext(Dispatchers.IO) {
        AppPerformanceTracker.trace("deepl_fetch_usage") { perfTrace ->
            val apiKey = appPreferences.apiKey.trim()
            if (apiKey.isEmpty()) {
                applyOfflineTelemetryFallback()
                return@trace Result.failure(IllegalStateException("DeepL API Key no configurada"))
            }

            try {
                val authHeader = "DeepL-Auth-Key $apiKey"
                val response = getApiService().getUsage(authHeader)

                if (response.isSuccessful) {
                    val usage = response.body()
                    if (usage != null) {
                        appPreferences.lastServerUsedChars = usage.characterCount
                        appPreferences.lastServerCharLimit = usage.characterLimit

                        _telemetryState.update { current ->
                            current.copy(
                                serverUsedCharacters = usage.characterCount,
                                serverCharacterLimit = usage.characterLimit,
                                isOfflineQuota = false
                            )
                        }
                        perfTrace.putMetric("server_used_chars", usage.characterCount)
                        perfTrace.putMetric("server_char_limit", usage.characterLimit)
                        Log.d(tag, "DeepL Quota fetched: ${usage.characterCount} / ${usage.characterLimit}")
                        Result.success(usage)
                    } else {
                        applyOfflineTelemetryFallback()
                        Result.failure(IllegalStateException("Respuesta de uso vacía"))
                    }
                } else {
                    val msg = "Error al consultar cuota DeepL (${response.code()})"
                    Log.w(tag, msg)
                    applyOfflineTelemetryFallback()
                    Result.failure(RuntimeException(msg))
                }
            } catch (e: Exception) {
                Log.w(tag, "Fallo al consultar /v2/usage: ${e.message}", e)
                applyOfflineTelemetryFallback()
                Result.failure(e)
            }
        }
    }

    private fun applyOfflineTelemetryFallback() {
        _telemetryState.update { current ->
            val usedChars = if (current.serverUsedCharacters > 0L) {
                current.serverUsedCharacters
            } else {
                appPreferences.lastServerUsedChars
            }
            val charLimit = if (current.serverCharacterLimit > 0L) {
                current.serverCharacterLimit
            } else {
                appPreferences.lastServerCharLimit
            }
            current.copy(
                serverUsedCharacters = usedChars,
                serverCharacterLimit = charLimit,
                isOfflineQuota = true
            )
        }
    }

    fun normalizeTargetLanguage(lang: String): String = when (lang.trim().uppercase()) {
        "EN" -> "EN-US"
        "PT" -> "PT-PT"
        else -> lang.trim().uppercase()
    }
}
