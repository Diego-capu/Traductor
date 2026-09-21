package com.antigravity.translator.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Decoupled Floating Action Menu.
 *
 * Renders as a separate WindowManager overlay window anchored adjacent to the FloatingBubbleView.
 * Tapping outside or selecting an action automatically dismisses this menu without modifying
 * the bubble's position or layout bounds.
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class FloatingMenuView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onTranslateClicked: () -> Unit,
    private val onRegionSnipClicked: () -> Unit,
    private val onToggleMagnifierClicked: () -> Unit,
    private val onClearClicked: () -> Unit,
    private val onToggleModeClicked: (isManual: Boolean) -> Unit,
    private val onCloseClicked: () -> Unit,
    initialIsManualMode: Boolean = true
) {
    private var isManualMode: Boolean = initialIsManualMode
    private var isAttached: Boolean = false
    private var menuContainer: FrameLayout? = null
    private val density = context.resources.displayMetrics.density

    private lateinit var modeButton: TextView

    private val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    fun isShowing(): Boolean = isAttached

    fun show(bubbleX: Int, bubbleY: Int, bubbleSizePx: Int) {
        if (isAttached) {
            dismiss()
            return
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Estimated menu width: ~350dp, height: ~52dp
        val estMenuWidth = (350 * density).toInt().coerceAtMost((screenWidth * 0.92).toInt())
        val estMenuHeight = (52 * density).toInt()

        // Determine whether to place menu to the RIGHT or LEFT of the bubble
        val menuX = if (bubbleX + bubbleSizePx + estMenuWidth < screenWidth - (10 * density).toInt()) {
            // Place to the right
            bubbleX + bubbleSizePx + (8 * density).toInt()
        } else {
            // Place to the left
            kotlin.math.max((10 * density).toInt(), bubbleX - estMenuWidth - (8 * density).toInt())
        }

        // Align vertically with bubble, clamping within screen bounds
        val desiredY = bubbleY + (bubbleSizePx / 2) - (estMenuHeight / 2)
        val menuY = desiredY.coerceIn(
            (40 * density).toInt(),
            screenHeight - estMenuHeight - (50 * density).toInt()
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = menuX
            y = menuY
        }

        val root = FrameLayout(context).apply {
            // Dismiss when tapping outside the menu card
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    dismiss()
                    true
                } else {
                    false
                }
            }
        }

        val scrollView = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
        }

        val menuCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            // Glassmorphism: semi-transparent dark charcoal with crisp 1.5dp #39C5BB border
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6171B24"))
                cornerRadius = 16f * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#39C5BB"))
            }
            elevation = 22f
        }

        // 1. "TRADUCIR" (Primary Action: Vocaloid Teal CTA)
        val translateButton = TextView(context).apply {
            text = "TRADUCIR"
            textSize = 12f
            setTextColor(Color.parseColor("#0B1326")) // Dark obsidian text
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            setPadding((16 * density).toInt(), (9 * density).toInt(), (16 * density).toInt(), (9 * density).toInt())
            background = createRoundedButton(Color.parseColor("#39C5BB"), 14f * density)
            setOnClickListener {
                dismiss()
                onTranslateClicked()
            }
        }

        // 2. "RECORTAR" (Snip Area: Teal Glass)
        val snipButton = TextView(context).apply {
            text = "✂ RECORTAR"
            textSize = 12f
            setTextColor(Color.parseColor("#39C5BB"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            setPadding((14 * density).toInt(), (9 * density).toInt(), (14 * density).toInt(), (9 * density).toInt())
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (8 * density).toInt()
            }
            layoutParams = p
            background = createRoundedButton(
                Color.parseColor("#2639C5BB"),
                14f * density,
                Color.parseColor("#39C5BB"),
                (1f * density).toInt()
            )
            setOnClickListener {
                dismiss()
                onRegionSnipClicked()
            }
        }

        // 3. "LUPA" (Magnifier: Miku Accent Magenta)
        val magnifierButton = TextView(context).apply {
            text = "🔍 LUPA"
            textSize = 12f
            setTextColor(Color.parseColor("#E040FB"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            setPadding((14 * density).toInt(), (9 * density).toInt(), (14 * density).toInt(), (9 * density).toInt())
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (8 * density).toInt()
            }
            layoutParams = p
            background = createRoundedButton(
                Color.parseColor("#26E040FB"),
                14f * density,
                Color.parseColor("#E040FB"),
                (1f * density).toInt()
            )
            setOnClickListener {
                dismiss()
                onToggleMagnifierClicked()
            }
        }

        // 4. "LIMPIAR" (Neutral Glass)
        val clearButton = TextView(context).apply {
            text = "LIMPIAR"
            textSize = 12f
            setTextColor(Color.parseColor("#8F9BA8"))
            isClickable = true
            setPadding((12 * density).toInt(), (9 * density).toInt(), (12 * density).toInt(), (9 * density).toInt())
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (8 * density).toInt()
            }
            layoutParams = p
            background = createRoundedButton(
                Color.parseColor("#1AFFFFFF"),
                14f * density,
                Color.parseColor("#338F9BA8"),
                (1f * density).toInt()
            )
            setOnClickListener {
                dismiss()
                onClearClicked()
            }
        }

        // 5. "MANUAL / AUTO"
        modeButton = TextView(context).apply {
            text = if (isManualMode) "MANUAL" else "AUTO"
            textSize = 11f
            setTextColor(if (isManualMode) Color.parseColor("#39C5BB") else Color.parseColor("#E040FB"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            setPadding((12 * density).toInt(), (9 * density).toInt(), (12 * density).toInt(), (9 * density).toInt())
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (8 * density).toInt()
            }
            layoutParams = p
            val strokeColor = if (isManualMode) Color.parseColor("#4D39C5BB") else Color.parseColor("#4DE040FB")
            background = createRoundedButton(Color.parseColor("#171B24"), 14f * density, strokeColor, (1f * density).toInt())
            setOnClickListener {
                isManualMode = !isManualMode
                text = if (isManualMode) "MANUAL" else "AUTO"
                val activeColor = if (isManualMode) Color.parseColor("#39C5BB") else Color.parseColor("#E040FB")
                val activeStroke = if (isManualMode) Color.parseColor("#4D39C5BB") else Color.parseColor("#4DE040FB")
                setTextColor(activeColor)
                background = createRoundedButton(Color.parseColor("#171B24"), 14f * density, activeStroke, (1f * density).toInt())
                onToggleModeClicked(isManualMode)
            }
        }

        // 6. Close "✕"
        val closeButton = TextView(context).apply {
            text = "✕"
            textSize = 13f
            setTextColor(Color.parseColor("#FF5252"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            setPadding((14 * density).toInt(), (9 * density).toInt(), (14 * density).toInt(), (9 * density).toInt())
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (8 * density).toInt()
            }
            layoutParams = p
            background = createRoundedButton(
                Color.parseColor("#26FF5252"),
                14f * density,
                Color.parseColor("#4DFF5252"),
                (1f * density).toInt()
            )
            setOnClickListener {
                dismiss()
                onCloseClicked()
            }
        }

        menuCard.addView(translateButton)
        menuCard.addView(snipButton)
        menuCard.addView(magnifierButton)
        menuCard.addView(clearButton)
        menuCard.addView(modeButton)
        menuCard.addView(closeButton)

        scrollView.addView(menuCard)
        root.addView(scrollView)
        menuContainer = root

        try {
            windowManager.addView(root, params)
            isAttached = true
        } catch (e: Exception) {
            // Guard
        }
    }

    fun dismiss() {
        if (!isAttached) return
        menuContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
            menuContainer = null
            isAttached = false
        }
    }

    fun setMode(manual: Boolean) {
        isManualMode = manual
        if (::modeButton.isInitialized) {
            modeButton.text = if (manual) "MANUAL" else "AUTO"
            val activeColor = if (manual) Color.parseColor("#39C5BB") else Color.parseColor("#E040FB")
            val activeStroke = if (manual) Color.parseColor("#4D39C5BB") else Color.parseColor("#4DE040FB")
            modeButton.setTextColor(activeColor)
            modeButton.background = createRoundedButton(Color.parseColor("#171B24"), 14f * density, activeStroke, (1f * density).toInt())
        }
    }

    private fun createRoundedButton(
        bgColor: Int,
        radius: Float,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidth: Int = 0
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = radius
            if (strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }
}
