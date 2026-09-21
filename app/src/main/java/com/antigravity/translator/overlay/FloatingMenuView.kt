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
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F01F1F27"))
                cornerRadius = 40f
                setStroke(2, Color.parseColor("#4DFFFFFF"))
            }
            elevation = 20f
        }

        // 1. "TRADUCIR"
        val translateButton = TextView(context).apply {
            text = "TRADUCIR"
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            setPadding(16, 10, 16, 10)
            background = createRoundedButton(Color.parseColor("#6200EE"), 24f)
            setOnClickListener {
                dismiss()
                onTranslateClicked()
            }
        }

        // 2. "RECORTAR" (Snip Area)
        val snipButton = TextView(context).apply {
            text = "✂ RECORTAR"
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            setPadding(14, 10, 14, 10)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8
            }
            layoutParams = p
            background = createRoundedButton(Color.parseColor("#00838F"), 24f)
            setOnClickListener {
                dismiss()
                onRegionSnipClicked()
            }
        }

        // 3. "LUPA" (Magnifier Bubble)
        val magnifierButton = TextView(context).apply {
            text = "🔍 LUPA"
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            setPadding(14, 10, 14, 10)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8
            }
            layoutParams = p
            background = createRoundedButton(Color.parseColor("#3949AB"), 24f)
            setOnClickListener {
                dismiss()
                onToggleMagnifierClicked()
            }
        }

        // 4. "LIMPIAR"
        val clearButton = TextView(context).apply {
            text = "LIMPIAR"
            textSize = 12f
            setTextColor(Color.parseColor("#CFD8DC"))
            isClickable = true
            setPadding(12, 10, 12, 10)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8
            }
            layoutParams = p
            background = createRoundedButton(Color.parseColor("#26FFFFFF"), 24f)
            setOnClickListener {
                dismiss()
                onClearClicked()
            }
        }

        // 5. "MANUAL / AUTO"
        modeButton = TextView(context).apply {
            text = if (isManualMode) "MANUAL" else "AUTO"
            textSize = 11f
            setTextColor(if (isManualMode) Color.parseColor("#00E676") else Color.parseColor("#FFD600"))
            isClickable = true
            setPadding(12, 10, 12, 10)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8
            }
            layoutParams = p
            background = createRoundedButton(Color.parseColor("#1FFFFFFF"), 24f)
            setOnClickListener {
                isManualMode = !isManualMode
                text = if (isManualMode) "MANUAL" else "AUTO"
                setTextColor(if (isManualMode) Color.parseColor("#00E676") else Color.parseColor("#FFD600"))
                onToggleModeClicked(isManualMode)
            }
        }

        // 6. Close "✕"
        val closeButton = TextView(context).apply {
            text = "✕"
            textSize = 14f
            setTextColor(Color.parseColor("#FF5252"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            setPadding(14, 10, 14, 10)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8
            }
            layoutParams = p
            background = createRoundedButton(Color.parseColor("#26FF5252"), 24f)
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
            modeButton.setTextColor(if (manual) Color.parseColor("#00E676") else Color.parseColor("#FFD600"))
        }
    }

    private fun createRoundedButton(bgColor: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = radius
        }
    }
}
