package com.antigravity.translator.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Outline
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingBubbleView(
    context: Context,
    private val windowManager: WindowManager,
    val windowLayoutParams: WindowManager.LayoutParams,
    private val onBubbleClicked: (bubbleX: Int, bubbleY: Int) -> Unit
) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    val bubbleSizePx = (52 * density).toInt()

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging: Boolean = false

    private val circleIcon: ImageView
    private val borderOverlay: View
    private val statusIndicator: View

    init {
        // Deep cybernetic charcoal circular base with elevation
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#12131A"))
        }
        elevation = 18f

        // Animated GIF ImageView with circular hardware-accelerated clipping
        circleIcon = ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
        }

        // Circular badge with glowing Miku teal border (#39C5BB)
        borderOverlay = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke((2.5f * density).toInt(), Color.parseColor("#39C5BB"))
            }
            isClickable = false
            isFocusable = false
        }

        // Miku telemetry status indicator dot on top-right edge
        statusIndicator = View(context).apply {
            val dotSize = (12 * density).toInt()
            val p = LayoutParams(dotSize, dotSize).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = (2 * density).toInt()
                marginEnd = (2 * density).toInt()
            }
            layoutParams = p
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#39C5BB")) // Signature Miku Teal
                setStroke((1.5f * density).toInt(), Color.parseColor("#12131A"))
            }
            elevation = 20f
            isClickable = false
            isFocusable = false
        }

        addView(circleIcon)
        addView(borderOverlay)
        addView(statusIndicator)

        loadAnimatedGif()
        setupDragAndClickBehavior()
    }

    private fun loadAnimatedGif() {
        post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(resources, R.drawable.miku_bubble)
                    // Efficient hardware-accelerated memory decoding to preserve battery & GPU bandwidth
                    val drawable = ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
                    }
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
                circleIcon.setColorFilter(Color.parseColor("#39C5BB"))
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (circleIcon.drawable as? AnimatedImageDrawable)?.stop()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val anim = circleIcon.drawable as? AnimatedImageDrawable
            if (visibility == View.VISIBLE) {
                anim?.start()
            } else {
                anim?.stop()
            }
        }
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
                        isDragging = true
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
                        // Clean tap without drag: open decoupled menu relative to this bubble's position
                        onBubbleClicked(windowLayoutParams.x, windowLayoutParams.y)
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun updateState(state: ServiceState) {
        val dotColor = when (state) {
            ServiceState.RUNNING -> Color.parseColor("#39C5BB") // Signature Miku Teal
            ServiceState.PAUSED -> Color.parseColor("#FFD600")  // Amber
            ServiceState.STOPPED -> Color.parseColor("#FF5252") // Red
        }
        (statusIndicator.background as? GradientDrawable)?.setColor(dotColor)
    }
}
