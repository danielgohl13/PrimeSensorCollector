package com.example.smartphonecollector.testing

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

/**
 * Utility functions for handling test timeouts and preventing infinite loops
 */
object TestTimeoutUtils {
    
    /**
     * Default test timeout for coroutine tests
     */
    val DEFAULT_TEST_TIMEOUT = 10.seconds
    
    /**
     * Timeout for quick operations
     */
    val QUICK_TIMEOUT = 2.seconds
    
    /**
     * Timeout for longer integration tests
     */
    val INTEGRATION_TIMEOUT = 30.seconds
    
    /**
     * Run a test block with a timeout to prevent infinite loops
     */
    suspend fun <T> withTestTimeout(
        timeout: Duration = DEFAULT_TEST_TIMEOUT,
        block: suspend CoroutineScope.() -> T
    ): T {
        return withTimeout(timeout.inWholeMilliseconds) {
            block()
        }
    }
    
    /**
     * Run a test with automatic cancellation after timeout
     */
    fun runTestWithTimeout(
        timeout: Duration = DEFAULT_TEST_TIMEOUT,
        testBody: suspend TestScope.() -> Unit
    ) = runTest(timeout = timeout) {
        testBody()
    }
    
    /**
     * Wait for a condition to be true with timeout
     */
    suspend fun waitForCondition(
        timeout: Duration = QUICK_TIMEOUT,
        checkInterval: Duration = 100.milliseconds,
        condition: () -> Boolean
    ) {
        val startTime = System.currentTimeMillis()
        val timeoutMs = timeout.inWholeMilliseconds
        
        while (!condition()) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                fail("Condition not met within timeout of ${timeout}")
            }
            delay(checkInterval.inWholeMilliseconds)
        }
    }
    
    /**
     * Wait for a StateFlow value to change with timeout
     */
    suspend fun <T> waitForStateFlowValue(
        stateFlow: kotlinx.coroutines.flow.StateFlow<T>,
        expectedValue: T,
        timeout: Duration = QUICK_TIMEOUT
    ) {
        withTimeout(timeout.inWholeMilliseconds) {
            stateFlow.first { it == expectedValue }
        }
    }
    
    /**
     * Collect StateFlow values for a limited time to prevent infinite collection
     */
    suspend fun <T> collectStateFlowWithTimeout(
        stateFlow: kotlinx.coroutines.flow.StateFlow<T>,
        timeout: Duration = QUICK_TIMEOUT,
        collector: (T) -> Unit
    ) {
        withTimeout(timeout.inWholeMilliseconds) {
            stateFlow.collect { value ->
                collector(value)
            }
        }
    }
    
    /**
     * Advance test time with safety checks to prevent infinite advancement
     */
    suspend fun TestScope.safeAdvanceTimeBy(
        delayTimeMillis: Long,
        maxIterations: Int = 100
    ) {
        var iterations = 0
        val targetTime = currentTime + delayTimeMillis
        
        while (currentTime < targetTime && iterations < maxIterations) {
            val remainingTime = targetTime - currentTime
            val stepSize = minOf(remainingTime, 1000L) // Advance in 1-second steps max
            advanceTimeBy(stepSize)
            iterations++
        }
        
        if (iterations >= maxIterations) {
            fail("Test time advancement exceeded maximum iterations ($maxIterations)")
        }
    }
    
    /**
     * Run until idle with timeout protection
     */
    suspend fun TestScope.safeRunUntilIdle(timeout: Duration = QUICK_TIMEOUT) {
        withTimeout(timeout.inWholeMilliseconds) {
            runCurrent()
        }
    }
}