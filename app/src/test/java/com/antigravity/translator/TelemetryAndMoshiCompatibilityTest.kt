package com.antigravity.translator

import com.antigravity.translator.data.api.DeepLTranslationRequest
import com.antigravity.translator.data.api.DeepLTranslationResponse
import com.antigravity.translator.data.api.DeepLTranslationResult
import com.antigravity.translator.data.api.DeepLUsageResponse
import com.antigravity.translator.data.model.TelemetryData
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryAndMoshiCompatibilityTest {

    @Test
    fun testTelemetryDataOfflineQuotaDefaultAndCopy() {
        val defaultTelemetry = TelemetryData()
        assertFalse(defaultTelemetry.isOfflineQuota)
        assertEquals(0L, defaultTelemetry.serverUsedCharacters)
        assertEquals(0L, defaultTelemetry.serverCharacterLimit)

        val offlineTelemetry = defaultTelemetry.copy(
            serverUsedCharacters = 12500L,
            serverCharacterLimit = 500000L,
            isOfflineQuota = true
        )
        assertTrue(offlineTelemetry.isOfflineQuota)
        assertEquals(12500L, offlineTelemetry.serverUsedCharacters)
        assertEquals(500000L, offlineTelemetry.serverCharacterLimit)
    }

    @Test
    fun testDeepLModelsMoshiSerializationCompatibility() {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val requestAdapter = moshi.adapter(DeepLTranslationRequest::class.java)
        val request = DeepLTranslationRequest(
            text = listOf("こんにちは", "世界"),
            targetLang = "ES",
            sourceLang = "JA",
            formality = "less"
        )
        val json = requestAdapter.toJson(request)
        assertNotNull(json)
        assertTrue(json.contains("\"formality\":\"less\""))
        assertTrue(json.contains("\"target_lang\":\"ES\""))

        val usageAdapter = moshi.adapter(DeepLUsageResponse::class.java)
        val usageJson = "{\"character_count\":1500,\"character_limit\":500000}"
        val usage = usageAdapter.fromJson(usageJson)
        assertNotNull(usage)
        assertEquals(1500L, usage?.characterCount)
        assertEquals(500000L, usage?.characterLimit)
    }

    @Test
    fun testDeepLModelsMoshiDeserializationCompatibility() {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val responseJson = "{\"translations\":[{\"detected_source_language\":\"EN\",\"text\":\"Hola\"}]}"
        val responseAdapter = moshi.adapter(DeepLTranslationResponse::class.java)
        val response = responseAdapter.fromJson(responseJson)
        assertNotNull(response)
        assertEquals(1, response?.translations?.size)
        assertEquals("Hola", response?.translations?.get(0)?.text)
        assertEquals("EN", response?.translations?.get(0)?.detectedSourceLanguage)
    }

    @Test
    fun testDeepLModelsGsonCompatibility() {
        val gson = com.google.gson.GsonBuilder()
            .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()

        val request = DeepLTranslationRequest(
            text = listOf("Hello", "World"),
            targetLang = "ES",
            sourceLang = "EN",
            formality = "default"
        )
        val json = gson.toJson(request)
        assertTrue(json.contains("\"target_lang\":\"ES\""))
        assertTrue(json.contains("\"source_lang\":\"EN\""))

        val responseJson = "{\"translations\":[{\"detected_source_language\":\"EN\",\"text\":\"Hola\"}]}"
        val response = gson.fromJson(responseJson, DeepLTranslationResponse::class.java)
        assertNotNull(response)
        assertEquals(1, response.translations.size)
        assertEquals("Hola", response.translations[0].text)
        assertEquals("EN", response.translations[0].detectedSourceLanguage)

        val usageJson = "{\"character_count\":2500,\"character_limit\":500000}"
        val usage = gson.fromJson(usageJson, DeepLUsageResponse::class.java)
        assertNotNull(usage)
        assertEquals(2500L, usage.characterCount)
        assertEquals(500000L, usage.characterLimit)
    }

    @Test
    fun testDeepLTargetLanguageNormalization() {
        val normalize = { lang: String ->
            when (lang.trim().uppercase()) {
                "EN" -> "EN-US"
                "PT" -> "PT-PT"
                else -> lang.trim().uppercase()
            }
        }

        assertEquals("EN-US", normalize("EN"))
        assertEquals("EN-US", normalize("en"))
        assertEquals("PT-PT", normalize("PT"))
        assertEquals("PT-PT", normalize("pt"))
        assertEquals("ES", normalize("ES"))
        assertEquals("JA", normalize("JA"))
        assertEquals("DE", normalize("DE"))
    }
}
