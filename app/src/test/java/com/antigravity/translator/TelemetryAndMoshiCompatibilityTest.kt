package com.antigravity.translator

import com.antigravity.translator.data.api.DeepLTranslationRequest
import com.antigravity.translator.data.api.DeepLTranslationResponse
import com.antigravity.translator.data.api.DeepLTranslationResult
import com.antigravity.translator.data.api.DeepLUsageResponse
import com.antigravity.translator.data.model.TelemetryData
import com.google.gson.Gson
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
    fun testDeepLModelsGsonCompatibility() {
        val gson = Gson()
        val request = DeepLTranslationRequest(
            text = listOf("Hello"),
            targetLang = "ES",
            formality = "less"
        )
        val json = gson.toJson(request)
        assertTrue(json.contains("\"formality\":\"less\""))

        val responseJson = "{\"translations\":[{\"detected_source_language\":\"EN\",\"text\":\"Hola\"}]}"
        val response = gson.fromJson(responseJson, DeepLTranslationResponse::class.java)
        assertEquals(1, response.translations.size)
        assertEquals("Hola", response.translations[0].text)
        assertEquals("EN", response.translations[0].detectedSourceLanguage)
    }
}
