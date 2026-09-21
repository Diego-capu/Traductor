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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen interactive overlay for dragging and selecting a rectangular screen region (Snip Tool).
 *
 * Features:
 * - Hardware-accelerated 4-quadrant scrim surrounding the active selection.
 * - High-visibility neon border with corner anchor handles.
 * - Dynamic floating action chip bar (TRADUCIR / CANCELAR) anchored to the selection.
 * - Immediate touch passthrough restoration upon confirmation or cancellation.
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class RegionSelectionOverlayView(
    context: Context,
    private val onRegionSelected: (Rect) -> Unit,
    private val onDismissed: () -> Unit
) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDragging = false
    private var hasSelection = false

    private val selectedRect = Rect()

    // Paints
    private val scrimPaint = Paint().apply {
        color = Color.parseColor("#B30B0F19") // 70% deep cyber scrim
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#39C5BB") // Signature Vocaloid Teal
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#39C5BB")
        style = Paint.Style.STROKE
        strokeWidth = 4.5f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#39C5BB")
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 2f, Color.BLACK)
    }

    private val actionContainer: LinearLayout

    init {
        setWillNotDraw(false)

        // Top instruction badge
        val instructionBadge = TextView(context).apply {
            text = "Arrastra sobre la pantalla para recortar el área a traducir"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6171B24"))
                cornerRadius = 14f * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#4D39C5BB"))
            }
            val p = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = (48 * density).toInt()
            }
            layoutParams = p
        }
        addView(instructionBadge)

        // Floating Action Buttons (Confirm / Cancel)
        actionContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
            // Frosted cyberpunk glassmorphism container
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6171B24"))
                cornerRadius = 16f * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#39C5BB"))
            }
            elevation = 20f
        }

        val confirmBtn = TextView(context).apply {
            text = "✓ TRADUCIR"
            textSize = 12f
            setTextColor(Color.parseColor("#0B1326")) // Dark obsidian text
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((16 * density).toInt(), (9 * density).toInt(), (16 * density).toInt(), (9 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#39C5BB"))
                cornerRadius = 14f * density
            }
            setOnClickListener {
                if (hasSelection && selectedRect.width() > 20 && selectedRect.height() > 20) {
                    onRegionSelected(Rect(selectedRect))
                }
            }
        }

        val cancelBtn = TextView(context).apply {
            text = "✕ CANCELAR"
            textSize = 12f
            setTextColor(Color.parseColor("#FF5252"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((14 * density).toInt(), (9 * density).toInt(), (14 * density).toInt(), (9 * density).toInt())
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (8 * density).toInt()
            }
            layoutParams = p
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26FF5252"))
                cornerRadius = 14f * density
                setStroke((1f * density).toInt(), Color.parseColor("#4DFF5252"))
            }
            setOnClickListener {
                onDismissed()
            }
        }

        actionContainer.addView(confirmBtn)
        actionContainer.addView(cancelBtn)
        addView(actionContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        setupTouchHandling()
    }

    private fun setupTouchHandling() {
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    currentX = event.x
                    currentY = event.y
                    isDragging = true
                    hasSelection = false
                    actionContainer.visibility = View.GONE
                    updateRect()
                    invalidate()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        currentX = event.x
                        currentY = event.y
                        updateRect()
                        invalidate()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    updateRect()

                    val minSize = (24 * density).toInt()
                    if (selectedRect.width() >= minSize && selectedRect.height() >= minSize) {
                        hasSelection = true
                        positionActionToolbar()
                    } else {
                        hasSelection = false
                        selectedRect.setEmpty()
                    }
                    invalidate()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateRect() {
        val left = min(startX, currentX).toInt().coerceAtLeast(0)
        val top = min(startY, currentY).toInt().coerceAtLeast(0)
        val right = max(startX, currentX).toInt().coerceAtMost(width)
        val bottom = max(startY, currentY).toInt().coerceAtMost(height)
        selectedRect.set(left, top, right, bottom)
    }

    private fun positionActionToolbar() {
        actionContainer.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val toolbarWidth = actionContainer.measuredWidth
        val toolbarHeight = actionContainer.measuredHeight

        // Center horizontally with the selected box
        var targetX = selectedRect.centerX() - (toolbarWidth / 2)
        targetX = targetX.coerceIn((12 * density).toInt(), width - toolbarWidth - (12 * density).toInt())

        // Prefer placing below the selection; if close to bottom, place above
        val margin = (12 * density).toInt()
        var targetY = selectedRect.bottom + margin
        if (targetY + toolbarHeight > height - (40 * density).toInt()) {
            targetY = selectedRect.top - toolbarHeight - margin
        }
        targetY = targetY.coerceIn((60 * density).toInt(), height - toolbarHeight - (20 * density).toInt())

        val p = actionContainer.layoutParams as LayoutParams
        p.gravity = Gravity.TOP or Gravity.START
        p.leftMargin = targetX
        p.topMargin = targetY
        actionContainer.layoutParams = p
        actionContainer.visibility = View.VISIBLE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (!hasSelection && !isDragging) {
            // Full scrim
            canvas.drawRect(0f, 0f, w, h, scrimPaint)
            return
        }

        val rLeft = selectedRect.left.toFloat()
        val rTop = selectedRect.top.toFloat()
        val rRight = selectedRect.right.toFloat()
        val rBottom = selectedRect.bottom.toFloat()

        // 4 Scrim Quadrants around the clear cutout (zero offscreen buffer allocation)
        // 1. Top rect
        if (rTop > 0) canvas.drawRect(0f, 0f, w, rTop, scrimPaint)
        // 2. Bottom rect
        if (rBottom < h) canvas.drawRect(0f, rBottom, w, h, scrimPaint)
        // 3. Left rect
        if (rLeft > 0) canvas.drawRect(0f, rTop, rLeft, rBottom, scrimPaint)
        // 4. Right rect
        if (rRight < w) canvas.drawRect(rRight, rTop, w, rBottom, scrimPaint)

        // Bounding Rectangle Outline
        canvas.drawRect(rLeft, rTop, rRight, rBottom, borderPaint)

        // Corner Grip Handles
        val cornerLen = 16f * density
        // Top-Left
        canvas.drawLine(rLeft, rTop, rLeft + cornerLen, rTop, cornerPaint)
        canvas.drawLine(rLeft, rTop, rLeft, rTop + cornerLen, cornerPaint)
        // Top-Right
        canvas.drawLine(rRight, rTop, rRight - cornerLen, rTop, cornerPaint)
        canvas.drawLine(rRight, rTop, rRight, rTop + cornerLen, cornerPaint)
        // Bottom-Left
        canvas.drawLine(rLeft, rBottom, rLeft + cornerLen, rBottom, cornerPaint)
        canvas.drawLine(rLeft, rBottom, rLeft, rBottom - cornerLen, cornerPaint)
        // Bottom-Right
        canvas.drawLine(rRight, rBottom, rRight - cornerLen, rBottom, cornerPaint)
        canvas.drawLine(rRight, rBottom, rRight, rBottom - cornerLen, cornerPaint)

        // Size badge text above box
        if (selectedRect.width() > 60 && selectedRect.height() > 40) {
            val label = "${selectedRect.width()} × ${selectedRect.height()}"
            val textY = if (rTop > 24 * density) rTop - (8 * density) else rTop + (20 * density)
            canvas.drawText(label, (rLeft + rRight) / 2f, textY, textPaint)
        }
    }
}
