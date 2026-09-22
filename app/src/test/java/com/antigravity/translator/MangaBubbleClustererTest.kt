package com.antigravity.translator

import android.graphics.Rect
import com.antigravity.translator.domain.model.DetectedTextBlock
import com.antigravity.translator.engine.ocr.MangaBubbleClusterer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaBubbleClustererTest {

    private fun rect(l: Int, t: Int, r: Int, b: Int) = Rect().apply {
        left = l
        top = t
        right = r
        bottom = b
    }

    @Test
    fun testClusterLinesInSameBubbleAndFixHyphenation() {
        val line1 = DetectedTextBlock(
            text = "it'd be cow-",
            boundingBox = rect(100, 100, 250, 130)
        )
        val line2 = DetectedTextBlock(
            text = "ardly of me",
            boundingBox = rect(100, 135, 250, 165)
        )
        val line3 = DetectedTextBlock(
            text = "to flee?",
            boundingBox = rect(100, 170, 250, 200)
        )

        // Separate bubble in another panel far away
        val distantBubble = DetectedTextBlock(
            text = "HUH?",
            boundingBox = rect(500, 500, 600, 550)
        )

        val rawBlocks = listOf(line1, line2, line3, distantBubble)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(rawBlocks, density = 1.0f, sourceLanguage = "EN")

        // Should result in exactly 2 clusters
        assertEquals(2, clustered.size)

        val firstBubble = clustered.first { it.boundingBox.left <= 100 && 100 <= it.boundingBox.right && it.boundingBox.top <= 100 && 100 <= it.boundingBox.bottom }
        assertEquals("it'd be cowardly of me to flee?", firstBubble.text)

        val secondBubble = clustered.first { it.boundingBox.left <= 500 && 500 <= it.boundingBox.right && it.boundingBox.top <= 500 && 500 <= it.boundingBox.bottom }
        assertEquals("HUH?", secondBubble.text)
    }

    @Test
    fun testTategakiRightToLeftColumnOrderingForJapanese() {
        // Right vertical column (Col 1: top to bottom)
        val col1Top = DetectedTextBlock(
            text = "お前は",
            boundingBox = rect(200, 100, 230, 150)
        )
        val col1Bottom = DetectedTextBlock(
            text = "もう",
            boundingBox = rect(198, 160, 228, 200)
        )

        // Left vertical column (Col 2: top to bottom)
        val col2Top = DetectedTextBlock(
            text = "死んで",
            boundingBox = rect(150, 100, 180, 150)
        )
        val col2Bottom = DetectedTextBlock(
            text = "いる",
            boundingBox = rect(148, 160, 178, 200)
        )

        val rawBlocks = listOf(col2Bottom, col1Top, col2Top, col1Bottom) // Input in arbitrary order
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(rawBlocks, density = 1.0f, sourceLanguage = "JA")

        assertEquals(1, clustered.size)
        // Expected Tategaki order: Col 1 top ("お前は"), Col 1 bottom ("もう"), Col 2 top ("死んで"), Col 2 bottom ("いる")
        // And Japanese text joined without spaces: "お前はもう死んでいる"
        assertEquals("お前はもう死んでいる", clustered[0].text)
    }

    @Test
    fun testFuriganaFilteringDiscardsSmallRubyCharacters() {
        // Main kanji line (height = 50px)
        val mainLine1 = DetectedTextBlock(
            text = "私",
            boundingBox = rect(100, 100, 140, 150)
        )
        // Main kanji line 2 (height = 50px)
        val mainLine2 = DetectedTextBlock(
            text = "の名は",
            boundingBox = rect(100, 160, 140, 210)
        )
        // Tiny Furigana annotation next to kanji (height = 12px < 40% of ~37px average)
        val furiganaRuby = DetectedTextBlock(
            text = "わたし",
            boundingBox = rect(145, 105, 155, 117)
        )

        val rawBlocks = listOf(mainLine1, furiganaRuby, mainLine2)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(rawBlocks, density = 1.0f, sourceLanguage = "JA")

        assertEquals(1, clustered.size)
        // Furigana "わたし" should be filtered out, leaving clean text "私の名は"
        assertEquals("私の名は", clustered[0].text)
        assertFalse(clustered[0].text.contains("わたし"))
    }

    @Test
    fun testStandardHorizontalReadingOrderForKoreanAndWestern() {
        // Korean Manhwa: horizontal line 1 (left to right)
        val line1 = DetectedTextBlock(
            text = "나를",
            boundingBox = rect(100, 100, 150, 130)
        )
        val line1Part2 = DetectedTextBlock(
            text = "기억해?",
            boundingBox = rect(160, 100, 220, 130)
        )
        // Line 2 (below)
        val line2 = DetectedTextBlock(
            text = "정말?",
            boundingBox = rect(100, 140, 170, 170)
        )

        val rawBlocks = listOf(line2, line1Part2, line1)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(rawBlocks, density = 1.0f, sourceLanguage = "KO")

        assertEquals(1, clustered.size)
        // Korean should NOT be reversed: Line 1 then Line 2
        assertEquals("나를 기억해? 정말?", clustered[0].text)
    }

    @Test
    fun testIsTategakiLanguage() {
        val dummyBlock = listOf(
            DetectedTextBlock(text = "Hello", boundingBox = rect(0, 0, 10, 10))
        )
        val japaneseBlock = listOf(
            DetectedTextBlock(text = "こんにちは", boundingBox = rect(0, 0, 10, 10))
        )

        assertTrue(MangaBubbleClusterer.isTategakiLanguage("JA", dummyBlock))
        assertTrue(MangaBubbleClusterer.isTategakiLanguage("ja", dummyBlock))
        assertTrue(MangaBubbleClusterer.isTategakiLanguage("ZH", dummyBlock))
        assertFalse(MangaBubbleClusterer.isTategakiLanguage("KO", dummyBlock))
        assertFalse(MangaBubbleClusterer.isTategakiLanguage("EN", dummyBlock))
        assertFalse(MangaBubbleClusterer.isTategakiLanguage("ES", dummyBlock))

        // Auto detection with empty string
        assertTrue(MangaBubbleClusterer.isTategakiLanguage("", japaneseBlock))
        assertFalse(MangaBubbleClusterer.isTategakiLanguage("", dummyBlock))
    }
}
