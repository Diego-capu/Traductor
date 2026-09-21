package com.antigravity.translator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.antigravity.translator.R
import com.antigravity.translator.TranslatorApplication
import com.antigravity.translator.data.pref.AppPreferences
import com.antigravity.translator.data.repository.DeepLRepository
import com.antigravity.translator.domain.model.DetectedTextBlock
import com.antigravity.translator.domain.model.ServiceState
import com.antigravity.translator.engine.capture.ScreenCaptureEngine
import com.antigravity.translator.engine.ocr.MangaBubbleClusterer
import com.antigravity.translator.engine.ocr.OcrEngine
import com.antigravity.translator.overlay.OverlayWindowManager
import com.antigravity.translator.tts.TtsManager
import com.antigravity.translator.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground Service responsible for:
 * 1. Screen capture buffer streaming via MediaProjection and ImageReader
 * 2. On-device OCR via Google ML Kit
 * 3. Text batching and persistent disk caching via DeepLRepository
 * 4. Dual-layer WindowManager overlays with on-demand (single-shot) and automatic modes.
 */
class ScreenCaptureService : Service() {

    private val tag = "ScreenCaptureService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var appPreferences: AppPreferences
    private lateinit var deepLRepository: DeepLRepository
    private lateinit var ocrEngine: OcrEngine
    private lateinit var overlayWindowManager: OverlayWindowManager
    private lateinit var ttsManager: TtsManager

    private var mediaProjection: MediaProjection? = null
    private var captureEngine: ScreenCaptureEngine? = null

    private var captureTickerJob: Job? = null
    private var lastDetectedBlocks: List<DetectedTextBlock> = emptyList()

    private val _serviceState = MutableStateFlow(ServiceState.STOPPED)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(tag, "MediaProjection stopped by system")
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = TranslatorApplication.instance
        appPreferences = app.appPreferences
        deepLRepository = app.deepLRepository
        ocrEngine = OcrEngine()
        ttsManager = TtsManager(this)

