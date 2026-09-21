package com.antigravity.translator

import android.graphics.Rect
import com.antigravity.translator.domain.model.DetectedTextBlock
import com.antigravity.translator.engine.ocr.MangaBubbleClusterer
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaBubbleClustererTest {

    @Test
    fun testClusterLinesInSameBubbleAndFixHyphenation() {
        val line1 = DetectedTextBlock(
            text = "it'd be cow-",
            boundingBox = Rect(100, 100, 250, 130)
        )
        val line2 = DetectedTextBlock(
            text = "ardly of me",
            boundingBox = Rect(100, 135, 250, 165)
        )
        val line3 = DetectedTextBlock(
            text = "to flee?",
            boundingBox = Rect(100, 170, 250, 200)
        )

        // Separate bubble in another panel far away
        val distantBubble = DetectedTextBlock(
            text = "HUH?",
            boundingBox = Rect(500, 500, 600, 550)
        )

        val rawBlocks = listOf(line1, line2, line3, distantBubble)
        val clustered = MangaBubbleClusterer.clusterMangaBubbles(rawBlocks, density = 1.0f)

        // Should result in exactly 2 clusters
        assertEquals(2, clustered.size)

        val firstBubble = clustered.first { it.boundingBox.contains(100, 100) }
        assertEquals("it'd be cowardly of me to flee?", firstBubble.text)

        val secondBubble = clustered.first { it.boundingBox.contains(500, 500) }
        assertEquals("HUH?", secondBubble.text)
    }
}
