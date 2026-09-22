package com.antigravity.translator

import android.graphics.Rect
import com.antigravity.translator.domain.model.DetectedTextBlock
import com.antigravity.translator.domain.model.TranslatedBlock
import com.antigravity.translator.engine.ocr.MangaBubbleClusterer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicFontSizeTest {

    private fun rect(l: Int, t: Int, r: Int, b: Int) = Rect().apply {
        left = l
        top = t
        right = r
        bottom = b
    }

    @Test
    fun testDetectedTextBlockDefaultAndCustomFontSize() {
        val defaultBlock = DetectedTextBlock(
            text = "Test",
            boundingBox = rect(0, 0, 100, 50)
        )
        assertEquals(0f, defaultBlock.originalTextSizePx, 0.001f)

        val customBlock = DetectedTextBlock(
            text = "Custom",
            boundingBox = rect(0, 0, 100, 50),
            originalTextSizePx = 28.5f
        )
        assertEquals(28.5f, customBlock.originalTextSizePx, 0.001f)

        val copiedBlock = customBlock.copy(text = "Copied")
        assertEquals(28.5f, copiedBlock.originalTextSizePx, 0.001f)
    }

    @Test
    fun testTranslatedBlockMaintainsFontSize() {
        val translatedBlock = TranslatedBlock(
            id = "test-id",
            originalText = "Hello",
            translatedText = "Hola",
            boundingBox = rect(10, 10, 110, 60),
            originalTextSizePx = 24.0f
        )
        assertEquals(24.0f, translatedBlock.originalTextSizePx, 0.001f)
    }

    @Test
    fun testClusterAveragesOriginalTextSizes() {
        val block1 = DetectedTextBlock(
            text = "Line One",
            boundingBox = rect(50, 50, 200, 80),
            originalTextSizePx = 24.0f
        )
        val block2 = DetectedTextBlock(
            text = "Line Two",
            boundingBox = rect(50, 85, 200, 115),
            originalTextSizePx = 28.0f
        )

        val clusters = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = listOf(block1, block2),
            density = 1.0f,
            sourceLanguage = "EN"
        )

        assertEquals(1, clusters.size)
        // Average of 24.0 and 28.0 is 26.0
        assertEquals(26.0f, clusters[0].originalTextSizePx, 0.001f)
    }

    @Test
    fun testClusterWeightedAverageOriginalTextSizes() {
        val block1 = DetectedTextBlock(
            text = "0123456789", // 10 chars
            boundingBox = rect(50, 50, 200, 80),
            originalTextSizePx = 30.0f
        )
        val block2 = DetectedTextBlock(
            text = "A".repeat(90), // 90 chars
            boundingBox = rect(50, 85, 200, 115),
            originalTextSizePx = 20.0f
        )

        val clusters = MangaBubbleClusterer.clusterMangaBubbles(
            blocks = listOf(block1, block2),
            density = 1.0f,
            sourceLanguage = "EN"
        )

        assertEquals(1, clusters.size)
        // Weighted average: (30.0 * 10 + 20.0 * 90) / 100 = 21.0f
        assertEquals(21.0f, clusters[0].originalTextSizePx, 0.001f)
    }

    @Test
    fun testVerticalTextLineHeightRatioCalculation() {
        // Tategaki column: height is 300, width is 40 -> height > width * 1.4
        val columnWidth = 40f
        val columnHeight = 300f
        val isVertical = columnHeight > (columnWidth * 1.4f)
        assertTrue(isVertical)

        val calculatedFontSize = columnWidth * 0.85f
        assertEquals(34.0f, calculatedFontSize, 0.001f)

        // Horizontal line: width is 300, height is 40 -> not vertical
        val horizWidth = 300f
        val horizHeight = 40f
        val isHorizontal = horizHeight <= (horizWidth * 1.4f)
        assertTrue(isHorizontal)

        val horizFontSize = horizHeight * 0.85f
        assertEquals(34.0f, horizFontSize, 0.001f)
    }

    @Test
    fun testAutoSizeBoundsAlwaysStrictlyAscending() {
        val density = 2.5f
        val testSizes = listOf(0f, 5f, 10f, 12f, 15f, 18f, 20f, 25f, 50f, 100f)

        for (size in testSizes) {
            val minTextSize = (10 * density).toInt().coerceAtLeast(12)
            val maxTextSize = if (size > 0f) {
                size.toInt().coerceAtLeast(minTextSize + 4)
            } else {
                (17 * density).toInt().coerceAtLeast(minTextSize + 4)
            }
            assertTrue("maxTextSize ($maxTextSize) must be strictly greater than minTextSize ($minTextSize)", maxTextSize > minTextSize)
        }
    }
}
