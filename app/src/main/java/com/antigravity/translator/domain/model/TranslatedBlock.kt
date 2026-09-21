package com.antigravity.translator.domain.model

import android.graphics.Rect

/**
 * Represents a translated block ready for overlay rendering.
 *
 * @param id Unique identifier linking to the detected block
 * @param originalText Original source text detected by OCR
 * @param translatedText Text translated by DeepL or retrieved from cache
 * @param boundingBox Absolute screen bounds for overlay drawing
 */
data class TranslatedBlock(
    val id: String,
    val originalText: String,
    val translatedText: String,
    val boundingBox: Rect
)
