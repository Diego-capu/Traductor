package com.antigravity.translator.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.antigravity.translator.R

/**
 * Modern 2-Column Grid-Style Floating Action Menu (Miku_AI Cyberpunk HUD).
 *
 * Appears as an independent WindowManager overlay with a semi-transparent scrim (#800B0F19).
 * Tapping outside the card or selecting an action dismisses the menu smoothly without altering
 * the floating bubble FAB's position or state.
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class FloatingMenuView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onTranslateClicked: () -> Unit,
    private val onRegionSnipClicked: () -> Unit,
    private val onToggleMagnifierClicked: () -> Unit,
    private val onCopyTextClicked: () -> Unit,
    private val onToggleModeClicked: (isManual: Boolean) -> Unit,
    private val onSettingsOrPauseClicked: () -> Unit,
    private val onClearClicked: (() -> Unit)? = null,
    private val onCloseClicked: (() -> Unit)? = null,
    private val getSourceLanguage: (() -> String)? = null,
    private val getTargetLanguage: (() -> String)? = null,
    initialIsManualMode: Boolean = true
) {
    private var isManualMode: Boolean = initialIsManualMode
    private var isAttached: Boolean = false
    private var rootScrimView: FrameLayout? = null
    private val density = context.resources.displayMetrics.density

    // Dynamic references for Auto/Manual item
    private var autoModeLabel: TextView? = null
    private var autoModeSquircle: FrameLayout? = null
    private var autoModeIcon: ImageView? = null

    private val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    fun isShowing(): Boolean = isAttached

    /**
     * Displays the 2-column launcher card centered on screen over a translucent scrim.
     * Dimensions are clamped between 220dp and 260dp to accommodate scaled system fonts.
     */
    fun show(bubbleX: Int = 0, bubbleY: Int = 0, bubbleSizePx: Int = 0) {
        if (isAttached) {
            dismiss()
            return
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        // Dynamic width clamped to [220dp, 260dp] ensuring 2-line labels never clip
        val baseWidth = (240 * density).toInt()
        val minWidth = (220 * density).toInt()
        val maxWidth = (260 * density).toInt()
        val cardWidth = baseWidth.coerceIn(minWidth, maxWidth).coerceAtMost((screenWidth * 0.88).toInt())

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        // 1. Semi-transparent scrim backdrop (#800B0F19)
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#800B0F19"))
            setOnClickListener {
                dismiss()
            }
        }

        // 2. Card Container: Frosted deep slate glass (#F0171B24), 1.5dp #39C5BB border, 24dp radius
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F0171B24"))
                cornerRadius = 24f * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#39C5BB"))
            }
            setPadding(
                (16 * density).toInt(),
                (20 * density).toInt(),
                (16 * density).toInt(),
                (18 * density).toInt()
            )
            elevation = 32f
        }

        val cardParams = FrameLayout.LayoutParams(
            cardWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        root.addView(card, cardParams)

        // Header: ACCIONES HUD + MOTOR: MIKU-NPU
        card.addView(buildHeader())

        // Subtle spacer
        val topSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (14 * density).toInt()
            )
        }
        card.addView(topSpacer)

        // 3. Grid of 6 items (3 rows x 2 columns)
        // Row 1: Traducción Global & Traducción de Área
        val row1 = buildRow(
            createGridItem(
                title = "Traducción\nGlobal",
                iconRes = R.drawable.ic_menu_translate,
                tileColor = Color.parseColor("#2563EB"),
                action = onTranslateClicked
            ),
            createGridItem(
                title = "Traducción\nde Área",
                iconRes = R.drawable.ic_menu_crop,
                tileColor = Color.parseColor("#39C5BB"),
                action = onRegionSnipClicked
            )
        )
        card.addView(row1)

        // Row 2: Lupa Dinámica & Copiar Texto
        val row2 = buildRow(
            createGridItem(
                title = "Lupa\nDinámica",
                iconRes = R.drawable.ic_menu_magnifier,
                tileColor = Color.parseColor("#E040FB"),
                action = onToggleMagnifierClicked
            ),
            createGridItem(
                title = "Copiar\nTexto",
                iconRes = R.drawable.ic_menu_copy,
                tileColor = Color.parseColor("#E11D48"),
                action = onCopyTextClicked
            )
        )
        card.addView(row2)

        // Row 3: Traducción Automática & Configuración / Pausa
        val autoItem = createAutoModeItem()
        val settingsItem = createGridItem(
            title = "Configuración\n/ Pausa",
            iconRes = R.drawable.ic_menu_settings,
            tileColor = Color.parseColor("#475569"),
            action = onSettingsOrPauseClicked
        )
        val row3 = buildRow(autoItem, settingsItem)
        card.addView(row3)

        // Divider spacer
        val bottomSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (12 * density).toInt()
            )
        }
        card.addView(bottomSpacer)

        // Footer: Language pair & Close
        card.addView(buildFooter())

        rootScrimView = root

        try {
            windowManager.addView(root, windowParams)
            isAttached = true

            // Tactile Card entry bounce animation
            card.alpha = 0f
            card.scaleX = 0.88f
            card.scaleY = 0.88f
            card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(160)
                .setInterpolator(OvershootInterpolator(1.15f))
                .start()
        } catch (e: Exception) {
            // Guard
        }
    }

    private fun buildHeader(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            // Spark icon
            val sparkIcon = ImageView(context).apply {
                setImageResource(R.drawable.ic_menu_spark)
                setColorFilter(Color.parseColor("#39C5BB"))
                layoutParams = LinearLayout.LayoutParams(
                    (15 * density).toInt(),
                    (15 * density).toInt()
                )
            }
            addView(sparkIcon)

            // "ACCIONES HUD" title
            val headerTitle = TextView(context).apply {
                text = "ACCIONES HUD"
                textSize = 11f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                letterSpacing = 0.05f
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (6 * density).toInt()
                }
                layoutParams = p
            }
            addView(headerTitle)

            // Flexible spacer
            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            }
            addView(spacer)

            // Pill badge "MOTOR: MIKU-NPU"
            val pillBadge = TextView(context).apply {
                text = "MIKU-NPU"
                textSize = 9.5f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(
                    (7 * density).toInt(),
                    (2 * density).toInt(),
                    (7 * density).toInt(),
                    (2 * density).toInt()
                )
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E040FB"))
                    cornerRadius = 8f * density
                }
            }
            addView(pillBadge)
        }
    }

    private fun buildFooter(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            val srcLang = getSourceLanguage?.invoke() ?: "JA"
            val tgtLang = getTargetLanguage?.invoke() ?: "ES"

            // Language Route
            val routeText = TextView(context).apply {
                text = "文 $srcLang → $tgtLang"
                textSize = 11f
                setTextColor(Color.parseColor("#8F9BA8"))
                setTypeface(typeface, Typeface.BOLD)
            }
            addView(routeText)

            // Flexible spacer
            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            }
            addView(spacer)

            // Close button "Ocultar ✕"
            val hideBtn = TextView(context).apply {
                text = "Ocultar ✕"
                textSize = 11.5f
                setTextColor(Color.parseColor("#39C5BB"))
                setTypeface(typeface, Typeface.BOLD)
                isClickable = true
                setPadding((6 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                setOnClickListener {
                    dismiss()
                }
            }
            addView(hideBtn)
        }
    }

    private fun buildRow(item1: View, item2: View): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (3 * density).toInt()
                bottomMargin = (3 * density).toInt()
            }
            addView(item1)
            addView(item2)
        }
    }

    private fun createGridItem(
        title: String,
        iconRes: Int,
        tileColor: Int,
        action: () -> Unit
    ): View {
        val itemContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (4 * density).toInt()
                marginEnd = (4 * density).toInt()
            }
            setPadding((4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt())
        }

        // 1. Icon Squircle Tile (52dp x 52dp, 14dp radius)
        val squircle = FrameLayout(context).apply {
            val squircleSize = (52 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(squircleSize, squircleSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val alphaColor = (tileColor and 0x00FFFFFF) or (0x28 shl 24)
            background = GradientDrawable().apply {
                setColor(alphaColor)
                cornerRadius = 14f * density
                setStroke((1.5f * density).toInt(), tileColor)
            }
        }

        val iconView = ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(tileColor)
            val iconSize = (24 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        }
        squircle.addView(iconView)
        itemContainer.addView(squircle)

        // 2. Text Label
        val label = TextView(context).apply {
            text = title
            textSize = 11.5f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * density).toInt()
            }
        }
        itemContainer.addView(label)

        // Tactile scale / alpha animation before action dispatch
        setTactileClickListener(itemContainer, action)

        return itemContainer
    }

    private fun createAutoModeItem(): View {
        val itemContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (4 * density).toInt()
                marginEnd = (4 * density).toInt()
            }
            setPadding((4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt())
        }

        val color = if (isManualMode) Color.parseColor("#10B981") else Color.parseColor("#E040FB")
        val alphaColor = (color and 0x00FFFFFF) or (0x28 shl 24)

        val squircle = FrameLayout(context).apply {
            val squircleSize = (52 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(squircleSize, squircleSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            background = GradientDrawable().apply {
                setColor(alphaColor)
                cornerRadius = 14f * density
                setStroke((1.5f * density).toInt(), color)
            }
        }

        val iconView = ImageView(context).apply {
            setImageResource(R.drawable.ic_menu_sync)
            setColorFilter(color)
            val iconSize = (24 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        }
        squircle.addView(iconView)
        itemContainer.addView(squircle)

        val label = TextView(context).apply {
            text = if (isManualMode) "Traducción\nAutomática" else "Modo\nManual"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * density).toInt()
            }
        }
        itemContainer.addView(label)

        autoModeLabel = label
        autoModeSquircle = squircle
        autoModeIcon = iconView

        setTactileClickListener(itemContainer) {
            isManualMode = !isManualMode
            updateAutoModeUI()
            onToggleModeClicked(isManualMode)
        }

        return itemContainer
    }

    private fun updateAutoModeUI() {
        val color = if (isManualMode) Color.parseColor("#10B981") else Color.parseColor("#E040FB")
        val alphaColor = (color and 0x00FFFFFF) or (0x28 shl 24)

        autoModeSquircle?.background = GradientDrawable().apply {
            setColor(alphaColor)
            cornerRadius = 14f * density
            setStroke((1.5f * density).toInt(), color)
        }
        autoModeIcon?.setColorFilter(color)
        autoModeLabel?.text = if (isManualMode) "Traducción\nAutomática" else "Modo\nManual"
    }

    /**
     * Tactile interaction: compresses scale to 0.92 and alpha to 0.8 on touch down,
     * springs back to normal on touch up, and waits 100ms so feedback is visible before
     * closing the menu and executing the action.
     */
    private fun setTactileClickListener(view: View, action: () -> Unit) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.8f).setDuration(70).start()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(70).start()
                    v.postDelayed({
                        dismiss()
                        action()
                    }, 100)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(70).start()
                    true
                }
                else -> false
            }
        }
    }

    fun dismiss() {
        if (!isAttached) return
        rootScrimView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
            rootScrimView = null
            isAttached = false
        }
    }

    fun setMode(manual: Boolean) {
        isManualMode = manual
        updateAutoModeUI()
    }
}
