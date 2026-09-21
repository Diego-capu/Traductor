package com.antigravity.translator.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import com.antigravity.translator.domain.model.TranslatedBlock

/**
 * Transparent full-screen overlay canvas that draws translated text pills/boxes
 * directly over detected bounding boxes.
 *
 * Remains View.GONE whenever there are no translations to display, ensuring zero
 * interference with touch events or system Untrusted Touch protections.
 */
class TranslationCanvasView(context: Context) : View(context) {

    private val density = context.resources.displayMetrics.density
    private val blocks = mutableListOf<TranslatedBlock>()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2171B24") // Deep cyber slate (95% opacity)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#39C5BB") // Signature Vocaloid Teal
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * density
        isFakeBoldText = true
        // Text drop shadow for pristine legibility across busy game / manga content
        setShadowLayer(5f, 0f, 2f, Color.BLACK)
    }

    private val cornerRadius = 12f * density
    private val padding = 8f * density

    init {
        // Start hidden so it consumes zero resources and never blocks touches
        visibility = GONE
    }

    @Synchronized
    fun updateBlocks(newBlocks: List<TranslatedBlock>) {
        blocks.clear()
        blocks.addAll(newBlocks)
        post {
            visibility = if (blocks.isEmpty()) GONE else VISIBLE
            invalidate()
        }
    }

    @Synchronized
    fun clear() {
        blocks.clear()
        post {
            visibility = GONE
            invalidate()
        }
    }

    @Synchronized
    fun setOverlayVisible(visible: Boolean) {
        post {
            visibility = if (visible && blocks.isNotEmpty()) VISIBLE else GONE
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        synchronized(this) {
            for (block in blocks) {
                val rect = block.boundingBox
                val text = block.translatedText.ifEmpty { block.originalText }
                if (text.isBlank()) continue

                val boxWidth = kotlin.math.max(rect.width().toFloat(), 80f)
                val targetTextWidth = boxWidth - (padding * 2)

                // Dynamically build StaticLayout for multiline wrapping
                val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(
                        text,
                        0,
                        text.length,
                        textPaint,
                        kotlin.math.max(targetTextWidth.toInt(), 50)
                    )
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setIncludePad(false)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(
                        text,
                        textPaint,
                        kotlin.math.max(targetTextWidth.toInt(), 50),
                        Layout.Alignment.ALIGN_NORMAL,
                        1.0f,
                        0.0f,
                        false
                    )
                }

                val layoutHeight = staticLayout.height.toFloat()
                val totalHeight = kotlin.math.max(rect.height().toFloat(), layoutHeight + (padding * 2))
                val totalWidth = kotlin.math.max(boxWidth, staticLayout.width + (padding * 2))

                val bgRect = RectF(
                    rect.left.toFloat(),
                    rect.top.toFloat(),
                    rect.left.toFloat() + totalWidth,
                    rect.top.toFloat() + totalHeight
                )

                // 1. Draw rounded background box to cleanly occlude original text
                canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)
                canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, borderPaint)

                // 2. Draw translated text inside pill
                canvas.save()
                canvas.translate(bgRect.left + padding, bgRect.top + padding)
                staticLayout.draw(canvas)
                canvas.restore()
            }
        }
    }
}
