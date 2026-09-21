package com.antigravity.translator.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Factory for creating configured Retrofit instances for DeepL.
 */
object RetrofitClient {

    private const val BASE_URL_FREE = "https://api-free.deepl.com/"
    private const val BASE_URL_PRO = "https://api.deepl.com/"

    /**
     * Interceptor to handle rate limiting (429) and network backoff.
     */
    private class RetryAndRateLimitInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            var response = chain.proceed(request)
            var tryCount = 0
            val maxLimit = 3

            while (!response.isSuccessful && response.code == 429 && tryCount < maxLimit) {
                tryCount++
                val retryAfterHeader = response.header("Retry-After")
                val delayMs = retryAfterHeader?.toLongOrNull()?.times(1000) ?: (tryCount * 1500L)
                response.close()
                try {
                    Thread.sleep(delayMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Interrupted during rate limit backoff", e)
                }
                response = chain.proceed(request)
            }
            return response
        }
    }

    fun create(isPro: Boolean = false): DeepLApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(RetryAndRateLimitInterceptor())
            .addInterceptor(logging)
            .build()

        val baseUrl = if (isPro) BASE_URL_PRO else BASE_URL_FREE

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeepLApiService::class.java)
    }
}
