package com.antigravity.translator

import android.graphics.Rect
import com.antigravity.translator.domain.model.DetectedTextBlock
import com.antigravity.translator.domain.model.ReadingProfile
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
        // Tiny Furigana annotation next to kanji (height = 6px < 20% of ~35px average, placed outside main column)
        val furiganaRuby = DetectedTextBlock(
            text = "わたし",
            boundingBox = rect(145, 105, 155, 111)
        )

        val rawBlocks = listOf(mainLine1, furiganaRuby, mainLine2)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(rawBlocks, density = 1.0f, sourceLanguage = "JA")

        assertEquals(1, clustered.size)
        // Furigana "わたし" should be filtered out, leaving clean text "私の名は"
        assertEquals("私の名は", clustered[0].text)
        assertFalse(clustered[0].text.contains("わたし"))
    }

    @Test
    fun testPreservesSmallSingleStrokeKanjiAlignedInColumn() {
        // Main kanji line 1 (left = 100..140)
        val mainLine1 = DetectedTextBlock(
            text = "第",
            boundingBox = rect(100, 100, 140, 150)
        )
        // Single-stroke kanji "一" (height = 6px < 20%, but aligned vertically in column at left=110..130)
        val singleStrokeIchi = DetectedTextBlock(
            text = "一",
            boundingBox = rect(110, 155, 130, 161)
        )
        // Main kanji line 2 (left = 100..140)
        val mainLine2 = DetectedTextBlock(
            text = "話",
            boundingBox = rect(100, 166, 140, 216)
        )

        val rawBlocks = listOf(mainLine1, singleStrokeIchi, mainLine2)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(rawBlocks, density = 1.0f, sourceLanguage = "JA")

        assertEquals(1, clustered.size)
        // "一" must NOT be discarded because it is vertically aligned within the column
        assertEquals("第一話", clustered[0].text)
    }

    @Test
    fun testMangaProfileVerticalGapTolerance() {
        // Two vertical pieces separated by a 30px gap
        // Default tolerance is 24px, but MANGA profile has +35% tolerance = 32.4px
        val line1 = DetectedTextBlock(
            text = "上の段",
            boundingBox = rect(100, 100, 140, 150)
        )
        val line2 = DetectedTextBlock(
            text = "下の段",
            boundingBox = rect(100, 180, 140, 230) // 180 - 150 = 30px gap
        )

        val rawBlocks = listOf(line1, line2)
        val clusteredManga = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = rawBlocks,
            density = 1.0f,
            sourceLanguage = "JA",
            readingProfile = ReadingProfile.MANGA
        )

        // Under MANGA profile (32.4px tolerance), 30px gap is clustered into 1 bubble
        assertEquals(1, clusteredManga.size)
        assertEquals("上の段下の段", clusteredManga[0].text)
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

    @Test
    fun testSeparateVerticalMangaBubblesDoNotMergeAcrossLargeHorizontalGap() {
        // Character A's speech bubble (right side): width = 40, height = 150
        val bubbleA = DetectedTextBlock(
            text = "何をしている？",
            boundingBox = rect(300, 100, 340, 250)
        )
        // Character B's speech bubble (left side): width = 40, height = 150
        // Horizontal gap = 300 - 240 = 60px (> 1.2 * 40 = 48px)
        val bubbleB = DetectedTextBlock(
            text = "別に何も",
            boundingBox = rect(200, 100, 240, 250)
        )

        val clustered = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = listOf(bubbleA, bubbleB),
            density = 1.0f,
            sourceLanguage = "JA",
            readingProfile = ReadingProfile.MANGA
        )

        // Must remain 2 separate distinct speech bubbles
        assertEquals(2, clustered.size)
        assertTrue(clustered.any { it.text == "何をしている？" })
        assertTrue(clustered.any { it.text == "別に何も" })
    }

    @Test
    fun testSeparateVerticalMangaBubblesDoNotMergeWithoutVerticalOverlap() {
        // Character A dialogue column at top (Y: 100..200)
        val bubbleTop = DetectedTextBlock(
            text = "逃げろ！",
            boundingBox = rect(200, 100, 240, 200)
        )
        // Character B dialogue column below (Y: 220..320)
        // They share nearby X (180..220 vs 200..240), but have 0 vertical overlap
        val bubbleBottom = DetectedTextBlock(
            text = "待ってくれ！",
            boundingBox = rect(180, 220, 220, 320)
        )

        val clustered = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = listOf(bubbleTop, bubbleBottom),
            density = 1.0f,
            sourceLanguage = "JA",
            readingProfile = ReadingProfile.MANGA
        )

        // Must remain 2 separate distinct speech bubbles
        assertEquals(2, clustered.size)
        assertTrue(clustered.any { it.text == "逃げろ！" })
        assertTrue(clustered.any { it.text == "待ってくれ！" })
    }

    @Test
    fun testMangaBoundingBoxProportionGuardRejectsAbnormallyWideClusters() {
        // Two candidate dialogue blocks:
        // Bubble 1: width = 50, height = 60
        val bubble1 = DetectedTextBlock(
            text = "あ",
            boundingBox = rect(300, 100, 350, 160)
        )
        // Bubble 2: width = 50, height = 60
        // If merged: unionWidth = 350 - 180 = 170, unionHeight = 60
        // unionWidth (170) > unionHeight (60) * 1.5 = 90
        val bubble2 = DetectedTextBlock(
            text = "い",
            boundingBox = rect(180, 100, 230, 160)
        )

        val clustered = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = listOf(bubble1, bubble2),
            density = 1.0f,
            sourceLanguage = "JA",
            readingProfile = ReadingProfile.MANGA
        )

        // Must be rejected by Proportion Guard and kept as 2 distinct bubbles
        assertEquals(2, clustered.size)
    }

    @Test
    fun testHorizontalTextDoesNotMergeWhenVerticalLineGapExceedsThreshold() {
        // Comic / Manhwa profile: two lines with height = 30
        val line1 = DetectedTextBlock(
            text = "First dialogue bubble",
            boundingBox = rect(100, 100, 300, 130)
        )
        // Line 2 spaced further apart: top = 160 (vGap = 160 - 130 = 30px > 0.8 * 30 = 24px)
        val line2 = DetectedTextBlock(
            text = "Second separate bubble",
            boundingBox = rect(100, 160, 300, 190)
        )

        val clustered = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = listOf(line1, line2),
            density = 1.0f,
            sourceLanguage = "EN",
            readingProfile = ReadingProfile.COMIC
        )

        // Must remain 2 separate distinct speech bubbles
        assertEquals(2, clustered.size)
        assertTrue(clustered.any { it.text == "First dialogue bubble" })
        assertTrue(clustered.any { it.text == "Second separate bubble" })
    }
}
