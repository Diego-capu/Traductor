package com.antigravity.translator.data.api

import com.google.gson.annotations.SerializedName
import com.squareup.moshi.Json

/**
 * DeepL API Translation Request payload.
 * Supports batching multiple text segments in one HTTP call.
 */
data class DeepLTranslationRequest(
    @field:Json(name = "text")
    @SerializedName("text")
    val text: List<String>,

    @field:Json(name = "target_lang")
    @SerializedName("target_lang")
    val targetLang: String,

    @field:Json(name = "source_lang")
    @SerializedName("source_lang")
    val sourceLang: String? = null,

    @field:Json(name = "formality")
    @SerializedName("formality")
    val formality: String? = null
)

/**
 * DeepL API Translation Response payload.
 */
data class DeepLTranslationResponse(
    @SerializedName("translations")
    val translations: List<DeepLTranslationResult>
)

data class DeepLTranslationResult(
    @SerializedName("detected_source_language")
    val detectedSourceLanguage: String?,

    @SerializedName("text")
    val text: String
)

/**
 * DeepL API Usage Telemetry Response (/v2/usage).
 */
data class DeepLUsageResponse(
    @field:Json(name = "character_count")
    @SerializedName("character_count")
    val characterCount: Long = 0L,

    @field:Json(name = "character_limit")
    @SerializedName("character_limit")
    val characterLimit: Long = 0L
)
