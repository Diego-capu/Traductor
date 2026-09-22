package com.antigravity.translator.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.antigravity.translator.domain.model.TranslatedBlock

/**
 * An individual interactive comic speech bubble overlay view.
 *
 * Placed directly over the detected manga speech bubble coordinates.
 * Features:
 * - Solid opaque white background to completely occlude the original text.
 * - Rounded comic-style border.
 * - Centered high-contrast black text with auto-fitting font size.
 * - On-click callback to open the large translation detail view.
 */
@SuppressLint("SetTextI18n")
class MangaBubbleView(
    context: Context,
    val block: TranslatedBlock,
    private val onBubbleClicked: (TranslatedBlock) -> Unit,
    private val onBubbleLongClicked: ((TranslatedBlock) -> Unit)? = null
) : FrameLayout(context) {

    val textView: TextView

    init {
        // Comic speech bubble card styling
        val bubbleBackground = GradientDrawable().apply {
            setColor(Color.WHITE) // Solid white to completely cover original manga dialogue
            cornerRadius = 18f
            setStroke(2, Color.parseColor("#1C1B1F")) // Classic comic ink outline
        }
        background = bubbleBackground
        elevation = 6f

        val density = context.resources.displayMetrics.density
        val boxWidth = block.boundingBox.width()
        val boxHeight = block.boundingBox.height()

        val autoSizeMinTextSizeInPx = 18 // ~9sp minimum readable threshold
        val autoSizeMaxTextSizeInPx = if (block.originalTextSizePx > 0f) {
            block.originalTextSizePx.toInt().coerceAtLeast(autoSizeMinTextSizeInPx)
        } else {
            (17 * density).toInt().coerceAtLeast(autoSizeMinTextSizeInPx)
        }

        textView = TextView(context).apply {
            text = block.translatedText.ifEmpty { block.originalText }
            setTextColor(Color.parseColor("#0A0A0A"))
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            includeFontPadding = false
            setPadding(6, 4, 6, 4)
            setLineSpacing(1f, 1.0f)

            // Precision #2: Dimension constraints so auto-sizing downscales properly without unbounded expansion
            if (boxWidth > 0) {
                maxWidth = boxWidth + (8 * density).toInt()
            }
            if (boxHeight > 0) {
                maxHeight = boxHeight + (6 * density).toInt()
            }

            // Set primary font size matching original detected text height
            setTextSize(TypedValue.COMPLEX_UNIT_PX, autoSizeMaxTextSizeInPx.toFloat())

            // Auto-size text to fill the bubble neatly with originalTextSizePx as maximum
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                autoSizeMinTextSizeInPx,
                autoSizeMaxTextSizeInPx,
                1,
                TypedValue.COMPLEX_UNIT_PX
            )
        }

        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            gravity = Gravity.CENTER
        }
        addView(textView, params)

        // Tapping opens large detail view
        setOnClickListener {
            onBubbleClicked(block)
        }

        // Long-pressing triggers instant TTS speech
        setOnLongClickListener {
            if (onBubbleLongClicked != null) {
                onBubbleLongClicked.invoke(block)
                true
            } else {
                false
            }
        }
    }

    /**
     * Cleans up child views and listeners when recycling or detaching the overlay.
     */
    fun cleanup() {
        setOnClickListener(null)
        setOnLongClickListener(null)
        removeAllViews()
    }
}
