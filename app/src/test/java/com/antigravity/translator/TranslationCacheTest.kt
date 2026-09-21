package com.antigravity.translator

import com.antigravity.translator.data.repository.TranslationCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TranslationCacheTest {

    private lateinit var cache: TranslationCache

    @Before
    fun setUp() {
        cache = TranslationCache(maxMemoryEntries = 3)
    }

    @Test
    fun testCachePutAndGet() {
        cache.put("EN", "ES", "Hello world", "Hola mundo")
        val result = cache.get("EN", "ES", "Hello world")
        assertEquals("Hola mundo", result)
    }

    @Test
    fun testCaseInsensitiveLanguages() {
        cache.put("en", "es", "Hello", "Hola")
        val result = cache.get("EN", "ES", "Hello")
        assertEquals("Hola", result)
    }

    @Test
    fun testLruEviction() {
        cache.put("EN", "ES", "one", "uno")
        cache.put("EN", "ES", "two", "dos")
        cache.put("EN", "ES", "three", "tres")

        // Access 'one' to make it recently used
        cache.get("EN", "ES", "one")

        // Add fourth entry, should evict 'two' (least recently used)
        cache.put("EN", "ES", "four", "cuatro")

        assertEquals("uno", cache.get("EN", "ES", "one"))
        assertEquals("tres", cache.get("EN", "ES", "three"))
        assertEquals("cuatro", cache.get("EN", "ES", "four"))
        assertNull(cache.get("EN", "ES", "two"))
    }

    @Test
    fun testEmptyOrBlankStringIgnored() {
        cache.put("EN", "ES", "", "")
        assertEquals(0, cache.size())
    }
}
