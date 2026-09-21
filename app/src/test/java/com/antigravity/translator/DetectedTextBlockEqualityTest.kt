package com.antigravity.translator

import android.graphics.Rect
import com.antigravity.translator.domain.model.DetectedTextBlock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectedTextBlockEqualityTest {

    @Test
    fun testContentEqualWithIdenticalCoordinates() {
        val block1 = DetectedTextBlock(
            text = "Settings",
            boundingBox = Rect(10, 20, 100, 50)
        )
        val block2 = DetectedTextBlock(
            text = "Settings",
            boundingBox = Rect(10, 20, 100, 50)
        )

        assertTrue(block1.isContentEqual(block2))
    }

    @Test
    fun testContentEqualWithMinorJitterWithinTolerance() {
        val block1 = DetectedTextBlock(
            text = "Profile",
            boundingBox = Rect(10, 20, 100, 50)
        )
        // 1 pixel jitter in left and top
        val block2 = DetectedTextBlock(
            text = "Profile",
            boundingBox = Rect(11, 21, 100, 50)
        )

        assertTrue(block1.isContentEqual(block2, tolerancePx = 4))
    }

    @Test
    fun testContentNotEqualWhenTextDiffers() {
        val block1 = DetectedTextBlock(
            text = "Hello",
            boundingBox = Rect(10, 20, 100, 50)
        )
        val block2 = DetectedTextBlock(
            text = "World",
            boundingBox = Rect(10, 20, 100, 50)
        )

        assertFalse(block1.isContentEqual(block2))
    }
}
