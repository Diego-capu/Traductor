package com.antigravity.translator.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Interactive full-screen crop selection overlay.
 *
 * Allows the user to freely resize and drag a crop bounding box defining the persistent
 * screen translation region. Features darkened scrim exterior, Miku cyan framing,
 * corner/edge touch handles, dimension indicator, and zero emojis.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class RegionCropOverlayView(
    context: Context,
    initialCropRect: Rect?,
    private val onCropConfirmed: (Rect) -> Unit,
    private val onFullScreenSelected: () -> Unit,
    private val onDismissRequested: () -> Unit
) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val displayMetrics = context.resources.displayMetrics
    private val screenWidth = displayMetrics.widthPixels
    private val screenHeight = displayMetrics.heightPixels

    private val cropCanvasView: CropCanvasView

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Initialize crop rect (use saved rect if available, or center 82% width x 50% height default)
        val defaultRect = if (initialCropRect != null && initialCropRect.width() > 50 && initialCropRect.height() > 50) {
            val clamped = RectF(
                initialCropRect.left.toFloat().coerceIn(0f, (screenWidth - 80 * density)),
                initialCropRect.top.toFloat().coerceIn(0f, (screenHeight - 80 * density)),
                initialCropRect.right.toFloat().coerceIn(80 * density, screenWidth.toFloat()),
                initialCropRect.bottom.toFloat().coerceIn(80 * density, screenHeight.toFloat())
            )
            clamped
        } else {
            val marginX = (24 * density)
            val boxWidth = screenWidth - (marginX * 2)
            val boxHeight = (screenHeight * 0.52f)
            val boxTop = (screenHeight - boxHeight) / 2f
            RectF(marginX, boxTop, marginX + boxWidth, boxTop + boxHeight)
        }

        cropCanvasView = CropCanvasView(context, defaultRect, density, screenWidth, screenHeight)
        addView(
            cropCanvasView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        // 1. Top Instruction Banner
        val topBanner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6111827"))
                cornerRadius = 18 * density
                setStroke((1 * density).toInt(), Color.parseColor("#3339C5BB"))
            }

            val titleView = TextView(context).apply {
                text = "Área de Traducción Fija"
                setTextColor(Color.parseColor("#39C5BB"))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }

            val subtitleView = TextView(context).apply {
                text = "Arrastra esquinas o bordes para ajustar qué parte traducir"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11.5f
                gravity = Gravity.CENTER
            }

            addView(titleView)
            addView(subtitleView)
        }

        val topParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = (44 * density).toInt()
        }
        addView(topBanner, topParams)

        // 2. Bottom Actions Bar Card
        val bottomCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F20F172A"))
                cornerRadius = 22 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#26FFFFFF"))
            }
            elevation = 16f

            // Full Screen Button
            val fullScreenBtn = TextView(context).apply {
                text = "Pantalla Completa"
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#26334155"))
                    cornerRadius = 14 * density
                    setStroke((1 * density).toInt(), Color.parseColor("#475569"))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onFullScreenSelected()
                }
            }

            // Confirm Button
            val confirmBtn = TextView(context).apply {
                text = "Confirmar Área"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 13.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding((22 * density).toInt(), (10 * density).toInt(), (22 * density).toInt(), (10 * density).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#39C5BB"))
                    cornerRadius = 14 * density
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val rect = cropCanvasView.getClampedCropRect()
                    onCropConfirmed(rect)
                }
            }

            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams((12 * density).toInt(), 1)
            }

            addView(fullScreenBtn)
            addView(spacer)
            addView(confirmBtn)
        }

        val bottomParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (36 * density).toInt()
        }
        addView(bottomCard, bottomParams)
    }

    /**
     * Inner custom view handling touch gestures and drawing the selection box.
     */
    private class CropCanvasView(
        context: Context,
        private val cropRect: RectF,
        private val density: Float,
        private val screenWidth: Int,
        private val screenHeight: Int
    ) : View(context) {

        private enum class TouchMode {
            NONE,
            MOVE_BOX,
            CORNER_TOP_LEFT,
            CORNER_TOP_RIGHT,
            CORNER_BOTTOM_LEFT,
            CORNER_BOTTOM_RIGHT,
            EDGE_TOP,
            EDGE_BOTTOM,
            EDGE_LEFT,
            EDGE_RIGHT
        }

        private var currentTouchMode = TouchMode.NONE
        private var lastTouchX = 0f
        private var lastTouchY = 0f

        private val minSizePx = 72 * density
        private val touchRadiusPx = 36 * density
        private val handleRadiusPx = 8.5f * density
        private val edgeHandleRadiusPx = 4.5f * density

        // Paints
        private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 10, 15, 26)
            style = Paint.Style.FILL
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#39C5BB")
            strokeWidth = 2.5f * density
            style = Paint.Style.STROKE
        }

        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3339C5BB")
            strokeWidth = 1f * density
            style = Paint.Style.STROKE
        }

        private val handleOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#39C5BB")
            style = Paint.Style.FILL
        }

        private val handleInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 11f * density
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        private val badgeBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC0F172A")
            style = Paint.Style.FILL
        }

        fun getClampedCropRect(): Rect {
            return Rect(
                cropRect.left.toInt().coerceIn(0, screenWidth - 1),
                cropRect.top.toInt().coerceIn(0, screenHeight - 1),
                cropRect.right.toInt().coerceIn(cropRect.left.toInt() + 1, screenWidth),
                cropRect.bottom.toInt().coerceIn(cropRect.top.toInt() + 1, screenHeight)
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val widthF = width.toFloat()
            val heightF = height.toFloat()

            // 1. Draw 4 scrim rectangles surrounding the crop box
            // Top
            canvas.drawRect(0f, 0f, widthF, cropRect.top, scrimPaint)
            // Bottom
            canvas.drawRect(0f, cropRect.bottom, widthF, heightF, scrimPaint)
            // Left
            canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, scrimPaint)
            // Right
            canvas.drawRect(cropRect.right, cropRect.top, widthF, cropRect.bottom, scrimPaint)

            // 2. Rule of thirds subtle grid inside crop box
            val oneThirdW = cropRect.width() / 3f
            val oneThirdH = cropRect.height() / 3f
            canvas.drawLine(cropRect.left + oneThirdW, cropRect.top, cropRect.left + oneThirdW, cropRect.bottom, gridPaint)
            canvas.drawLine(cropRect.left + (oneThirdW * 2f), cropRect.top, cropRect.left + (oneThirdW * 2f), cropRect.bottom, gridPaint)
            canvas.drawLine(cropRect.left, cropRect.top + oneThirdH, cropRect.right, cropRect.top + oneThirdH, gridPaint)
            canvas.drawLine(cropRect.left, cropRect.top + (oneThirdH * 2f), cropRect.right, cropRect.top + (oneThirdH * 2f), gridPaint)

            // 3. Framing border
            canvas.drawRect(cropRect, borderPaint)

            // 4. Corner handles (filled cyan with white core)
            drawCornerHandle(canvas, cropRect.left, cropRect.top)
            drawCornerHandle(canvas, cropRect.right, cropRect.top)
            drawCornerHandle(canvas, cropRect.left, cropRect.bottom)
            drawCornerHandle(canvas, cropRect.right, cropRect.bottom)

            // 5. Edge midpoint handles (small cyan circles)
            val midX = cropRect.centerX()
            val midY = cropRect.centerY()
            canvas.drawCircle(midX, cropRect.top, edgeHandleRadiusPx, handleOuterPaint)
            canvas.drawCircle(midX, cropRect.bottom, edgeHandleRadiusPx, handleOuterPaint)
            canvas.drawCircle(cropRect.left, midY, edgeHandleRadiusPx, handleOuterPaint)
            canvas.drawCircle(cropRect.right, midY, edgeHandleRadiusPx, handleOuterPaint)

            // 6. Dimension badge pill inside/above the crop box
            val dimensionText = "${cropRect.width().toInt()} × ${cropRect.height().toInt()} px"
            val textWidth = textPaint.measureText(dimensionText)
            val badgePaddingX = 8f * density
            val badgeHeight = 18f * density
            val badgeY = if (cropRect.top > (30 * density)) {
                cropRect.top - (badgeHeight / 2f) - (6 * density)
            } else {
                cropRect.top + (badgeHeight / 2f) + (6 * density)
            }
            val badgeRect = RectF(
                midX - (textWidth / 2f) - badgePaddingX,
                badgeY - (badgeHeight / 2f),
                midX + (textWidth / 2f) + badgePaddingX,
                badgeY + (badgeHeight / 2f)
            )
            canvas.drawRoundRect(badgeRect, 6f * density, 6f * density, badgeBackgroundPaint)
            canvas.drawText(dimensionText, midX, badgeY + (4f * density), textPaint)
        }

        private fun drawCornerHandle(canvas: Canvas, cx: Float, cy: Float) {
            canvas.drawCircle(cx, cy, handleRadiusPx, handleOuterPaint)
            canvas.drawCircle(cx, cy, handleRadiusPx * 0.55f, handleInnerPaint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = x
                    lastTouchY = y
                    currentTouchMode = detectTouchMode(x, y)
                    return currentTouchMode != TouchMode.NONE
                }

                MotionEvent.ACTION_MOVE -> {
                    if (currentTouchMode == TouchMode.NONE) return false

                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    lastTouchX = x
                    lastTouchY = y

                    applyGesture(dx, dy)
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    currentTouchMode = TouchMode.NONE
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun detectTouchMode(x: Float, y: Float): TouchMode {
            // Check corners first (highest priority)
            if (dist(x, y, cropRect.left, cropRect.top) <= touchRadiusPx) return TouchMode.CORNER_TOP_LEFT
            if (dist(x, y, cropRect.right, cropRect.top) <= touchRadiusPx) return TouchMode.CORNER_TOP_RIGHT
            if (dist(x, y, cropRect.left, cropRect.bottom) <= touchRadiusPx) return TouchMode.CORNER_BOTTOM_LEFT
            if (dist(x, y, cropRect.right, cropRect.bottom) <= touchRadiusPx) return TouchMode.CORNER_BOTTOM_RIGHT

            // Check edges
            if (kotlin.math.abs(y - cropRect.top) <= touchRadiusPx && x >= cropRect.left && x <= cropRect.right) return TouchMode.EDGE_TOP
            if (kotlin.math.abs(y - cropRect.bottom) <= touchRadiusPx && x >= cropRect.left && x <= cropRect.right) return TouchMode.EDGE_BOTTOM
            if (kotlin.math.abs(x - cropRect.left) <= touchRadiusPx && y >= cropRect.top && y <= cropRect.bottom) return TouchMode.EDGE_LEFT
            if (kotlin.math.abs(x - cropRect.right) <= touchRadiusPx && y >= cropRect.top && y <= cropRect.bottom) return TouchMode.EDGE_RIGHT

            // Check inside box
            if (cropRect.contains(x, y)) return TouchMode.MOVE_BOX

            return TouchMode.NONE
        }

        private fun applyGesture(dx: Float, dy: Float) {
            val maxW = screenWidth.toFloat()
            val maxH = screenHeight.toFloat()

            when (currentTouchMode) {
                TouchMode.MOVE_BOX -> {
                    val boxW = cropRect.width()
                    val boxH = cropRect.height()
                    var newLeft = cropRect.left + dx
                    var newTop = cropRect.top + dy

                    // Clamp inside display
                    if (newLeft < 0f) newLeft = 0f
                    if (newTop < 0f) newTop = 0f
                    if (newLeft + boxW > maxW) newLeft = maxW - boxW
                    if (newTop + boxH > maxH) newTop = maxH - boxH

                    cropRect.set(newLeft, newTop, newLeft + boxW, newTop + boxH)
                }

                TouchMode.CORNER_TOP_LEFT -> {
                    val newLeft = (cropRect.left + dx).coerceIn(0f, cropRect.right - minSizePx)
                    val newTop = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minSizePx)
                    cropRect.left = newLeft
                    cropRect.top = newTop
                }

                TouchMode.CORNER_TOP_RIGHT -> {
                    val newRight = (cropRect.right + dx).coerceIn(cropRect.left + minSizePx, maxW)
                    val newTop = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minSizePx)
                    cropRect.right = newRight
                    cropRect.top = newTop
                }

                TouchMode.CORNER_BOTTOM_LEFT -> {
                    val newLeft = (cropRect.left + dx).coerceIn(0f, cropRect.right - minSizePx)
                    val newBottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minSizePx, maxH)
                    cropRect.left = newLeft
                    cropRect.bottom = newBottom
                }

                TouchMode.CORNER_BOTTOM_RIGHT -> {
                    val newRight = (cropRect.right + dx).coerceIn(cropRect.left + minSizePx, maxW)
                    val newBottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minSizePx, maxH)
                    cropRect.right = newRight
                    cropRect.bottom = newBottom
                }

                TouchMode.EDGE_TOP -> {
                    cropRect.top = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minSizePx)
                }

                TouchMode.EDGE_BOTTOM -> {
                    cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minSizePx, maxH)
                }

                TouchMode.EDGE_LEFT -> {
                    cropRect.left = (cropRect.left + dx).coerceIn(0f, cropRect.right - minSizePx)
                }

                TouchMode.EDGE_RIGHT -> {
                    cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minSizePx, maxW)
                }

                TouchMode.NONE -> {}
            }
        }

        private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dX = x1 - x2
            val dY = y1 - y2
            return kotlin.math.sqrt(dX * dX + dY * dY)
        }
    }
}
