package com.antigravity.translator.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import com.antigravity.translator.domain.model.ServiceState
import com.antigravity.translator.domain.model.TranslatedBlock
import com.antigravity.translator.tts.TtsManager

/**
 * Orchestrates WindowManager overlays with decoupled windows:
 * 1. FloatingBubbleView (FAB): Fixed 52dp circle. Never shifts, resizes, or flickers.
 * 2. FloatingMenuView: Independent popup window with Full, Snip, Magnifier, and Clear actions.
 * 3. RegionSelectionOverlayView: Full-screen interactive drag-to-select snip tool.
 * 4. MagnifierBubbleView: Secondary draggable target reticle for localized 200x100 dp translation.
 * 5. MangaBubbleViews: Clean comic speech bubble overlays with 1:1 physical coordinate mapping and long-press TTS.
 * 6. TranslationDetailDialog: Modal viewing card with native TTS and independent clipboard copy.
 */
class OverlayWindowManager(
    private val context: Context,
    private val onTranslateNowRequested: () -> Unit,
    private val onRegionSnipRequested: () -> Unit,
    private val onSampleAreaRequested: (Rect) -> Unit,
    private val onClearOverlayRequested: () -> Unit,
    private val onToggleModeRequested: (isManual: Boolean) -> Unit,
    private val onStateToggle: (newState: ServiceState) -> Unit,
    private val onStopRequested: () -> Unit,
    private val ttsManager: TtsManager? = null,
    private val getTargetLanguage: (() -> String)? = null,
    private val getSourceLanguage: (() -> String)? = null,
    private val initialIsManualMode: Boolean = true
) {
    private val tag = "OverlayWindowManager"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubbleView: FloatingBubbleView? = null
    private var isBubbleAttached = false

    private var regionSelectionView: RegionSelectionOverlayView? = null
    private var magnifierBubbleView: MagnifierBubbleView? = null
    private var isMagnifierAttached = false

    private val menuView = FloatingMenuView(
        context = context,
        windowManager = windowManager,
        onTranslateClicked = onTranslateNowRequested,
        onRegionSnipClicked = onRegionSnipRequested,
        onToggleMagnifierClicked = { toggleMagnifier() },
        onClearClicked = onClearOverlayRequested,
        onToggleModeClicked = onToggleModeRequested,
        onCloseClicked = onStopRequested,
        initialIsManualMode = initialIsManualMode
    )

    private val activeBubbleViews = mutableListOf<MangaBubbleView>()
    private val detailDialog = TranslationDetailDialog(
        context = context,
        windowManager = windowManager,
        ttsManager = ttsManager,
        getTargetLanguage = getTargetLanguage,
        getSourceLanguage = getSourceLanguage
    )

    private val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    /**
     * Initializes and attaches the decoupled floating bubble.
     */
    fun showOverlays() {
        showBubbleOverlay()
    }

    private fun showBubbleOverlay() {
        if (isBubbleAttached) return
        try {
            val density = context.resources.displayMetrics.density
            val bubbleSize = (52 * density).toInt()

            val bubbleParams = WindowManager.LayoutParams(
                bubbleSize,
                bubbleSize,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (16 * density).toInt()
                y = (200 * density).toInt()
            }

            bubbleView = FloatingBubbleView(
                context = context,
                windowManager = windowManager,
                windowLayoutParams = bubbleParams,
                onBubbleClicked = { curX, curY ->
                    // Decoupled: Tapping the circle toggles the separate menu window without moving the bubble!
                    if (menuView.isShowing()) {
                        menuView.dismiss()
                    } else {
                        menuView.show(curX, curY, bubbleSize)
                    }
                }
            )

            windowManager.addView(bubbleView, bubbleParams)
            isBubbleAttached = true
            Log.d(tag, "Floating bubble attached with fixed dimensions")
        } catch (e: Exception) {
            Log.e(tag, "Failed to attach bubble overlay", e)
        }
    }

    /**
     * Updates and displays comic speech bubbles directly over original dialogue coordinates.
     * Uses FLAG_LAYOUT_IN_SCREEN so (x, y) maps 1:1 to physical screen pixels.
     */
    fun updateTranslatedBlocks(blocks: List<TranslatedBlock>) {
        mainHandler.post {
            clearBubbleViews()

            if (blocks.isEmpty()) return@post

            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            for (block in blocks) {
                try {
                    val rect = block.boundingBox
                    if (rect.width() <= 0 || rect.height() <= 0) continue

                    // Match the exact bounding box of the original text with compact padding
                    val paddingX = (4 * displayMetrics.density).toInt()
                    val paddingY = (3 * displayMetrics.density).toInt()

                    val bubbleX = kotlin.math.max(0, rect.left - paddingX)
                    val bubbleY = kotlin.math.max(0, rect.top - paddingY)
                    val bubbleWidth = kotlin.math.min(screenWidth - bubbleX, rect.width() + (paddingX * 2))
                    val bubbleHeight = kotlin.math.min(screenHeight - bubbleY, rect.height() + (paddingY * 2))

                    val bubbleParams = WindowManager.LayoutParams(
                        bubbleWidth,
                        bubbleHeight,
                        layoutType,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = bubbleX
                        y = bubbleY
                    }

                    val mangaBubbleView = MangaBubbleView(
                        context = context,
                        block = block,
                        onBubbleClicked = { clickedBlock ->
                            detailDialog.show(clickedBlock)
                        },
                        onBubbleLongClicked = { clickedBlock ->
                            val targetLang = getTargetLanguage?.invoke() ?: "ES"
                            val textToSpeak = clickedBlock.translatedText.ifEmpty { clickedBlock.originalText }
                            ttsManager?.speak(textToSpeak, targetLang)
                            Toast.makeText(context, "🔊 Reproduciendo audio...", Toast.LENGTH_SHORT).show()
                        }
                    )

                    windowManager.addView(mangaBubbleView, bubbleParams)
                    activeBubbleViews.add(mangaBubbleView)
                } catch (e: Exception) {
                    Log.w(tag, "Error adding manga bubble view", e)
                }
            }
            Log.d(tag, "Rendered ${activeBubbleViews.size} manga speech bubbles")
        }
    }

    /**
     * Launches the full-screen Snip Tool (RegionSelectionOverlayView).
     * Automatically unblocks all user touch input once confirmed or cancelled.
     */
    fun startRegionSelection(onRegionSelected: (Rect) -> Unit) {
        mainHandler.post {
            dismissRegionSelection()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            val overlay = RegionSelectionOverlayView(
                context = context,
                onRegionSelected = { rect ->
                    dismissRegionSelection()
                    onRegionSelected(rect)
                },
                onDismissed = {
                    dismissRegionSelection()
                }
            )
            regionSelectionView = overlay

            try {
                windowManager.addView(overlay, params)
                Log.d(tag, "RegionSelectionOverlayView attached to WindowManager")
            } catch (e: Exception) {
                Log.e(tag, "Failed to attach RegionSelectionOverlayView", e)
            }
        }
    }

    fun dismissRegionSelection() {
        mainHandler.post {
            regionSelectionView?.let {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    // Ignore
                }
                regionSelectionView = null
            }
        }
    }

    /**
     * Toggles the secondary Point / Drag-and-Drop Magnifier Bubble.
     */
    fun toggleMagnifier() {
        if (isMagnifierAttached) {
            dismissMagnifier()
        } else {
            showMagnifier()
        }
    }

    fun showMagnifier() {
        if (isMagnifierAttached) return
        mainHandler.post {
            try {
                val density = context.resources.displayMetrics.density
                val bubbleSize = (48 * density).toInt()

                val params = WindowManager.LayoutParams(
                    bubbleSize,
                    bubbleSize,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = (80 * density).toInt()
                    y = (320 * density).toInt()
                }

                val magnifier = MagnifierBubbleView(
                    context = context,
                    windowManager = windowManager,
                    windowLayoutParams = params,
                    onSampleAreaRequested = { sampleRect ->
                        onSampleAreaRequested(sampleRect)
                    },
                    onCloseRequested = {
                        dismissMagnifier()
                    }
                )
                magnifierBubbleView = magnifier
                windowManager.addView(magnifier, params)
                isMagnifierAttached = true
                Log.d(tag, "MagnifierBubbleView attached to WindowManager")
            } catch (e: Exception) {
                Log.e(tag, "Failed to attach MagnifierBubbleView", e)
            }
        }
    }

    fun dismissMagnifier() {
        mainHandler.post {
            if (!isMagnifierAttached) return@post
            magnifierBubbleView?.let {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    // Ignore
                }
                magnifierBubbleView = null
                isMagnifierAttached = false
            }
        }
    }

    /**
     * Clears all translated manga speech bubbles from the screen.
     */
    fun clearCanvas() {
        mainHandler.post {
            clearBubbleViews()
            detailDialog.dismiss()
        }
    }

    private fun clearBubbleViews() {
        for (view in activeBubbleViews) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                // Ignore if already detached
            }
        }
        activeBubbleViews.clear()
    }

    /**
     * Updates state reflection on the floating bubble dot.
     */
    fun updateControlState(state: ServiceState) {
        bubbleView?.updateState(state)
        if (state == ServiceState.PAUSED) {
            clearCanvas()
        }
    }

    fun updateMode(isManual: Boolean) {
        menuView.setMode(isManual)
    }

    /**
     * Returns screen bounds of the floating bubble to exclude from OCR.
     */
    fun getControlBounds(): Rect? {
        val b = bubbleView ?: return null
        val location = IntArray(2)
        b.getLocationOnScreen(location)
        val size = b.bubbleSizePx
        return Rect(location[0], location[1], location[0] + size, location[1] + size)
    }

    /**
     * Tears down all overlays, snip tool, magnifier, and modal dialogs.
     */
    fun removeOverlays() {
        mainHandler.post {
            clearBubbleViews()
            detailDialog.dismiss()
            menuView.dismiss()
            dismissRegionSelection()
            dismissMagnifier()

            try {
                if (isBubbleAttached && bubbleView != null) {
                    windowManager.removeView(bubbleView)
                    bubbleView = null
                    isBubbleAttached = false
                }
            } catch (e: Exception) {
                Log.w(tag, "Error removing bubble overlay", e)
            }
        }
    }
}
