package com.antigravity.translator.data.pref

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

import com.antigravity.translator.domain.model.ReadingProfile

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

    var readingProfile: ReadingProfile
        get() {
            val name = prefs.getString(KEY_READING_PROFILE, ReadingProfile.MANGA.name) ?: ReadingProfile.MANGA.name
            return try {
                ReadingProfile.valueOf(name)
            } catch (e: Exception) {
                ReadingProfile.MANGA
            }
        }
        set(value) = prefs.edit().putString(KEY_READING_PROFILE, value.name).apply()

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

    var lastServerUsedChars: Long
        get() = prefs.getLong(KEY_LAST_SERVER_USED_CHARS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SERVER_USED_CHARS, value).apply()

    var lastServerCharLimit: Long
        get() = prefs.getLong(KEY_LAST_SERVER_CHAR_LIMIT, 500000L) // Default 500k for Free tier
        set(value) = prefs.edit().putLong(KEY_LAST_SERVER_CHAR_LIMIT, value).apply()

    var cumulativeCharactersSent: Long
        get() = prefs.getLong(KEY_CUMULATIVE_CHARS_SENT, 0L)
        set(value) = prefs.edit().putLong(KEY_CUMULATIVE_CHARS_SENT, value).apply()

    var cumulativeCharactersSaved: Long
        get() = prefs.getLong(KEY_CUMULATIVE_CHARS_SAVED, 0L)
        set(value) = prefs.edit().putLong(KEY_CUMULATIVE_CHARS_SAVED, value).apply()

    var cumulativeTotalRequests: Int
        get() = prefs.getInt(KEY_CUMULATIVE_TOTAL_REQUESTS, 0)
        set(value) = prefs.edit().putInt(KEY_CUMULATIVE_TOTAL_REQUESTS, value).apply()

    var cumulativeFailedRequests: Int
        get() = prefs.getInt(KEY_CUMULATIVE_FAILED_REQUESTS, 0)
        set(value) = prefs.edit().putInt(KEY_CUMULATIVE_FAILED_REQUESTS, value).apply()

    var cropLeft: Int
        get() = prefs.getInt(KEY_CROP_LEFT, -1)
        set(value) = prefs.edit().putInt(KEY_CROP_LEFT, value).apply()

    var cropTop: Int
        get() = prefs.getInt(KEY_CROP_TOP, -1)
        set(value) = prefs.edit().putInt(KEY_CROP_TOP, value).apply()

    var cropRight: Int
        get() = prefs.getInt(KEY_CROP_RIGHT, -1)
        set(value) = prefs.edit().putInt(KEY_CROP_RIGHT, value).apply()

    var cropBottom: Int
        get() = prefs.getInt(KEY_CROP_BOTTOM, -1)
        set(value) = prefs.edit().putInt(KEY_CROP_BOTTOM, value).apply()

    var isCropEnabled: Boolean
        get() = prefs.getBoolean(KEY_IS_CROP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_CROP_ENABLED, value).apply()

    fun getSavedCropRegion(): Rect? {
        if (!isCropEnabled) return null
        val left = cropLeft
        val top = cropTop
        val right = cropRight
        val bottom = cropBottom
        return if (left >= 0 && top >= 0 && right > left && bottom > top) {
            Rect(left, top, right, bottom)
        } else {
            null
        }
    }

    fun getLastCropOrNull(): Rect? {
        val left = cropLeft
        val top = cropTop
        val right = cropRight
        val bottom = cropBottom
        return if (left >= 0 && top >= 0 && right > left && bottom > top) {
            Rect(left, top, right, bottom)
        } else {
            null
        }
    }

    fun saveCropRegion(rect: Rect?) {
        if (rect != null && rect.width() > 0 && rect.height() > 0) {
            cropLeft = rect.left
            cropTop = rect.top
            cropRight = rect.right
            cropBottom = rect.bottom
            isCropEnabled = true
        } else {
            isCropEnabled = false
        }
    }

    companion object {
        private const val TAG = "AppPreferences"
        private const val PREFS_FILENAME = "translator_secure_prefs"
        private const val DEFAULT_API_KEY = "f28b396c-fa20-4b60-a87e-a9c82bc26e2c:fx"
        private const val KEY_READING_PROFILE = "key_reading_profile"
        private const val KEY_API_KEY = "key_deepl_api_key"
        private const val KEY_SOURCE_LANG = "key_source_lang"
        private const val KEY_TARGET_LANG = "key_target_lang"
        private const val KEY_IS_PRO = "key_is_pro"
        private const val KEY_CAPTURE_INTERVAL_MS = "key_capture_interval_ms"
        private const val KEY_SHOW_BOUNDING_BOXES = "key_show_bounding_boxes"
        private const val KEY_IS_MANUAL_MODE = "key_is_manual_mode"
        private const val KEY_LAST_SERVER_USED_CHARS = "key_last_server_used_chars"
        private const val KEY_LAST_SERVER_CHAR_LIMIT = "key_last_server_char_limit"
        private const val KEY_CUMULATIVE_CHARS_SENT = "key_cumulative_chars_sent"
        private const val KEY_CUMULATIVE_CHARS_SAVED = "key_cumulative_chars_saved"
        private const val KEY_CUMULATIVE_TOTAL_REQUESTS = "key_cumulative_total_requests"
        private const val KEY_CUMULATIVE_FAILED_REQUESTS = "key_cumulative_failed_requests"
        private const val KEY_CROP_LEFT = "key_crop_left"
        private const val KEY_CROP_TOP = "key_crop_top"
        private const val KEY_CROP_RIGHT = "key_crop_right"
        private const val KEY_CROP_BOTTOM = "key_crop_bottom"
        private const val KEY_IS_CROP_ENABLED = "key_is_crop_enabled"
    }
}
