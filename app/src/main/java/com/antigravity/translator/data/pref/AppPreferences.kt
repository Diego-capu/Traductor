package com.antigravity.translator.data.pref

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Handles persistent encrypted storage for API keys and user preferences.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "EncryptedSharedPreferences failed, falling back to standard prefs", e)
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var sourceLanguage: String
        get() = prefs.getString(KEY_SOURCE_LANG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SOURCE_LANG, value.trim().uppercase()).apply()

    var targetLanguage: String
        get() = prefs.getString(KEY_TARGET_LANG, "ES") ?: "ES"
        set(value) = prefs.edit().putString(KEY_TARGET_LANG, value.trim().uppercase()).apply()

    var isProAccount: Boolean
        get() {
            val key = apiKey
            // DeepL Free keys typically end with ":fx"
            return if (key.isNotEmpty()) {
                !key.endsWith(":fx")
            } else {
                prefs.getBoolean(KEY_IS_PRO, false)
            }
        }
        set(value) = prefs.edit().putBoolean(KEY_IS_PRO, value).apply()

    var captureIntervalMs: Long
        get() = prefs.getLong(KEY_CAPTURE_INTERVAL_MS, 1000L) // Default 1 FPS
        set(value) = prefs.edit().putLong(KEY_CAPTURE_INTERVAL_MS, value).apply()

    var showBoundingBoxes: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BOUNDING_BOXES, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_BOUNDING_BOXES, value).apply()

    var isManualMode: Boolean
        get() = prefs.getBoolean(KEY_IS_MANUAL_MODE, true) // Default: Manual / On-Demand
        set(value) = prefs.edit().putBoolean(KEY_IS_MANUAL_MODE, value).apply()

    companion object {
        private const val TAG = "AppPreferences"
        private const val PREFS_FILENAME = "translator_secure_prefs"
        private const val DEFAULT_API_KEY = "f28b396c-fa20-4b60-a87e-a9c82bc26e2c:fx"
        private const val KEY_API_KEY = "key_deepl_api_key"
        private const val KEY_SOURCE_LANG = "key_source_lang"
        private const val KEY_TARGET_LANG = "key_target_lang"
        private const val KEY_IS_PRO = "key_is_pro"
        private const val KEY_CAPTURE_INTERVAL_MS = "key_capture_interval_ms"
        private const val KEY_SHOW_BOUNDING_BOXES = "key_show_bounding_boxes"
        private const val KEY_IS_MANUAL_MODE = "key_is_manual_mode"
    }
}
