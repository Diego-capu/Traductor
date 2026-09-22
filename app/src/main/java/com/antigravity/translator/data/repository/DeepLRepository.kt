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

        val apiKey = appPreferences.apiKey.trim()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("DeepL API Key is not configured"))
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
            try {
                appPreferences.cumulativeTotalRequests += 1
                _telemetryState.update { it.copy(totalRequests = it.totalRequests + 1) }

                val supportsFormality = setOf("DE", "FR", "IT", "ES", "NL", "PL", "PT", "PT-BR", "PT-PT", "RU", "JA")
                val formalityValue = if (supportsFormality.contains(targetLang.uppercase())) "less" else null

                val request = DeepLTranslationRequest(
                    text = uncachedUniqueTexts,
                    targetLang = targetLang,
                    sourceLang = if (sourceLang.isNotBlank()) sourceLang else null,
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

                    return@withContext Result.failure(RuntimeException(errorMessage))
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
                    return@withContext Result.failure(IllegalStateException(msg))
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception during DeepL API batch translation", e)
                try {
                    appPreferences.cumulativeFailedRequests += 1
                    _telemetryState.update { it.copy(failedRequests = it.failedRequests + 1) }
                } catch (ignored: Exception) {}
                return@withContext Result.failure(e)
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
                boundingBox = block.boundingBox
            )
        }

        Result.success(resultList)
    }

    /**
     * Queries DeepL server quota (/v2/usage) and updates live telemetry.
     * Fails gracefully with Result.failure to preserve local cache and avoid interrupting capture pipeline.
     */
    suspend fun fetchRemoteUsage(): Result<DeepLUsageResponse> = withContext(Dispatchers.IO) {
        val apiKey = appPreferences.apiKey.trim()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("DeepL API Key no configurada"))
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
                            serverCharacterLimit = usage.characterLimit
                        )
                    }
                    Log.d(tag, "DeepL Quota fetched: ${usage.characterCount} / ${usage.characterLimit}")
                    Result.success(usage)
                } else {
                    Result.failure(IllegalStateException("Respuesta de uso vacía"))
                }
            } else {
                val msg = "Error al consultar cuota DeepL (${response.code()})"
                Log.w(tag, msg)
                Result.failure(RuntimeException(msg))
            }
        } catch (e: Exception) {
            Log.w(tag, "Fallo al consultar /v2/usage: ${e.message}", e)
            Result.failure(e)
        }
    }
}
