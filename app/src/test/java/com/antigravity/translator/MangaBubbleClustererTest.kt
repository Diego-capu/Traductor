package com.antigravity.translator

import android.graphics.Rect
import com.antigravity.translator.domain.model.DetectedTextBlock
import com.antigravity.translator.engine.ocr.MangaBubbleClusterer
import org.junit.Assert.assertEquals
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
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(rawBlocks, density = 1.0f)

        // Should result in exactly 2 clusters
        assertEquals(2, clustered.size)

        val firstBubble = clustered.first { it.boundingBox.left <= 100 && 100 <= it.boundingBox.right && it.boundingBox.top <= 100 && 100 <= it.boundingBox.bottom }
        assertEquals("it'd be cowardly of me to flee?", firstBubble.text)

        val secondBubble = clustered.first { it.boundingBox.left <= 500 && 500 <= it.boundingBox.right && it.boundingBox.top <= 500 && 500 <= it.boundingBox.bottom }
        assertEquals("HUH?", secondBubble.text)
    }
}
