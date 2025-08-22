package com.example.smartphonecollector.testing

import com.example.smartphonecollector.testing.TestTimeoutUtils.DEFAULT_TEST_TIMEOUT
import com.example.smartphonecollector.testing.TestTimeoutUtils.QUICK_TIMEOUT
import com.example.smartphonecollector.testing.TestTimeoutUtils.waitForCondition
import com.example.smartphonecollector.testing.TestTimeoutUtils.withTestTimeout
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests that demonstrate how to prevent infinite loops and handle timeouts in tests
 */
class InfiniteLoopPreventionTest {

    @Test(timeout = 5000) // JUnit timeout as backup
    fun `test with timeout prevents infinite loops`() = runTest(timeout = 3.seconds) {
        // This test demonstrates how to use timeouts to prevent infinite loops
        
        var counter = 0
        val maxIterations = 100
        
        // Simulate a potentially infinite loop with safety counter
        while (counter < maxIterations) {
            counter++
            delay(10) // Small delay to prevent tight loop
            
            // Break condition to prevent actual infinite loop
            if (counter >= 50) break
        }
        
        assertTrue("Counter should be reasonable", counter < maxIterations)
        assertEquals("Should break at expected point", 50, counter)
    }

    @Test(timeout = 3000)
    fun `waitForCondition prevents infinite waiting`() = runTest(timeout = 2.seconds) {
        var conditionMet = false
        
        // Start a coroutine that will set the condition after a delay
        launch {
            delay(500)
            conditionMet = true
        }
        
        // Wait for condition with timeout
        waitForCondition(
            timeout = 1.seconds,
            checkInterval = 50.milliseconds
        ) {
            conditionMet
        }
        
        assertTrue("Condition should be met", conditionMet)
    }

    @Test(timeout = 2000)
    fun `waitForCondition times out when condition never met`() = runTest(timeout = 1.seconds) {
        var exceptionThrown = false
        
        try {
            waitForCondition(
                timeout = 200.milliseconds,
                checkInterval = 50.milliseconds
            ) {
                false // Condition never met
            }
        } catch (e: AssertionError) {
            exceptionThrown = true
            assertTrue("Should mention timeout", e.message?.contains("timeout") == true)
        }
        
        assertTrue("Should throw timeout exception", exceptionThrown)
    }

    @Test(timeout = 5000)
    fun `periodic task simulation with controlled advancement`() = runTest(timeout = 3.seconds) {
        var taskExecutions = 0
        val maxExecutions = 5
        
        // Simulate a periodic task
        val job = launch {
            while (isActive && taskExecutions < maxExecutions) {
                taskExecutions++
                delay(1000) // 1 second interval
            }
        }
        
        // Advance time in controlled steps
        repeat(maxExecutions) {
            advanceTimeBy(1000)
            runCurrent() // Process pending coroutines
        }
        
        // Cancel the job to prevent it from running indefinitely
        job.cancel()
        
        assertEquals("Should execute expected number of times", maxExecutions, taskExecutions)
    }

    @Test(timeout = 3000)
    fun `coroutine cancellation prevents runaway processes`() = runTest(timeout = 2.seconds) {
        var isRunning = false
        var executionCount = 0
        
        val job = launch {
            isRunning = true
            try {
                while (isActive) {
                    executionCount++
                    delay(100)
                    
                    // Safety check to prevent actual infinite loop in test
                    if (executionCount > 20) {
                        break
                    }
                }
            } finally {
                isRunning = false
            }
        }
        
        // Let it run for a bit
        advanceTimeBy(1000)
        runCurrent()
        
        assertTrue("Job should be running", isRunning)
        assertTrue("Should have executed multiple times", executionCount > 5)
        
        // Cancel the job
        job.cancel()
        job.join() // Wait for cancellation to complete
        
        assertFalse("Job should be stopped", isRunning)
    }

    @Test(timeout = 4000)
    fun `withTestTimeout prevents hanging operations`() = runTest(timeout = 3.seconds) {
        var operationCompleted = false
        
        // This operation would hang without timeout
        try {
            withTestTimeout(timeout = 500.milliseconds) {
                // Simulate a hanging operation
                delay(1000) // This will timeout
                operationCompleted = true
            }
            fail("Should have timed out")
        } catch (e: TimeoutCancellationException) {
            // Expected timeout
            assertFalse("Operation should not have completed", operationCompleted)
        }
    }

    @Test(timeout = 5000)
    fun `safe time advancement prevents infinite advancement`() = runTest(timeout = 3.seconds) {
        val startTime = currentTime
        val advanceAmount = 10000L // 10 seconds
        
        // Use safe advancement that limits iterations
        TestTimeoutUtils.run {
            safeAdvanceTimeBy(advanceAmount, maxIterations = 50)
        }
        
        val actualAdvancement = currentTime - startTime
        assertTrue("Should advance time", actualAdvancement > 0)
        assertTrue("Should not exceed reasonable advancement", actualAdvancement <= advanceAmount)
    }

    @Test(timeout = 3000)
    fun `resource cleanup prevents memory leaks in tests`() = runTest(timeout = 2.seconds) {
        val resources = mutableListOf<TestResource>()
        
        try {
            // Create some test resources
            repeat(5) {
                resources.add(TestResource("resource_$it"))
            }
            
            // Simulate some work
            advanceTimeBy(1000)
            runCurrent()
            
            assertEquals("Should have created resources", 5, resources.size)
            
        } finally {
            // Always clean up resources
            resources.forEach { it.cleanup() }
            resources.clear()
        }
        
        assertTrue("Resources should be cleaned up", resources.isEmpty())
    }

    @Test(timeout = 2000)
    fun `bounded retry logic prevents infinite retries`() = runTest(timeout = 1.seconds) {
        var attemptCount = 0
        val maxAttempts = 3
        var success = false
        
        // Simulate retry logic with bounds
        while (attemptCount < maxAttempts && !success) {
            attemptCount++
            delay(100) // Simulate operation delay
            
            // Simulate failure for first 2 attempts, success on 3rd
            success = attemptCount >= 3
        }
        
        assertEquals("Should make expected number of attempts", 3, attemptCount)
        assertTrue("Should eventually succeed", success)
    }

    @Test(timeout = 3000)
    fun `state monitoring with timeout prevents infinite observation`() = runTest(timeout = 2.seconds) {
        var stateValue = 0
        val targetValue = 5
        
        // Start a coroutine that changes state
        val stateChanger = launch {
            repeat(10) {
                delay(100)
                stateValue++
            }
        }
        
        // Monitor state with timeout
        var monitoringComplete = false
        val monitor = launch {
            while (stateValue < targetValue && isActive) {
                delay(50)
            }
            monitoringComplete = true
        }
        
        // Wait for monitoring to complete or timeout
        withTimeout(1000) {
            monitor.join()
        }
        
        assertTrue("Monitoring should complete", monitoringComplete)
        assertTrue("State should reach target", stateValue >= targetValue)
        
        // Cleanup
        stateChanger.cancel()
        monitor.cancel()
    }

    /**
     * Test resource class for cleanup demonstration
     */
    private class TestResource(val name: String) {
        private var isCleanedUp = false
        
        fun cleanup() {
            isCleanedUp = true
        }
        
        fun isCleanedUp() = isCleanedUp
    }
}