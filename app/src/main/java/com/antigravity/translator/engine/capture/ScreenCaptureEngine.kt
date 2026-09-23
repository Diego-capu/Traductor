package com.antigravity.translator.engine.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.antigravity.translator.telemetry.AppPerformanceTracker
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Low-level graphics engine capturing screen buffers via MediaProjection,
 * VirtualDisplay, and ImageReader with stride padding normalization and rotation support.
 */
class ScreenCaptureEngine(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val onDimensionsChanged: ((width: Int, height: Int) -> Unit)? = null
) {
    private val tag = "ScreenCaptureEngine"
    private val lock = ReentrantLock()
    private val cacheLock = Any()

    private val captureThread = HandlerThread("ScreenCaptureThread").apply { start() }
    private val backgroundHandler = Handler(captureThread.looper)

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    @Volatile
    private var lastCachedBitmap: Bitmap? = null

    var screenWidth: Int = 0
        private set
    var screenHeight: Int = 0
        private set
    var screenDensity: Int = 0
        private set

    init {
        setupDimensions()
        initVirtualDisplay()
    }

    /**
     * Reads current window metrics and display density.
     */
    private fun setupDimensions() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            screenDensity = context.resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            val displayMetrics = DisplayMetrics().apply {
                windowManager.defaultDisplay.getRealMetrics(this)
            }
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            screenDensity = displayMetrics.densityDpi
        }

        Log.d(tag, "Configured capture dimensions: ${screenWidth}x${screenHeight} @ ${screenDensity}dpi")
    }

    /**
     * Safely converts an Image buffer into a Bitmap, handling stride row padding.
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null

        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val paddedBitmap = Bitmap.createBitmap(
            screenWidth + (rowPadding / pixelStride),
            screenHeight,
            Bitmap.Config.ARGB_8888
        )
        paddedBitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding > 0) {
            val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, screenWidth, screenHeight)
            paddedBitmap.recycle()
            cropped
        } else {
            paddedBitmap
        }
    }

    /**
     * Safely initializes ImageReader and VirtualDisplay.
     * Note: maxImages is set to 3 for triple buffering to prevent HardwareBuffer dequeue stalls.
     */
    private fun initVirtualDisplay() {
        lock.withLock {
            try {
                // Initialize ImageReader with RGBA_8888 and maxImages = 3 (triple buffering)
                val reader = ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    PixelFormat.RGBA_8888,
                    3
                )
                imageReader = reader

                // Set listener on background thread to continuously cache latest frame
                // and free Image buffers immediately to prevent starvation on static screens.
                reader.setOnImageAvailableListener({ r ->
                    try {
                        val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                        try {
                            val bitmap = imageToBitmap(img)
                            if (bitmap != null) {
                                synchronized(cacheLock) {
                                    val old = lastCachedBitmap
                                    lastCachedBitmap = bitmap
                                    old?.recycle()
                                }
                            }
                        } finally {
                            img.close()
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Background frame caching error: ${e.message}")
                    }
                }, backgroundHandler)

                virtualDisplay = mediaProjection.createVirtualDisplay(
                    VIRTUAL_DISPLAY_NAME,
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.surface,
                    null,
                    backgroundHandler
                )

                onDimensionsChanged?.invoke(screenWidth, screenHeight)
                Log.d(tag, "VirtualDisplay created successfully with continuous frame caching")
            } catch (e: Exception) {
                Log.e(tag, "Failed to create VirtualDisplay or ImageReader", e)
            }
        }
    }

    /**
     * Safely handles screen rotation / orientation change:
     * Destroys existing VirtualDisplay and ImageReader in safe sequence,
     * recalculates display metrics, and recreates the capture pipeline.
     */
    fun onConfigurationOrOrientationChanged() {
        lock.withLock {
            Log.d(tag, "Rebuilding capture pipeline for orientation change...")
            try {
                // 1. Release VirtualDisplay first
                virtualDisplay?.release()
                virtualDisplay = null

                // 2. Safely close existing ImageReader
                imageReader?.close()
                imageReader = null

                // Clear cached frame
                synchronized(cacheLock) {
                    lastCachedBitmap?.recycle()
                    lastCachedBitmap = null
                }

                // 3. Recalculate metrics
                setupDimensions()

                // 4. Recreate pipeline
                initVirtualDisplay()
            } catch (e: Exception) {
                Log.e(tag, "Error during capture pipeline rotation reconfiguration", e)
            }
        }
    }

    /**
     * Grabs the latest frame from the ImageReader buffer or the background frame cache.
     *
     * Handles static screen starvation: when screen is stationary, acquireLatestImage()
     * returns null, so this falls back to a clean copy of the last cached frame.
     */
    fun acquireLatestFrame(): Bitmap? {
        return AppPerformanceTracker.trace("screen_frame_capture") { perfTrace ->
            lock.withLock {
                val reader = imageReader ?: return@trace null

                // 1. Try to acquire the freshest frame if available right now
                var extractedBitmap: Bitmap? = null
                try {
                    val image = reader.acquireLatestImage() ?: reader.acquireNextImage()
                    if (image != null) {
                        try {
                            extractedBitmap = imageToBitmap(image)
                            if (extractedBitmap != null) {
                                synchronized(cacheLock) {
                                    val old = lastCachedBitmap
                                    lastCachedBitmap = extractedBitmap.copy(extractedBitmap.config ?: Bitmap.Config.ARGB_8888, false)
                                    old?.recycle()
                                }
                            }
                        } finally {
                            image.close()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Direct acquireImage attempt failed, checking cached frame: ${e.message}")
                }

                if (extractedBitmap != null) {
                    perfTrace.putMetric("frame_width", extractedBitmap.width.toLong())
                    perfTrace.putMetric("frame_height", extractedBitmap.height.toLong())
                    return@trace extractedBitmap
                }

                // 2. Fallback to cached frame if screen was stationary/static
                synchronized(cacheLock) {
                    val cached = lastCachedBitmap
                    if (cached != null && !cached.isRecycled) {
                        val fallback = cached.copy(cached.config ?: Bitmap.Config.ARGB_8888, false)
                        if (fallback != null) {
                            perfTrace.putMetric("frame_width", fallback.width.toLong())
                            perfTrace.putMetric("frame_height", fallback.height.toLong())
                        }
                        return@trace fallback
                    }
                }

                return@trace null
            }
        }
    }

    /**
     * Releases VirtualDisplay and closes ImageReader and background thread.
     */
    fun release() {
        lock.withLock {
            try {
                virtualDisplay?.release()
                virtualDisplay = null
                imageReader?.close()
                imageReader = null

                synchronized(cacheLock) {
                    lastCachedBitmap?.recycle()
                    lastCachedBitmap = null
                }

                try {
                    captureThread.quitSafely()
                } catch (_: Exception) {}

                Log.d(tag, "ScreenCaptureEngine released successfully")
            } catch (e: Exception) {
                Log.w(tag, "Error releasing capture engine resources", e)
            }
        }
    }

    companion object {
        private const val VIRTUAL_DISPLAY_NAME = "ScreenTranslatorVirtualDisplay"
    }
}
