package com.antigravity.translator.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.antigravity.translator.R

/**
 * Modern 2-Column Grid Floating Action Menu (Launcher Card Style).
 *
 * Renders as an independent WindowManager overlay window anchored dynamically
 * to the left or right of FloatingBubbleView depending on screen edges.
 * Contains native vector icons and zero emojis.
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class FloatingMenuView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onTranslateClicked: () -> Unit,
    private val onCopyTextClicked: () -> Unit,
    private val onToggleModeClicked: (isManual: Boolean) -> Unit,
    private val onCropAdjustClicked: () -> Unit = {},
    private val onSettingsClicked: () -> Unit,
    private val onCloseClicked: () -> Unit,
    initialIsManualMode: Boolean = true
) {
    private var isManualMode: Boolean = initialIsManualMode
    private var isAttached: Boolean = false
    private var lastDismissedTime: Long = 0L
    private var menuContainer: FrameLayout? = null
    private val density = context.resources.displayMetrics.density

    private var modeSquircle: FrameLayout? = null
    private var modeLabel: TextView? = null

    private val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    fun isShowing(): Boolean = isAttached

    fun wasRecentlyDismissed(): Boolean {
        return (System.currentTimeMillis() - lastDismissedTime) < 400L
    }

    fun show(bubbleX: Int, bubbleY: Int, bubbleSizePx: Int) {
        if (isAttached) {
            dismiss()
            return
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val estMenuWidth = (204 * density).toInt()
        val estMenuHeight = (250 * density).toInt()

        // Determine whether to place menu to the RIGHT or LEFT of the bubble
        // Matches screenshot 1 (bubble left -> menu right) & screenshot 2 (bubble right -> menu left)
        val marginPx = (8 * density).toInt()
        val menuX = if (bubbleX + bubbleSizePx + estMenuWidth + marginPx <= screenWidth) {
            // Bubble is on left half: place menu to the RIGHT of the bubble
            bubbleX + bubbleSizePx + marginPx
        } else {
            // Bubble is on right half: place menu to the LEFT of the bubble
            kotlin.math.max(marginPx, bubbleX - estMenuWidth - marginPx)
        }

        // Align vertically centered relative to bubble, clamped within screen margins
        val desiredY = bubbleY + (bubbleSizePx / 2) - (estMenuHeight / 2)
        val menuY = desiredY.coerceIn(
            (32 * density).toInt(),
            kotlin.math.max((32 * density).toInt(), screenHeight - estMenuHeight - (48 * density).toInt())
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

        // Main 2-Column Launcher Card
        val cardWidthPx = (200 * density).toInt()
        val menuCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(cardWidthPx, FrameLayout.LayoutParams.WRAP_CONTENT)
            setPadding(
                (14 * density).toInt(),
                (16 * density).toInt(),
                (14 * density).toInt(),
                (16 * density).toInt()
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F020242D"))
                cornerRadius = 24 * density
                setStroke((1 * density).toInt(), Color.parseColor("#26FFFFFF"))
            }
            elevation = 24f

            // Enter animation
            scaleX = 0.88f
            scaleY = 0.88f
            alpha = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(150)
                .setInterpolator(OvershootInterpolator(1.15f))
                .start()
        }

        val itemWidthPx = (82 * density).toInt()
        val horizontalGapPx = (10 * density).toInt()
        val verticalGapPx = (12 * density).toInt()

        // -------------------------------------------------------------
        // ROW 1: "Traducción global" (Blue) & "Copiar texto" (Pink)
        // -------------------------------------------------------------
        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val translateItem = createGridItem(
            label = "Traducción\nglobal",
            iconRes = R.drawable.ic_menu_translate,
            squircleColor = Color.parseColor("#2563EB"),
            itemWidthPx = itemWidthPx,
            onClick = onTranslateClicked
        )

        val copyTextItem = createGridItem(
            label = "Copiar\ntexto",
            iconRes = R.drawable.ic_menu_copy,
            squircleColor = Color.parseColor("#E11D48"),
            itemWidthPx = itemWidthPx,
            onClick = onCopyTextClicked
        )

        row1.addView(translateItem)
        row1.addView(createSpacer(horizontalGapPx))
        row1.addView(copyTextItem)

        // -------------------------------------------------------------
        // ROW 2: "Traducción automática" (Green/Amber) & "Ajustes" (Slate)
        // -------------------------------------------------------------
        val row2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = verticalGapPx
            }
        }

        val (autoItem, squircleView, labelView) = createToggleModeItem(
            itemWidthPx = itemWidthPx,
            onClick = {
                isManualMode = !isManualMode
                updateModeVisuals()
                onToggleModeClicked(isManualMode)
            }
        )
        modeSquircle = squircleView
        modeLabel = labelView
        updateModeVisuals()

        val settingsItem = createGridItem(
            label = "Ajustes",
            iconRes = R.drawable.ic_menu_settings,
            squircleColor = Color.parseColor("#475569"),
            itemWidthPx = itemWidthPx,
            onClick = onSettingsClicked
        )

        row2.addView(autoItem)
        row2.addView(createSpacer(horizontalGapPx))
        row2.addView(settingsItem)

        // -------------------------------------------------------------
        // ROW 3: "Ajustar recorte" (Cyan #0EA5E9) & "Detener servicio" (Red)
        // -------------------------------------------------------------
        val row3 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = verticalGapPx
            }
        }

        val cropItem = createGridItem(
            label = "Ajustar\nrecorte",
            iconRes = R.drawable.ic_menu_crop,
            squircleColor = Color.parseColor("#0EA5E9"),
            itemWidthPx = itemWidthPx,
            onClick = {
                dismiss()
                onCropAdjustClicked()
            }
        )

        val closeItem = createGridItem(
            label = "Detener\nservicio",
            iconRes = R.drawable.ic_menu_close,
            squircleColor = Color.parseColor("#DC2626"),
            itemWidthPx = itemWidthPx,
            onClick = onCloseClicked
        )

        row3.addView(cropItem)
        row3.addView(createSpacer(horizontalGapPx))
        row3.addView(closeItem)

        menuCard.addView(row1)
        menuCard.addView(row2)
        menuCard.addView(row3)

        root.addView(menuCard)
        menuContainer = root

        try {
            windowManager.addView(root, params)
            isAttached = true
        } catch (e: Exception) {
            // Guard against racing WindowManager states
        }
    }

    fun dismiss() {
        if (!isAttached) return
        lastDismissedTime = System.currentTimeMillis()
        menuContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
            menuContainer = null
            isAttached = false
            modeSquircle = null
            modeLabel = null
        }
    }

    fun setMode(manual: Boolean) {
        isManualMode = manual
        updateModeVisuals()
    }

    private fun updateModeVisuals() {
        modeLabel?.text = if (isManualMode) "Modo\nmanual" else "Traducción\nautomática"
        val color = if (isManualMode) Color.parseColor("#F59E0B") else Color.parseColor("#10B981")
        (modeSquircle?.background as? GradientDrawable)?.setColor(color)
    }

    private fun createSpacer(widthPx: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(widthPx, 1)
        }
    }

    private fun createGridItem(
        label: String,
        iconRes: Int,
        squircleColor: Int,
        itemWidthPx: Int,
        onClick: () -> Unit
    ): View {
        val itemLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(itemWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
        }

        val squircleSize = (48 * density).toInt()
        val squircle = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(squircleSize, squircleSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            background = GradientDrawable().apply {
                setColor(squircleColor)
                cornerRadius = 14 * density
            }
            elevation = 4f
        }

        val iconView = ImageView(context).apply {
            val iconSize = (24 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER
            }
            setImageDrawable(ContextCompat.getDrawable(context, iconRes))
            setColorFilter(Color.WHITE)
        }
        squircle.addView(iconView)

        val labelView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (30 * density).toInt()
            ).apply {
                topMargin = (5 * density).toInt()
            }
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        itemLayout.addView(squircle)
        itemLayout.addView(labelView)

        // Fluid tactile feedback animation & delayed execution
        itemLayout.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.85f).setDuration(60).start()
                    false
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(80).start()
                    v.postDelayed({
                        dismiss()
                        onClick()
                    }, 100)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(80).start()
                    false
                }
                else -> false
            }
        }

        return itemLayout
    }

    private fun createToggleModeItem(
        itemWidthPx: Int,
        onClick: () -> Unit
    ): Triple<View, FrameLayout, TextView> {
        val itemLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(itemWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
        }

        val squircleSize = (48 * density).toInt()
        val squircle = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(squircleSize, squircleSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            background = GradientDrawable().apply {
                setColor(if (isManualMode) Color.parseColor("#F59E0B") else Color.parseColor("#10B981"))
                cornerRadius = 14 * density
            }
            elevation = 4f
        }

        val iconView = ImageView(context).apply {
            val iconSize = (24 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER
            }
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_menu_sync))
            setColorFilter(Color.WHITE)
        }
        squircle.addView(iconView)

        val labelView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (30 * density).toInt()
            ).apply {
                topMargin = (5 * density).toInt()
            }
            text = if (isManualMode) "Modo\nmanual" else "Traducción\nautomática"
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        itemLayout.addView(squircle)
        itemLayout.addView(labelView)

        itemLayout.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.85f).setDuration(60).start()
                    false
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(80).start()
                    v.postDelayed({
                        onClick()
                    }, 100)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(80).start()
                    false
                }
                else -> false
            }
        }

        return Triple(itemLayout, squircle, labelView)
    }
}
