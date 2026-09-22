package com.antigravity.translator

import android.graphics.Rect
import com.antigravity.translator.domain.model.DetectedTextBlock
import com.antigravity.translator.domain.model.ReadingProfile
import com.antigravity.translator.engine.ocr.MangaBubbleClusterer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingProfileTest {

    private fun rect(l: Int, t: Int, r: Int, b: Int) = Rect().apply {
        left = l
        top = t
        right = r
        bottom = b
    }

    @Test
    fun testReadingProfileProperties() {
        // MANGA: Japanese, Vertical Tategaki RTL, No word spaces, Formality less
        assertEquals("JA", ReadingProfile.MANGA.defaultSourceLang)
        assertTrue(ReadingProfile.MANGA.isTategaki)
        assertFalse(ReadingProfile.MANGA.usesSpacedWords)
        assertEquals("less", ReadingProfile.MANGA.defaultFormality)

        // MANHWA: Korean, Horizontal LTR, Spaced words, Formality less
        assertEquals("KO", ReadingProfile.MANHWA.defaultSourceLang)
        assertFalse(ReadingProfile.MANHWA.isTategaki)
        assertTrue(ReadingProfile.MANHWA.usesSpacedWords)
        assertEquals("less", ReadingProfile.MANHWA.defaultFormality)

        // MANHUA: Chinese, Horizontal LTR, No word spaces, Formality null
        assertEquals("ZH", ReadingProfile.MANHUA.defaultSourceLang)
        assertFalse(ReadingProfile.MANHUA.isTategaki)
        assertFalse(ReadingProfile.MANHUA.usesSpacedWords)
        assertNull(ReadingProfile.MANHUA.defaultFormality)

        // COMIC: Occidental, Horizontal LTR, Spaced words, Formality null
        assertEquals("EN", ReadingProfile.COMIC.defaultSourceLang)
        assertFalse(ReadingProfile.COMIC.isTategaki)
        assertTrue(ReadingProfile.COMIC.usesSpacedWords)
        assertNull(ReadingProfile.COMIC.defaultFormality)
    }

    @Test
    fun testReadingProfileNextCycle() {
        assertEquals(ReadingProfile.MANHWA, ReadingProfile.MANGA.next())
        assertEquals(ReadingProfile.MANHUA, ReadingProfile.MANHWA.next())
        assertEquals(ReadingProfile.COMIC, ReadingProfile.MANHUA.next())
        assertEquals(ReadingProfile.MANGA, ReadingProfile.COMIC.next())
    }

    @Test
    fun testMangaProfileEnforcesTategakiRtlOrderingAndNoSpaces() {
        val col1Top = DetectedTextBlock(
            text = "お前は",
            boundingBox = rect(200, 100, 230, 150)
        )
        val col1Bottom = DetectedTextBlock(
            text = "もう",
            boundingBox = rect(198, 160, 228, 200)
        )
        val col2Top = DetectedTextBlock(
            text = "死んで",
            boundingBox = rect(150, 100, 180, 150)
        )
        val col2Bottom = DetectedTextBlock(
            text = "いる",
            boundingBox = rect(148, 160, 178, 200)
        )

        // Input passed with empty sourceLanguage but explicit MANGA profile
        val rawBlocks = listOf(col2Bottom, col1Top, col2Top, col1Bottom)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = rawBlocks,
            density = 1.0f,
            sourceLanguage = "",
            readingProfile = ReadingProfile.MANGA
        )

        assertEquals(1, clustered.size)
        // Expected Tategaki order with no spaces between kanji/kana
        assertEquals("お前はもう死んでいる", clustered[0].text)
    }

    @Test
    fun testManhwaProfileEnforcesHorizontalLtrOrderingAndSpaces() {
        val leftLine = DetectedTextBlock(
            text = "안녕",
            boundingBox = rect(100, 100, 180, 140)
        )
        val rightLine = DetectedTextBlock(
            text = "하세요",
            boundingBox = rect(190, 100, 270, 140)
        )

        val rawBlocks = listOf(rightLine, leftLine)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = rawBlocks,
            density = 1.0f,
            sourceLanguage = "",
            readingProfile = ReadingProfile.MANHWA
        )

        assertEquals(1, clustered.size)
        // Left-to-right order with space separation
        assertEquals("안녕 하세요", clustered[0].text)
    }

    @Test
    fun testManhuaProfileEnforcesHorizontalOrderWithoutSpaces() {
        val line1 = DetectedTextBlock(
            text = "你好",
            boundingBox = rect(100, 100, 200, 130)
        )
        val line2 = DetectedTextBlock(
            text = "世界",
            boundingBox = rect(100, 135, 200, 165)
        )

        val rawBlocks = listOf(line1, line2)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = rawBlocks,
            density = 1.0f,
            sourceLanguage = "",
            readingProfile = ReadingProfile.MANHUA
        )

        assertEquals(1, clustered.size)
        // Chinese joined without spaces
        assertEquals("你好世界", clustered[0].text)
    }

    @Test
    fun testComicProfileEnforcesWordHyphenationFix() {
        val line1 = DetectedTextBlock(
            text = "incredi-",
            boundingBox = rect(100, 100, 250, 130)
        )
        val line2 = DetectedTextBlock(
            text = "ble power",
            boundingBox = rect(100, 135, 250, 165)
        )

        val rawBlocks = listOf(line1, line2)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = rawBlocks,
            density = 1.0f,
            sourceLanguage = "",
            readingProfile = ReadingProfile.COMIC
        )

        assertEquals(1, clustered.size)
        // Hyphen unwrapped with word spacing
        assertEquals("incredible power", clustered[0].text)
    }

    @Test
    fun testMangaProfileWithEnglishScanlationPreservesHorizontalOrderAndSpaces() {
        val line1 = DetectedTextBlock(
            text = "I must find",
            boundingBox = rect(100, 100, 250, 130)
        )
        val line2 = DetectedTextBlock(
            text = "the truth!",
            boundingBox = rect(100, 135, 250, 165)
        )

        val rawBlocks = listOf(line1, line2)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = rawBlocks,
            density = 1.0f,
            sourceLanguage = "",
            readingProfile = ReadingProfile.MANGA
        )

        assertEquals(1, clustered.size)
        // English scanlation should NOT be treated as Tategaki, should have spaces
        assertEquals("I must find the truth!", clustered[0].text)
    }
}
