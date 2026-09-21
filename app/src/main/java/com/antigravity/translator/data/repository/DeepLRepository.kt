package com.antigravity.translator.data.repository

import android.util.Log
import com.antigravity.translator.data.api.DeepLApiService
import com.antigravity.translator.data.api.DeepLTranslationRequest
import com.antigravity.translator.data.api.RetrofitClient
import com.antigravity.translator.data.pref.AppPreferences
import com.antigravity.translator.domain.model.DetectedTextBlock
import com.antigravity.translator.domain.model.TranslatedBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository orchestrating OCR text translation with batching, in-memory LRU caching,
 * and error handling.
 */
class DeepLRepository(
    private val appPreferences: AppPreferences,
    private val cache: TranslationCache
) {
    private val tag = "DeepLRepository"

    @Volatile
    private var cachedApiService: DeepLApiService? = null
    private var lastUsedProFlag: Boolean? = null

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
            } else if (!uncachedUniqueTexts.contains(cleanText)) {
                uncachedUniqueTexts.add(cleanText)
            }
        }

        // 2. Fetch uncached items in batch if any
        if (uncachedUniqueTexts.isNotEmpty()) {
            try {
                val request = DeepLTranslationRequest(
                    text = uncachedUniqueTexts,
                    targetLang = targetLang,
                    sourceLang = if (sourceLang.isNotBlank()) sourceLang else null
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
                } else {
                    val msg = "DeepL returned mismatched translation item count"
                    Log.w(tag, msg)
                    return@withContext Result.failure(IllegalStateException(msg))
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception during DeepL API batch translation", e)
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
}
