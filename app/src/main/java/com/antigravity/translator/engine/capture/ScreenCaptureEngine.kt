package com.antigravity.translator.engine.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
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

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val backgroundHandler = Handler(Looper.getMainLooper())

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
     * Safely initializes ImageReader and VirtualDisplay.
     * Note: maxImages is capped at 2 to eliminate frame queue buildup.
     */
    private fun initVirtualDisplay() {
        lock.withLock {
            try {
                // Initialize ImageReader with RGBA_8888 and maxImages = 2
                imageReader = ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    PixelFormat.RGBA_8888,
                    2
                )

                virtualDisplay = mediaProjection.createVirtualDisplay(
                    VIRTUAL_DISPLAY_NAME,
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader?.surface,
                    null,
                    backgroundHandler
                )

                onDimensionsChanged?.invoke(screenWidth, screenHeight)
                Log.d(tag, "VirtualDisplay created successfully")
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
     * Grabs the latest frame from the ImageReader buffer.
     *
     * Correctly handles rowStride / pixelStride padding to eliminate graphic shearing,
     * and guarantees that image.close() is executed in a finally block to prevent
     * buffer starvation.
     */
    fun acquireLatestFrame(): Bitmap? {
        lock.withLock {
            val reader = imageReader ?: return null
            val image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                Log.w(tag, "acquireLatestImage failed", e)
                null
            } ?: return null

            try {
                val planes = image.planes
                if (planes.isEmpty()) return null

                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                // Allocate bitmap accounting for stride padding
                val paddedBitmap = Bitmap.createBitmap(
                    screenWidth + (rowPadding / pixelStride),
                    screenHeight,
                    Bitmap.Config.ARGB_8888
                )
                paddedBitmap.copyPixelsFromBuffer(buffer)

                // If padding exists, crop out exact screen dimension without distortion
                val finalBitmap = if (rowPadding > 0) {
                    val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, screenWidth, screenHeight)
                    paddedBitmap.recycle()
                    cropped
                } else {
                    paddedBitmap
                }

                return finalBitmap
            } catch (e: Exception) {
                Log.e(tag, "Error extracting bitmap from ImageReader buffer", e)
                return null
            } finally {
                // Critical: Close image immediately to return buffer to pool
                image.close()
            }
        }
    }

    /**
     * Releases VirtualDisplay and closes ImageReader.
     */
    fun release() {
        lock.withLock {
            try {
                virtualDisplay?.release()
                virtualDisplay = null
                imageReader?.close()
                imageReader = null
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
