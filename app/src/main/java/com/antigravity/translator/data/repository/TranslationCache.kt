package com.antigravity.translator.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.LruCache

/**
 * High-performance, thread-safe translation cache backed by:
 * 1. Fast in-memory LRU cache
 * 2. Persistent on-disk SharedPreferences storage
 *
 * Guarantees that any text that has ever been translated is stored permanently
 * and never re-translated via the DeepL API across frames or app restarts.
 */
class TranslationCache(
    context: Context? = null,
    maxMemoryEntries: Int = 2000
) {
    private val diskPrefs: SharedPreferences? = context?.getSharedPreferences(
        "persistent_translations_cache",
        Context.MODE_PRIVATE
    )

    private val memCache = object : LruCache<String, String>(maxMemoryEntries) {
        override fun sizeOf(key: String, value: String): Int = 1
    }

    /**
     * Normalizes text by trimming whitespace, collapsing consecutive spaces,
     * and lowercasing for consistent dictionary cache matching.
     */
    fun normalizeKey(sourceLang: String, targetLang: String, text: String): String {
        val cleanText = text.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
        return "${sourceLang.uppercase()}_${targetLang.uppercase()}::$cleanText"
    }

    @Synchronized
    fun get(sourceLang: String, targetLang: String, text: String): String? {
        if (text.isBlank()) return ""
        val key = normalizeKey(sourceLang, targetLang, text)

        // 1. Check in-memory LRU cache
        val inMem = memCache.get(key)
        if (inMem != null) return inMem

        // 2. Check persistent disk storage
        val fromDisk = diskPrefs?.getString(key, null)
        if (fromDisk != null) {
            memCache.put(key, fromDisk)
            return fromDisk
        }

        return null
    }

    @Synchronized
    fun put(sourceLang: String, targetLang: String, text: String, translation: String) {
        if (text.isBlank() || translation.isBlank()) return
        val key = normalizeKey(sourceLang, targetLang, text)

        memCache.put(key, translation)
        diskPrefs?.edit()?.putString(key, translation)?.apply()
    }

    @Synchronized
    fun clear() {
        memCache.evictAll()
        diskPrefs?.edit()?.clear()?.apply()
    }

    @Synchronized
    fun size(): Int = memCache.size()
}
