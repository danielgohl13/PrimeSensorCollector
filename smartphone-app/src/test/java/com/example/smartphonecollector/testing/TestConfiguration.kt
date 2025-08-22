package com.example.smartphonecollector.testing

import kotlinx.coroutines.test.TestScope
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Test configuration and rules for preventing infinite loops and managing timeouts
 */
object TestConfiguration {
    
    /**
     * Default timeout for unit tests
     */
    val UNIT_TEST_TIMEOUT = 10.seconds
    
    /**
     * Default timeout for integration tests
     */
    val INTEGRATION_TEST_TIMEOUT = 30.seconds
    
    /**
     * Default timeout for communication tests
     */
    val COMMUNICATION_TEST_TIMEOUT = 15.seconds
    
    /**
     * Create a timeout rule for JUnit tests
     */
    fun createTimeoutRule(timeout: Duration): TestRule {
        return Timeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }
    
    /**
     * Create a global timeout rule for all tests in a class
     */
    fun createGlobalTimeoutRule(): TestRule {
        return Timeout.builder()
            .withTimeout(UNIT_TEST_TIMEOUT.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .withLookingForStuckThread(true)
            .build()
    }
}

/**
 * Custom test rule that provides additional safety measures for coroutine tests
 */
class CoroutineTestRule(
    private val timeout: Duration = TestConfiguration.UNIT_TEST_TIMEOUT
) : TestRule {
    
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val testName = description.methodName
                println("Starting test: $testName with timeout: $timeout")
                
                val startTime = System.currentTimeMillis()
                
                try {
                    // Apply timeout to the test
                    val timeoutRule = Timeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    timeoutRule.apply(base, description).evaluate()
                    
                    val duration = System.currentTimeMillis() - startTime
                    println("Test completed: $testName in ${duration}ms")
                    
                } catch (e: Exception) {
                    val duration = System.currentTimeMillis() - startTime
                    println("Test failed: $testName after ${duration}ms - ${e.message}")
                    throw e
                }
            }
        }
    }
}

/**
 * Test utilities for monitoring test execution and preventing hangs
 */
object TestMonitor {
    
    private val activeTests = mutableSetOf<String>()
    
    /**
     * Register a test as starting
     */
    fun startTest(testName: String) {
        synchronized(activeTests) {
            activeTests.add(testName)
            println("Test started: $testName (Active tests: ${activeTests.size})")
        }
    }
    
    /**
     * Register a test as completed
     */
    fun endTest(testName: String) {
        synchronized(activeTests) {
            activeTests.remove(testName)
            println("Test ended: $testName (Active tests: ${activeTests.size})")
        }
    }
    
    /**
     * Get currently active tests (useful for debugging hangs)
     */
    fun getActiveTests(): Set<String> {
        return synchronized(activeTests) {
            activeTests.toSet()
        }
    }
    
    /**
     * Check if any tests are still running (useful in cleanup)
     */
    fun hasActiveTests(): Boolean {
        return synchronized(activeTests) {
            activeTests.isNotEmpty()
        }
    }
}

/**
 * Extension functions for TestScope to add safety measures
 */
fun TestScope.runWithSafetyChecks(
    maxTimeAdvancement: Long = 60000L, // 1 minute max
    maxIterations: Int = 1000,
    block: suspend TestScope.() -> Unit
) {
    var iterations = 0
    val startTime = currentTime
    
    try {
        block()
        
        // Safety check after test completion
        val timeAdvanced = currentTime - startTime
        if (timeAdvanced > maxTimeAdvancement) {
            throw AssertionError("Test advanced time by ${timeAdvanced}ms, which exceeds maximum of ${maxTimeAdvancement}ms")
        }
        
    } catch (e: Exception) {
        println("Test failed after $iterations iterations and ${currentTime - startTime}ms of simulated time")
        throw e
    }
}

/**
 * Safe coroutine launcher that prevents runaway coroutines in tests
 */
class SafeTestCoroutineScope(
    private val testScope: TestScope,
    private val maxCoroutines: Int = 100
) {
    private var launchedCoroutines = 0
    
    fun safeLaunch(block: suspend () -> Unit) {
        if (launchedCoroutines >= maxCoroutines) {
            throw IllegalStateException("Too many coroutines launched in test (max: $maxCoroutines)")
        }
        
        launchedCoroutines++
        testScope.launch {
            try {
                block()
            } finally {
                launchedCoroutines--
            }
        }
    }
    
    fun getActiveCoroutineCount(): Int = launchedCoroutines
}