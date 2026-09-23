package com.antigravity.translator.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.antigravity.translator.domain.model.ReadingProfile
import com.antigravity.translator.domain.model.ServiceState
import com.antigravity.translator.domain.model.TranslatedBlock
import com.antigravity.translator.tts.TtsManager

/**
 * Orchestrates WindowManager overlays with decoupled windows:
 * 1. FloatingBubbleView (FAB): Fixed 52dp circle. Never shifts, resizes, or flickers.
 * 2. FloatingMenuView: Independent 2-column launcher card popup with vector icons.
 * 3. DismissBackdropView: Full-screen transparent backdrop that dismisses translations when tapped outside speech bubbles in manual mode.
 * 4. MangaBubbleViews: Clean comic speech bubble overlays with 1:1 physical coordinate mapping and long-press TTS.
 * 5. TranslationDetailDialog: Modal viewing card with native TTS and independent clipboard copy.
 */
class OverlayWindowManager(
    private val context: Context,
    private val onTranslateNowRequested: () -> Unit,
    private val onCopyTextRequested: () -> Unit,
    private val onClearOverlayRequested: () -> Unit,
    private val onToggleModeRequested: (isManual: Boolean) -> Unit,
    private val onOpenSettingsRequested: () -> Unit,
    private val onStateToggle: (newState: ServiceState) -> Unit,
    private val onStopRequested: () -> Unit,
    private val onCropAdjustRequested: (() -> Unit)? = null,
    private val ttsManager: TtsManager? = null,
    private val getTargetLanguage: (() -> String)? = null,
    private val getSourceLanguage: (() -> String)? = null,
    initialIsManualMode: Boolean = true,
    initialReadingProfile: ReadingProfile = ReadingProfile.MANGA_JA,
    private val onReadingProfileChanged: ((ReadingProfile) -> Unit)? = null
) {
    private val tag = "OverlayWindowManager"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubbleView: FloatingBubbleView? = null
    private var isBubbleAttached = false

    private var currentIsManualMode: Boolean = initialIsManualMode
    private var dismissBackdropView: FrameLayout? = null
    private var isDismissBackdropAttached = false

    private var cropOverlayView: RegionSelectionOverlayView? = null
    private var isCropOverlayAttached = false

    private val menuView = FloatingMenuView(
        context = context,
        windowManager = windowManager,
        onTranslateClicked = onTranslateNowRequested,
        onCopyTextClicked = onCopyTextRequested,
        onToggleModeClicked = { manual ->
            updateMode(manual)
            onToggleModeRequested(manual)
        },
        onCropAdjustClicked = {
            onCropAdjustRequested?.invoke()
        },
        onSettingsClicked = onOpenSettingsRequested,
        onCloseClicked = onStopRequested,
        initialIsManualMode = initialIsManualMode,
        initialReadingProfile = initialReadingProfile,
        onReadingProfileChanged = { profile ->
            onReadingProfileChanged?.invoke(profile)
        }
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
                onBubbleSingleTap = { curX, curY ->
                    // Decoupled toggle: If showing or recently dismissed by the outside touch event, keep closed!
                    if (menuView.isShowing() || menuView.wasRecentlyDismissed()) {
                        menuView.dismiss()
                    } else {
                        menuView.show(curX, curY, bubbleSize)
                    }
                },
                onBubbleDoubleTap = {
                    menuView.dismiss()
                    onTranslateNowRequested()
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
     * In manual mode, also attaches a full-screen transparent backdrop behind bubbles
     * so that tapping anywhere outside bubbles instantly clears the translations.
     */
    fun updateTranslatedBlocks(blocks: List<TranslatedBlock>) {
        mainHandler.post {
            clearBubbleViews()
            removeDismissBackdrop()

            if (blocks.isEmpty()) return@post

            // Attach tap-to-dismiss backdrop under the bubbles in manual mode
            if (currentIsManualMode) {
                attachDismissBackdrop()
            }

            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            for (block in blocks) {
                try {
                    val rect = block.boundingBox
                    if (rect.width() <= 0 || rect.height() <= 0) continue

                    // Match the exact bounding box of the original text with compact 2dp padding
                    val paddingX = (2 * displayMetrics.density).toInt().coerceAtLeast(2)
                    val paddingY = (2 * displayMetrics.density).toInt().coerceAtLeast(2)

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
                            Toast.makeText(context, "Reproduciendo audio...", Toast.LENGTH_SHORT).show()
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
     * Attaches a full-screen transparent view behind manga speech bubbles.
     * Tapping anywhere outside speech bubbles invokes onClearOverlayRequested() to dismiss the translations.
     * Tapping on the floating bubble forwards the event directly to the bubble view.
     */
    private fun attachDismissBackdrop() {
        if (isDismissBackdropAttached) return
        try {
            val backdropParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            val backdrop = object : FrameLayout(context) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    val x = event.rawX.toInt()
                    val y = event.rawY.toInt()

                    // 1. If touch hits the floating bubble (Miku FAB), forward the event to it
                    val bubbleBounds = getControlBounds()
                    if (bubbleBounds != null && bubbleBounds.contains(x, y)) {
                        bubbleView?.dispatchTouchEvent(event)
                        return true
                    }

                    // 2. If menu is showing, let WindowManager handle it
                    if (menuView.isShowing()) {
                        return super.dispatchTouchEvent(event)
                    }

                    // 3. Any touch outside speech bubbles clears the canvas in manual mode
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        onClearOverlayRequested()
                        return true
                    }
                    return super.dispatchTouchEvent(event)
                }
            }

            windowManager.addView(backdrop, backdropParams)
            dismissBackdropView = backdrop
            isDismissBackdropAttached = true
            Log.d(tag, "DismissBackdropView attached for tap-to-dismiss translations")
        } catch (e: Exception) {
            Log.e(tag, "Failed to attach DismissBackdropView", e)
        }
    }

    private fun removeDismissBackdrop() {
        if (!isDismissBackdropAttached) return
        dismissBackdropView?.let {
            try {
                it.removeAllViews()
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore if detached
            }
            dismissBackdropView = null
            isDismissBackdropAttached = false
        }
    }

    /**
     * Clears all translated manga speech bubbles and backdrop from the screen.
     */
    fun clearCanvas() {
        mainHandler.post {
            removeDismissBackdrop()
            clearBubbleViews()
            detailDialog.dismiss()
        }
    }

    private fun clearBubbleViews() {
        for (view in activeBubbleViews) {
            try {
                view.cleanup()
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
        currentIsManualMode = isManual
        if (!isManual) {
            removeDismissBackdrop()
        }
        menuView.setMode(isManual)
    }

    fun updateReadingProfile(profile: ReadingProfile) {
        menuView.setReadingProfile(profile)
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
     * Shows interactive crop selection overlay for defining the translation area.
     */
    fun showCropSelectorOverlay(
        initialRect: Rect?,
        onConfirmed: (Rect) -> Unit,
        onFullScreen: () -> Unit
    ) {
        mainHandler.post {
            dismissCropSelectorOverlay()
            menuView.dismiss()
            bubbleView?.visibility = View.GONE

            try {
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )

                val cropView = RegionSelectionOverlayView(
                    context = context,
                    initialCropRect = initialRect,
                    onCropConfirmed = { confirmedRect ->
                        dismissCropSelectorOverlay()
                        onConfirmed(confirmedRect)
                    },
                    onFullScreenSelected = {
                        dismissCropSelectorOverlay()
                        onFullScreen()
                    },
                    onDismissRequested = {
                        dismissCropSelectorOverlay()
                    }
                )

                windowManager.addView(cropView, params)
                cropOverlayView = cropView
                isCropOverlayAttached = true
                Log.d(tag, "RegionSelectionOverlayView attached")
            } catch (e: Exception) {
                Log.e(tag, "Failed to attach RegionSelectionOverlayView", e)
            }
        }
    }

    fun dismissCropSelectorOverlay() {
        if (!isCropOverlayAttached) return
        cropOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore if detached
            }
            cropOverlayView = null
            isCropOverlayAttached = false
            bubbleView?.visibility = View.VISIBLE
            Log.d(tag, "RegionSelectionOverlayView dismissed")
        }
    }

    /**
     * Tears down all overlays and modal dialogs.
     */
    fun removeOverlays() {
        mainHandler.post {
            dismissCropSelectorOverlay()
            removeDismissBackdrop()
            clearBubbleViews()
            detailDialog.dismiss()
            menuView.dismiss()

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
