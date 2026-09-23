package com.antigravity.translator

import com.antigravity.translator.telemetry.AppPerformanceTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppPerformanceTrackerTest {

    @Test
    fun testAppPerformanceTrackerExecutesBlockAndReturnsValue() {
        val result = AppPerformanceTracker.trace("test_computation") { trace ->
            trace.putAttribute("env", "unit_test")
            trace.putMetric("metric_count", 42L)
            trace.incrementMetric("metric_count", 8L)
            100 + 200
        }

        assertEquals(300, result)
    }

    @Test
    fun testAppPerformanceTrackerHandlesNullTraceGracefully() {
        var executed = false
        AppPerformanceTracker.trace("test_null_trace_safe_calls") { trace ->
            trace.putAttribute("any_key", "any_value")
            trace.putMetric("test_metric", 1L)
            trace.incrementMetric("test_metric", 2L)
            executed = true
        }
        assertTrue(executed)
    }

    @Test(expected = IllegalStateException::class)
    fun testAppPerformanceTrackerPropagatesBlockExceptions() {
        AppPerformanceTracker.trace("test_throwing_block") { trace ->
            trace.putAttribute("status", "failing")
            throw IllegalStateException("Expected failure inside block")
        }
    }
}
