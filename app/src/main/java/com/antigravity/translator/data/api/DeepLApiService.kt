package com.antigravity.translator.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit definition for DeepL Translation API.
 */
interface DeepLApiService {

    @POST("v2/translate")
    suspend fun translateText(
        @Header("Authorization") authHeader: String,
        @Body request: DeepLTranslationRequest
    ): Response<DeepLTranslationResponse>
}
