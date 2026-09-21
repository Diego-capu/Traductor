package com.antigravity.translator.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.antigravity.translator.domain.model.ServiceState

/**
 * Compact circular floating action button (FAB) that expands into a translation menu on tap.
 *
 * Designed specifically to avoid cluttering or invading the screen during manga/comic reading:
 * - Idle/default: A small 50dp floating circle badge that can be dragged anywhere.
 * - On tap: Expands horizontally into the action menu (TRADUCIR, LIMPIAR, MODO, ✕).
 * - After tapping TRADUCIR or LIMPIAR: Automatically collapses back to the circle!
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class FloatingControlView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val onTranslateNow: () -> Unit,
    private val onClearOverlay: () -> Unit,
    private val onToggleMode: (isManual: Boolean) -> Unit,
    private val onStateToggle: (newState: ServiceState) -> Unit,
    private val onStopClicked: () -> Unit,
    initialIsManualMode: Boolean = true
) : FrameLayout(context) {

    private var isManualMode: Boolean = initialIsManualMode
    private var isExpanded: Boolean = false // Default: collapsed circle!
    private var currentState: ServiceState = ServiceState.RUNNING

    // Drag tracking
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging: Boolean = false

    private val containerLayout: LinearLayout
    private val circleButton: FrameLayout
    private val circleIcon: ImageView
    private val statusIndicator: View
    private val expandedControls: LinearLayout
    private val translateButton: TextView
    private val clearButton: TextView
    private val modeButton: TextView
    private val closeButton: TextView

    private val density = context.resources.displayMetrics.density
    private val circleSizePx = (50 * density).toInt()

    init {
        // Outer horizontal pill container
        containerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4, 4, 4, 4)
            elevation = 16f
        }

        // 1. Floating Circle Button (Always visible, draggable & clickable)
        circleButton = FrameLayout(context).apply {
            val lp = LinearLayout.LayoutParams(circleSizePx, circleSizePx)
            layoutParams = lp
            background = createCircleBackground(Color.parseColor("#6200EE"))
            elevation = 12f
        }

        circleIcon = ImageView(context).apply {
            val iconSize = (26 * density).toInt()
            val lp = LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = lp
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(Color.WHITE)
        }

        // Small green/amber status dot on the circle edge
        statusIndicator = View(context).apply {
            val dotSize = (10 * density).toInt()
            val lp = LayoutParams(dotSize, dotSize).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = (3 * density).toInt()
                marginEnd = (3 * density).toInt()
            }
            layoutParams = lp
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676")) // Active green
                setStroke(2, Color.WHITE)
            }
        }

        circleButton.addView(circleIcon)
        circleButton.addView(statusIndicator)

        // 2. Expanded Menu Controls (Hidden by default in collapsed state)
        expandedControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 6, 12, 6)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1F1F27"))
                cornerRadius = 40f
                setStroke(2, Color.parseColor("#4DFFFFFF"))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8
            }
            layoutParams = lp
            visibility = View.GONE // Start collapsed!
        }

        // Action: "TRADUCIR"
        translateButton = TextView(context).apply {
            text = "TRADUCIR"
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isClickable = true
            setPadding(18, 10, 18, 10)
            background = createRoundedButton(Color.parseColor("#6200EE"), 24f)
            setOnClickListener {
                collapseMenu()
                onTranslateNow()
            }
        }

        // Action: "LIMPIAR"
        clearButton = TextView(context).apply {
            text = "LIMPIAR"
            textSize = 12f
            setTextColor(Color.parseColor("#CFD8DC"))
            isClickable = true
            setPadding(14, 10, 14, 10)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8
            }
            layoutParams = lp
            background = createRoundedButton(Color.parseColor("#26FFFFFF"), 24f)
            setOnClickListener {
                collapseMenu()
                onClearOverlay()
            }
        }

        // Action: "MANUAL / AUTO"
        modeButton = TextView(context).apply {
            text = if (isManualMode) "MANUAL" else "AUTO"
            textSize = 11f
            setTextColor(if (isManualMode) Color.parseColor("#00E676") else Color.parseColor("#FFD600"))
            isClickable = true
            setPadding(12, 10, 12, 10)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8
            }
            layoutParams = lp
            background = createRoundedButton(Color.parseColor("#1FFFFFFF"), 24f)
            setOnClickListener {
                isManualMode = !isManualMode
                text = if (isManualMode) "MANUAL" else "AUTO"
                setTextColor(if (isManualMode) Color.parseColor("#00E676") else Color.parseColor("#FFD600"))
                onToggleMode(isManualMode)
            }
        }

        // Action: Close "✕"
        closeButton = TextView(context).apply {
            text = "✕"
            textSize = 14f
            setTextColor(Color.parseColor("#FF5252"))
            isClickable = true
            setPadding(14, 10, 14, 10)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 8
            }
            layoutParams = lp
            background = createRoundedButton(Color.parseColor("#26FF5252"), 24f)
            setOnClickListener {
                onStopClicked()
            }
        }

        expandedControls.addView(translateButton)
        expandedControls.addView(clearButton)
        expandedControls.addView(modeButton)
        expandedControls.addView(closeButton)

        containerLayout.addView(circleButton)
        containerLayout.addView(expandedControls)
        addView(containerLayout)

        setupDragAndClickBehavior()
    }

    private fun setupDragAndClickBehavior() {
        circleButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) {
                        isDragging = true
                    }

                    if (isDragging) {
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(this, layoutParams)
                        } catch (e: Exception) {
                            // Detached guard
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Clean tap on the circle button: toggle expand / collapse menu
                        toggleMenu()
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun toggleMenu() {
        if (isExpanded) {
            collapseMenu()
        } else {
            expandMenu()
        }
    }

    fun expandMenu() {
        isExpanded = true
        expandedControls.visibility = View.VISIBLE
        updateLayoutDimensions()
    }

    fun collapseMenu() {
        isExpanded = false
        expandedControls.visibility = View.GONE
        updateLayoutDimensions()
    }

    private fun updateLayoutDimensions() {
        try {
            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            windowManager.updateViewLayout(this, layoutParams)
        } catch (e: Exception) {
            // Guard
        }
    }

    fun updateState(state: ServiceState) {
        currentState = state
        val dotColor = when (state) {
            ServiceState.RUNNING -> Color.parseColor("#00E676") // Green
            ServiceState.PAUSED -> Color.parseColor("#FFD600")  // Amber
            ServiceState.STOPPED -> Color.parseColor("#FF5252") // Red
        }
        (statusIndicator.background as? GradientDrawable)?.setColor(dotColor)
    }

    fun setMode(manual: Boolean) {
        isManualMode = manual
        modeButton.text = if (manual) "MANUAL" else "AUTO"
        modeButton.setTextColor(if (manual) Color.parseColor("#00E676") else Color.parseColor("#FFD600"))
    }

    private fun createCircleBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(3, Color.parseColor("#80FFFFFF"))
        }
    }

    private fun createRoundedButton(bgColor: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = radius
        }
    }
}
