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
        val density = context.resources.displayMetrics.density

        // High readability Miku cyberpunk dark slate card
        val bubbleBackground = GradientDrawable().apply {
            setColor(Color.parseColor("#F2171B24")) // 95% opacity dark slate to cleanly occlude original text
            cornerRadius = 12f * density
            setStroke((1.5f * density).toInt(), Color.parseColor("#39C5BB")) // Crisp Miku teal border
        }
        background = bubbleBackground
        elevation = 8f

        textView = TextView(context).apply {
            text = block.translatedText.ifEmpty { block.originalText }
            setTextColor(Color.WHITE)
            // Subtle black drop shadow for pristine legibility across any game or manga
            setShadowLayer(5f, 0f, 2f, Color.BLACK)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            includeFontPadding = false
            setPadding((6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
            setLineSpacing(1f, 1.0f)

            // Auto-size text to fill the bubble neatly
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                7,  // min text size in sp
                17, // max text size in sp
                1,  // step in sp
                TypedValue.COMPLEX_UNIT_SP
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
}
