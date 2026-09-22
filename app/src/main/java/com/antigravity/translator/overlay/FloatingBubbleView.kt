package com.antigravity.translator.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Outline
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.antigravity.translator.R
import com.antigravity.translator.domain.model.ServiceState

/**
 * Decoupled Floating Action Button (FAB).
 *
 * Exclusively responsible for the circular handle.
 * Strictly maintains a fixed size (52dp x 52dp) and constant coordinates (x, y).
 * Never resizes, flickers, or shifts when the menu is opened or closed.
 * Plays the animated GIF avatar with circular hardware-accelerated clipping.
 * Supports quick gestures:
 * - Single Tap: Opens the HUD launcher menu.
 * - Double Tap: Immediately triggers translation without opening menus.
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingBubbleView(
    context: Context,
    private val windowManager: WindowManager,
    val windowLayoutParams: WindowManager.LayoutParams,
    private val onBubbleSingleTap: (bubbleX: Int, bubbleY: Int) -> Unit,
    private val onBubbleDoubleTap: () -> Unit
) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    val bubbleSizePx = (52 * density).toInt()

    private val gestureHandler = Handler(Looper.getMainLooper())
    private var tapCount = 0
    private val doubleTapTimeoutMs = 260L
    private var pendingSingleTapRunnable: Runnable? = null

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging: Boolean = false

    private val circleIcon: ImageView
    private val borderOverlay: View

    init {
        // Dark circular base with shadow elevation
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#1A1A24"))
        }
        elevation = 14f

        val bubbleSize = bubbleSizePx

        // Circular clipped ImageView displaying the animated Miku GIF
        circleIcon = ImageView(context).apply {
            layoutParams = LayoutParams(bubbleSize, bubbleSize)
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
        }

        // Circular border ring overlay for clean visual separation
        borderOverlay = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke((2 * density).toInt(), Color.parseColor("#99FFFFFF"))
            }
            isClickable = false
            isFocusable = false
        }

        addView(circleIcon)
        addView(borderOverlay)

        loadAnimatedGif()
        setupDragAndClickBehavior()
    }

    private fun loadAnimatedGif() {
        post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(resources, R.drawable.miku_bubble)
                    val drawable = ImageDecoder.decodeDrawable(source)
                    circleIcon.setImageDrawable(drawable)
                    if (drawable is AnimatedImageDrawable) {
                        drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        drawable.start()
                    }
                } else {
                    circleIcon.setImageResource(R.drawable.miku_bubble)
                }
            } catch (e: Throwable) {
                Log.e("FloatingBubbleView", "Error loading animated GIF", e)
                circleIcon.setImageResource(android.R.drawable.ic_menu_search)
                circleIcon.setColorFilter(Color.WHITE)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (circleIcon.drawable as? AnimatedImageDrawable)?.start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelPendingSingleTap()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (circleIcon.drawable as? AnimatedImageDrawable)?.stop()
        }
    }

    private fun cancelPendingSingleTap() {
        pendingSingleTapRunnable?.let { gestureHandler.removeCallbacks(it) }
        pendingSingleTapRunnable = null
        tapCount = 0
    }

    private fun setupDragAndClickBehavior() {
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = windowLayoutParams.x
                    initialY = windowLayoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) {
                        if (!isDragging) {
                            isDragging = true
                            cancelPendingSingleTap()
                        }
                    }

                    if (isDragging) {
                        windowLayoutParams.x = initialX + dx
                        windowLayoutParams.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(this, windowLayoutParams)
                        } catch (e: Exception) {
                            // Ignored if detached
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        handleTapGesture()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelPendingSingleTap()
                    isDragging = false
                    false
                }
                else -> false
            }
        }
    }

    private fun handleTapGesture() {
        tapCount++
        if (tapCount == 1) {
            val tapX = windowLayoutParams.x
            val tapY = windowLayoutParams.y
            val runnable = Runnable {
                tapCount = 0
                pendingSingleTapRunnable = null
                onBubbleSingleTap(tapX, tapY)
            }
            pendingSingleTapRunnable = runnable
            gestureHandler.postDelayed(runnable, doubleTapTimeoutMs)
        } else if (tapCount >= 2) {
            cancelPendingSingleTap()
            animateDoubleTapPulse()
            onBubbleDoubleTap()
        }
    }

    private fun animateDoubleTapPulse() {
        animate()
            .scaleX(1.22f)
            .scaleY(1.22f)
            .setDuration(110)
            .withEndAction {
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(110)
                    .start()
            }
            .start()
    }

    fun updateState(state: ServiceState) {
        val strokeColor = when (state) {
            ServiceState.RUNNING -> Color.parseColor("#99FFFFFF")
            ServiceState.PAUSED -> Color.parseColor("#B3FFD600")
            ServiceState.STOPPED -> Color.parseColor("#B3FF5252")
        }
        (borderOverlay.background as? GradientDrawable)?.setStroke((2 * density).toInt(), strokeColor)
    }
}
