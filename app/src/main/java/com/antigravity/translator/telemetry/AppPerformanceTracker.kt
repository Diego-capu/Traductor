package com.antigravity.translator.telemetry

import android.util.Log
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

/**
 * Thread-safe, resilient performance monitoring wrapper for Firebase Performance Monitoring.
 * Automatically wraps execution blocks in Firebase Traces when available,
 * and fails silently/gracefully without overhead or exceptions in test/offline environments.
 */
object AppPerformanceTracker {

    @PublishedApi
    internal const val TAG = "AppPerfTracker"

    /**
     * Executes the given [block] within a Firebase Performance [Trace] with the specified [traceName].
     * If Firebase is uninitialized or unavailable (e.g. headless unit tests),
     * the block executes normally with a safe wrapper.
     */
    inline fun <T> trace(traceName: String, block: (TraceWrapper) -> T): T {
        val traceInstance: Trace? = try {
            FirebasePerformance.getInstance().newTrace(traceName).apply {
                start()
            }
        } catch (e: Throwable) {
            // FirebaseApp is not initialized or running in unit test
            null
        }

        val wrapper = TraceWrapper(traceInstance)
        return try {
            block(wrapper)
        } finally {
            try {
                traceInstance?.stop()
            } catch (e: Throwable) {
                Log.w(TAG, "Error stopping trace $traceName: ${e.message}")
            }
        }
    }

    /**
     * Lightweight wrapper over Firebase [Trace] to safely record metrics and attributes.
     */
    class TraceWrapper(val rawTrace: Trace?) {
        fun putAttribute(attributeName: String, value: String) {
            try {
                rawTrace?.putAttribute(attributeName, value)
            } catch (ignored: Throwable) {}
        }

        fun putMetric(metricName: String, value: Long) {
            try {
                rawTrace?.putMetric(metricName, value)
            } catch (ignored: Throwable) {}
        }

        fun incrementMetric(metricName: String, incrementBy: Long = 1L) {
            try {
                rawTrace?.incrementMetric(metricName, incrementBy)
            } catch (ignored: Throwable) {}
        }
    }
}
