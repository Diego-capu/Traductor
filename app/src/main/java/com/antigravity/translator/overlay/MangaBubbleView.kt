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

        // Strict bounding constraints matching original detected speech bubble box
        layoutParams = LayoutParams(
            if (boxWidth > 0) boxWidth else LayoutParams.WRAP_CONTENT,
            if (boxHeight > 0) boxHeight else LayoutParams.WRAP_CONTENT
        )

        val minLegibleSizePx = 14 // Minimum legible size in px (~7-8sp)
        val originalSizePx = if (block.originalTextSizePx > 0f) {
            block.originalTextSizePx
        } else {
            16f * density
        }
        val maxTextSizePx = originalSizePx.toInt().coerceAtLeast(minLegibleSizePx)

        // Minimal internal padding (2dp to 4dp) to maximize dialogue text fill
        val padX = (3 * density).toInt().coerceIn(2, 6)
        val padY = (2 * density).toInt().coerceIn(2, 4)

        textView = TextView(context).apply {
            text = block.translatedText.ifEmpty { block.originalText }
            setTextColor(Color.parseColor("#0A0A0A"))
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            includeFontPadding = false
            setPadding(padX, padY, padX, padY)
            setLineSpacing(0.5f, 0.95f)

            // Strict dimension constraints so text never overflows original bubble coordinates
            if (boxWidth > 0) {
                maxWidth = boxWidth
            }
            if (boxHeight > 0) {
                maxHeight = boxHeight
            }

            // Base font size matching physical detected character height 1:1
            setTextSize(TypedValue.COMPLEX_UNIT_PX, originalSizePx)

            // Auto-size text: exact 1:1 original size as upper ceiling, smoothly downscaling if translation is longer
            if (maxTextSizePx > minLegibleSizePx) {
                try {
                    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                        this,
                        minLegibleSizePx,
                        maxTextSizePx,
                        1,
                        TypedValue.COMPLEX_UNIT_PX
                    )
                } catch (e: Exception) {
                    android.util.Log.w("MangaBubbleView", "Auto-sizing fallback: ${e.message}")
                }
            }
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