        overlayWindowManager = OverlayWindowManager(
            context = this,
            onTranslateNowRequested = {
                translateScreenOnce()
            },
            onRegionSnipRequested = {
                overlayWindowManager.startRegionSelection { selectedRect ->
                    translateRegion(selectedRect)
                }
            },
            onSampleAreaRequested = { sampleRect ->
                translateRegion(sampleRect)
            },
            onClearOverlayRequested = {
                lastDetectedBlocks = emptyList()
                overlayWindowManager.clearCanvas()
            },
            onToggleModeRequested = { isManual ->
                toggleCaptureMode(isManual)
            },
            onStateToggle = { newState ->
                when (newState) {
                    ServiceState.RUNNING -> resumeCapture()
                    ServiceState.PAUSED -> pauseCapture()
                    ServiceState.STOPPED -> stopSelf()
                }
            },
            onStopRequested = {
                stopSelf()
            },
            ttsManager = ttsManager,
            getTargetLanguage = { appPreferences.targetLanguage },
            getSourceLanguage = { appPreferences.sourceLanguage },
            initialIsManualMode = appPreferences.isManualMode
        )

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseCapture()
                return START_STICKY
            }
            ACTION_RESUME -> {
                resumeCapture()
                return START_STICKY
            }
            ACTION_TRANSLATE_ONCE -> {
                translateScreenOnce()
                return START_STICKY
            }
            ACTION_CLEAR_OVERLAY -> {
                overlayWindowManager.clearCanvas()
                return START_STICKY
            }
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != 0 && resultData != null) {
            startProjectionPipeline(resultCode, resultData)
        } else if (captureEngine == null) {
            Log.w(tag, "Service started without MediaProjection intent data")
            stopSelf()
        }

        return START_STICKY
    }

    /**
     * Initializes MediaProjection, creates overlays, and prepares translation mode.
     */
    private fun startProjectionPipeline(resultCode: Int, resultData: Intent) {
        // 1. Start foreground service immediately with mediaProjection type
        val notification = buildNotification(ServiceState.RUNNING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 2. Initialize MediaProjection
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)

        if (projection == null) {
            Log.e(tag, "MediaProjectionManager returned null projection")
            stopSelf()
            return
        }

        mediaProjection = projection
        projection.registerCallback(mediaProjectionCallback, Handler(Looper.getMainLooper()))

        // 3. Initialize low-level capture engine
        captureEngine = ScreenCaptureEngine(
            context = this,
            mediaProjection = projection
        )

        // 4. Show overlays
        overlayWindowManager.showOverlays()

        // 5. Update state
        _serviceState.value = ServiceState.RUNNING
        overlayWindowManager.updateControlState(ServiceState.RUNNING)

        // If in auto mode, start continuous loop; otherwise wait for on-demand "TRADUCIR" clicks
        if (!appPreferences.isManualMode) {
            startCaptureTicker()
        }
    }

    /**
     * Executes an On-Demand single-frame capture, OCR, translation, and overlay display.
     * Leaves the translated text visible until the user taps "LIMPIAR" or translates again.
     */
    fun translateScreenOnce() {
        serviceScope.launch {
            val engine = captureEngine
            if (engine == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScreenCaptureService, "Iniciando motor de captura...", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Small delay to ensure any transient touch highlights fade
            delay(150)

            val bitmap = engine.acquireLatestFrame()
            if (bitmap == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScreenCaptureService, "No se pudo capturar la pantalla. Intenta nuevamente.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                // 1. Detect text, filter out status bar / floating toolbar, and cluster speech bubbles
                val rawBlocks = ocrEngine.processFrame(bitmap)
                val cleanBlocks = filterIgnoredScreenRegions(rawBlocks)
                val density = resources.displayMetrics.density
                val detectedBlocks = com.antigravity.translator.engine.ocr.MangaBubbleClusterer.clusterMangaBubbles(cleanBlocks, density)

                if (detectedBlocks.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ScreenCaptureService, "No se detectó texto en la pantalla", Toast.LENGTH_SHORT).show()
                    }
                    overlayWindowManager.clearCanvas()
                    return@launch
                }

                // 2. Translate uncached texts (and pull cached ones instantly from disk)
                val result = deepLRepository.translateBlocks(detectedBlocks)

                result.onSuccess { translatedBlocks ->
                    lastDetectedBlocks = detectedBlocks
                    overlayWindowManager.updateTranslatedBlocks(translatedBlocks)
                }.onFailure { error ->
                    Log.e(tag, "Single translation error: ${error.message}", error)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ScreenCaptureService, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    /**
     * Executes localized translation for a sub-region (Snip selection or Magnifier Point).
     * Strictly avoids premature bitmap recycling before ML Kit completes its asynchronous processing.
     */
    fun translateRegion(rect: Rect) {
        serviceScope.launch {
            val engine = captureEngine
            if (engine == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScreenCaptureService, "Iniciando motor de captura...", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            delay(100)

            val fullBitmap = engine.acquireLatestFrame()
            if (fullBitmap == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScreenCaptureService, "No se pudo capturar la pantalla", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // 1. Validate & clamp coordinates
            val cropLeft = rect.left.coerceIn(0, fullBitmap.width - 1)
            val cropTop = rect.top.coerceIn(0, fullBitmap.height - 1)
            val cropRight = rect.right.coerceIn(cropLeft + 1, fullBitmap.width)
            val cropBottom = rect.bottom.coerceIn(cropTop + 1, fullBitmap.height)
            val cropWidth = cropRight - cropLeft
            val cropHeight = cropBottom - cropTop

            if (cropWidth < 10 || cropHeight < 10) {
                fullBitmap.recycle()
                return@launch
            }

            // 2. Crop the sub-region
            val croppedBitmap = try {
                Bitmap.createBitmap(fullBitmap, cropLeft, cropTop, cropWidth, cropHeight)
            } catch (e: Exception) {
                Log.e(tag, "Failed to create cropped sub-bitmap", e)
                fullBitmap.recycle()
                return@launch
            }

            // Safe to release fullBitmap now that cropped sub-bitmap exists
            try {
                fullBitmap.recycle()
            } catch (e: Exception) {
                // Guard
            }

            // 3. Process ML Kit OCR on croppedBitmap:
            // Crucial: Only recycle croppedBitmap AFTER ocrEngine.processFrame has fully awaited and completed!
            val rawBlocks = try {
                ocrEngine.processFrame(croppedBitmap)
            } finally {
                try {
                    if (!croppedBitmap.isRecycled) {
                        croppedBitmap.recycle()
                    }
                } catch (e: Exception) {
                    // Guard
                }
            }

            if (rawBlocks.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScreenCaptureService, "No se detectó texto en el área seleccionada", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // 4. Map bounding boxes back to physical screen space
            val offsetBlocks = rawBlocks.map { block ->
                val screenBox = Rect(block.boundingBox)
                screenBox.offset(cropLeft, cropTop)
                block.copy(boundingBox = screenBox)
            }

            // 5. Cluster and batch-translate
            val density = resources.displayMetrics.density
            val detectedBlocks = MangaBubbleClusterer.clusterMangaBubbles(offsetBlocks, density)

            val result = deepLRepository.translateBlocks(detectedBlocks)

            result.onSuccess { translatedBlocks ->
                lastDetectedBlocks = detectedBlocks
                overlayWindowManager.updateTranslatedBlocks(translatedBlocks)
            }.onFailure { error ->
                Log.e(tag, "Region translation error: ${error.message}", error)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScreenCaptureService, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun toggleCaptureMode(isManual: Boolean) {
        appPreferences.isManualMode = isManual
        overlayWindowManager.updateMode(isManual)

        if (isManual) {
            captureTickerJob?.cancel()
            captureTickerJob = null
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Modo Manual: Pulsa 'TRADUCIR' cuando desees traducir", Toast.LENGTH_SHORT).show()
            }
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Modo Automático: Traduciendo en tiempo real", Toast.LENGTH_SHORT).show()
            }
            startCaptureTicker()
        }
    }

    /**
     * Starts the frame capture ticker coroutine loop for continuous auto-translation.
     */
    private fun startCaptureTicker() {
        captureTickerJob?.cancel()
        captureTickerJob = serviceScope.launch {
            Log.d(tag, "Screen capture ticker started (Auto mode)")
            while (isActive && _serviceState.value == ServiceState.RUNNING && !appPreferences.isManualMode) {
                val cycleStartTime = System.currentTimeMillis()

                try {
                    processScreenFrame()
                } catch (e: Exception) {
                    Log.e(tag, "Error processing frame cycle", e)
                }

                val elapsed = System.currentTimeMillis() - cycleStartTime
                val interval = appPreferences.captureIntervalMs
                val waitTime = kotlin.math.max(200L, interval - elapsed)
                delay(waitTime)
            }
        }
    }

    private suspend fun processScreenFrame() {
        val engine = captureEngine ?: return
        val bitmap = engine.acquireLatestFrame() ?: return

        try {
            val rawBlocks = ocrEngine.processFrame(bitmap)
            val cleanBlocks = filterIgnoredScreenRegions(rawBlocks)
            val density = resources.displayMetrics.density
            val detectedBlocks = com.antigravity.translator.engine.ocr.MangaBubbleClusterer.clusterMangaBubbles(cleanBlocks, density)

            if (detectedBlocks.isEmpty()) {
                if (lastDetectedBlocks.isNotEmpty()) {
                    lastDetectedBlocks = emptyList()
                    overlayWindowManager.clearCanvas()
                }
                return
            }

            if (areDetectedBlocksEqual(lastDetectedBlocks, detectedBlocks)) {
                return
            }

            val translationResult = deepLRepository.translateBlocks(detectedBlocks)

            translationResult.onSuccess { translatedBlocks ->
                lastDetectedBlocks = detectedBlocks
                overlayWindowManager.updateTranslatedBlocks(translatedBlocks)
            }.onFailure { error ->
                Log.w(tag, "Frame translation failed: ${error.message}")
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun areDetectedBlocksEqual(
        previous: List<DetectedTextBlock>,
        current: List<DetectedTextBlock>
    ): Boolean {
        if (previous.size != current.size) return false
        for (i in previous.indices) {
            if (!previous[i].isContentEqual(current[i])) {
                return false
            }
        }
        return true
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else (28 * resources.displayMetrics.density).toInt()
    }

    private fun filterIgnoredScreenRegions(rawBlocks: List<DetectedTextBlock>): List<DetectedTextBlock> {
        val statusBarHeight = getStatusBarHeight()
        val controlBounds = overlayWindowManager.getControlBounds()

        return rawBlocks.filter { block ->
            val box = block.boundingBox
            // Filter out system status bar elements (e.g. clock, battery, wifi)
            if (box.top < statusBarHeight) return@filter false
            // Filter out the floating translator bar ("TRADUCIR", "LIMPIAR", etc.)
            if (controlBounds != null && android.graphics.Rect.intersects(box, controlBounds)) return@filter false
            true
        }
    }

    private fun pauseCapture() {
        Log.d(tag, "Pausing screen translation capture")
        _serviceState.value = ServiceState.PAUSED
        captureTickerJob?.cancel()
        captureTickerJob = null

        overlayWindowManager.clearCanvas()
        overlayWindowManager.updateControlState(ServiceState.PAUSED)
        updateNotification(ServiceState.PAUSED)
    }

    private fun resumeCapture() {
        Log.d(tag, "Resuming screen translation capture")
        _serviceState.value = ServiceState.RUNNING
        overlayWindowManager.updateControlState(ServiceState.RUNNING)
        updateNotification(ServiceState.RUNNING)

        if (!appPreferences.isManualMode) {
            startCaptureTicker()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(tag, "Configuration/Orientation change detected")
        captureEngine?.onConfigurationOrOrientationChanged()
    }

    private fun updateNotification(state: ServiceState) {
        val notification = buildNotification(state)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(state: ServiceState): Notification {
        val contentText = when (state) {
            ServiceState.RUNNING -> "Miku_AI activo. Usa la burbuja flotante para traducir."
            ServiceState.PAUSED -> "Miku_AI en pausa."
            ServiceState.STOPPED -> "Miku_AI detenido."
        }

        val openActivityIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val translateNowIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_TRANSLATE_ONCE
        }.let {
            PendingIntent.getService(this, 10, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val clearIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_CLEAR_OVERLAY
        }.let {
            PendingIntent.getService(this, 11, it, PendingIntent.FLAG_IMMUTABLE)
        }

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }.let {
            PendingIntent.getService(this, 1, it, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openActivityIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_search, "Traducir Pantalla", translateNowIntent)
            .addAction(android.R.drawable.ic_menu_delete, "Limpiar", clearIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d(tag, "Destroying ScreenCaptureService...")
        _serviceState.value = ServiceState.STOPPED

        captureTickerJob?.cancel()
        serviceScope.cancel()

        overlayWindowManager.removeOverlays()

        captureEngine?.release()
        captureEngine = null

        try {
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping mediaProjection", e)
        }
        mediaProjection = null

        ocrEngine.close()
        ttsManager.shutdown()

        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "screen_translation_channel"
        const val NOTIFICATION_ID = 9001

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        const val ACTION_STOP = "com.antigravity.translator.ACTION_STOP"
        const val ACTION_PAUSE = "com.antigravity.translator.ACTION_PAUSE"
        const val ACTION_RESUME = "com.antigravity.translator.ACTION_RESUME"
        const val ACTION_TRANSLATE_ONCE = "com.antigravity.translator.ACTION_TRANSLATE_ONCE"
        const val ACTION_CLEAR_OVERLAY = "com.antigravity.translator.ACTION_CLEAR_OVERLAY"
    }
}
