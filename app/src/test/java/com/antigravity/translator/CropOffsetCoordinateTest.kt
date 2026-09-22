package com.antigravity.translator

import android.graphics.Rect
import com.antigravity.translator.domain.model.DetectedTextBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class CropOffsetCoordinateTest {

    private fun rect(l: Int, t: Int, r: Int, b: Int) = Rect().apply {
        left = l
        top = t
        right = r
        bottom = b
    }

    @Test
    fun testBoundingBoxOffsetMapping() {
        val originalBox = rect(10, 20, 110, 80)
        val block = DetectedTextBlock(
            text = "Dialogue line",
            boundingBox = originalBox
        )

        val cropOffsetX = 100
        val cropOffsetY = 250

        val mappedBox = rect(
            block.boundingBox.left + cropOffsetX,
            block.boundingBox.top + cropOffsetY,
            block.boundingBox.right + cropOffsetX,
            block.boundingBox.bottom + cropOffsetY
        )
        val mappedBlock = block.copy(boundingBox = mappedBox)

        assertEquals(110, mappedBlock.boundingBox.left)
        assertEquals(270, mappedBlock.boundingBox.top)
        assertEquals(210, mappedBlock.boundingBox.right)
        assertEquals(330, mappedBlock.boundingBox.bottom)
        assertEquals(100, mappedBlock.boundingBox.right - mappedBlock.boundingBox.left)
        assertEquals(60, mappedBlock.boundingBox.bottom - mappedBlock.boundingBox.top)
    }

    @Test
    fun testClampingWithinScreenBounds() {
        val screenWidth = 1080
        val screenHeight = 2400

        // Excessively large or negative crop box
        val requestedCrop = rect(-50, -20, 1200, 2500)

        val left = requestedCrop.left.coerceIn(0, screenWidth - 2)
        val top = requestedCrop.top.coerceIn(0, screenHeight - 2)
        val right = requestedCrop.right.coerceIn(left + 1, screenWidth)
        val bottom = requestedCrop.bottom.coerceIn(top + 1, screenHeight)
        val clamped = rect(left, top, right, bottom)

        assertEquals(0, clamped.left)
        assertEquals(0, clamped.top)
        assertEquals(screenWidth, clamped.right)
        assertEquals(screenHeight, clamped.bottom)
    }
}
