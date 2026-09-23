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
        // MANGA_JA: Japanese, Vertical Tategaki RTL, No word spaces, Formality less
        assertEquals("JA", ReadingProfile.MANGA_JA.defaultSourceLang)
        assertTrue(ReadingProfile.MANGA_JA.isTategaki)
        assertFalse(ReadingProfile.MANGA_JA.usesSpacedWords)
        assertEquals("less", ReadingProfile.MANGA_JA.defaultFormality)

        // MANGA_EN: English scanlation, Horizontal LTR, Spaced words, Formality less
        assertEquals("EN", ReadingProfile.MANGA_EN.defaultSourceLang)
        assertFalse(ReadingProfile.MANGA_EN.isTategaki)
        assertTrue(ReadingProfile.MANGA_EN.usesSpacedWords)
        assertEquals("less", ReadingProfile.MANGA_EN.defaultFormality)

        // MANHWA_KO: Korean, Horizontal LTR, Spaced words, Formality less
        assertEquals("KO", ReadingProfile.MANHWA_KO.defaultSourceLang)
        assertFalse(ReadingProfile.MANHWA_KO.isTategaki)
        assertTrue(ReadingProfile.MANHWA_KO.usesSpacedWords)
        assertEquals("less", ReadingProfile.MANHWA_KO.defaultFormality)

        // MANHWA_EN: English manhwa scanlation, Horizontal LTR, Spaced words, Formality less
        assertEquals("EN", ReadingProfile.MANHWA_EN.defaultSourceLang)
        assertFalse(ReadingProfile.MANHWA_EN.isTategaki)
        assertTrue(ReadingProfile.MANHWA_EN.usesSpacedWords)
        assertEquals("less", ReadingProfile.MANHWA_EN.defaultFormality)

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
        assertEquals(ReadingProfile.MANGA_EN, ReadingProfile.MANGA_JA.next())
        assertEquals(ReadingProfile.MANHWA_KO, ReadingProfile.MANGA_EN.next())
        assertEquals(ReadingProfile.MANHWA_EN, ReadingProfile.MANHWA_KO.next())
        assertEquals(ReadingProfile.MANHUA, ReadingProfile.MANHWA_EN.next())
        assertEquals(ReadingProfile.COMIC, ReadingProfile.MANHUA.next())
        assertEquals(ReadingProfile.MANGA_JA, ReadingProfile.COMIC.next())
    }

    @Test
    fun testMangaJaProfileEnforcesTategakiRtlOrderingAndNoSpaces() {
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

        val rawBlocks = listOf(col2Bottom, col1Top, col2Top, col1Bottom)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = rawBlocks,
            density = 1.0f,
            sourceLanguage = "",
            readingProfile = ReadingProfile.MANGA_JA
        )

        assertEquals(1, clustered.size)
        // Expected Tategaki order with no spaces between kanji/kana
        assertEquals("お前はもう死んでいる", clustered[0].text)
    }

    @Test
    fun testMangaEnProfileEnforcesHorizontalOrderAndSpaces() {
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
            readingProfile = ReadingProfile.MANGA_EN
        )

        assertEquals(1, clustered.size)
        // English scanlation is horizontal LTR with natural spaces
        assertEquals("I must find the truth!", clustered[0].text)
    }

    @Test
    fun testMangaEnProfileFixesHyphenation() {
        val line1 = DetectedTextBlock(
            text = "cow-",
            boundingBox = rect(100, 100, 200, 130)
        )
        val line2 = DetectedTextBlock(
            text = "ardly",
            boundingBox = rect(100, 135, 200, 165)
        )

        val rawBlocks = listOf(line1, line2)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = rawBlocks,
            density = 1.0f,
            sourceLanguage = "EN",
            readingProfile = ReadingProfile.MANGA_EN
        )

        assertEquals(1, clustered.size)
        assertEquals("cowardly", clustered[0].text)
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
            readingProfile = ReadingProfile.MANHWA_KO
        )

        assertEquals(1, clustered.size)
        // Left-to-right order with space separation
        assertEquals("안녕 하세요", clustered[0].text)
    }

    @Test
    fun testManhwaEnProfileEnforcesHorizontalLtrOrderingAndSpaces() {
        val leftLine = DetectedTextBlock(
            text = "Solo",
            boundingBox = rect(100, 100, 180, 140)
        )
        val rightLine = DetectedTextBlock(
            text = "Leveling",
            boundingBox = rect(190, 100, 290, 140)
        )

        val rawBlocks = listOf(rightLine, leftLine)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = rawBlocks,
            density = 1.0f,
            sourceLanguage = "EN",
            readingProfile = ReadingProfile.MANHWA_EN
        )

        assertEquals(1, clustered.size)
        assertEquals("Solo Leveling", clustered[0].text)
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
}
