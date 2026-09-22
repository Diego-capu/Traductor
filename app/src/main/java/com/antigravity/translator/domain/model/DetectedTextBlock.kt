package com.antigravity.translator.domain.model

import android.graphics.Rect

/**
 * Represents a discrete text block detected on the screen by ML Kit OCR.
 *
 * @param id Unique identifier for diffing and tracking
 * @param text The raw extracted text content
 * @param boundingBox Absolute screen coordinates where the text appears
 */
data class DetectedTextBlock(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val boundingBox: Rect,
    val originalTextSizePx: Float = 0f
) {
    /**
     * Checks geometric and textual equivalence to determine if this text
     * element has moved or changed between frames.
     */
    fun isContentEqual(other: DetectedTextBlock, tolerancePx: Int = 4): Boolean {
        if (this.text != other.text) return false
        val rectDiff = kotlin.math.abs(this.boundingBox.left - other.boundingBox.left) +
                kotlin.math.abs(this.boundingBox.top - other.boundingBox.top) +
                kotlin.math.abs(this.boundingBox.right - other.boundingBox.right) +
                kotlin.math.abs(this.boundingBox.bottom - other.boundingBox.bottom)
        return rectDiff <= tolerancePx
    }
}
