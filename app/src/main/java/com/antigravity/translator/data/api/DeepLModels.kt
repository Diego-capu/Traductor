package com.antigravity.translator.data.api

import com.google.gson.annotations.SerializedName

/**
 * DeepL API Translation Request payload.
 * Supports batching multiple text segments in one HTTP call.
 */
data class DeepLTranslationRequest(
    @SerializedName("text")
    val text: List<String>,

    @SerializedName("target_lang")
    val targetLang: String,

    @SerializedName("source_lang")
    val sourceLang: String? = null
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
