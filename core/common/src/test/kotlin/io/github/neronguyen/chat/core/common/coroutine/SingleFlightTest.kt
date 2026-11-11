package io.github.neronguyen.chat.core.common.coroutine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SingleFlightTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `concurrent executions coalesce into single execution`() = runTest(testDispatcher) {
        val flight = SingleFlight<String>(testScope, testDispatcher)
        val gate = CompletableDeferred<String>()
        var executionCount = 0

        val call1 = async {
            flight.execute {
                executionCount++
                gate.await()
            }
        }
        val call2 = async {
            flight.execute {
                executionCount++
                gate.await()
            }
        }

        testScheduler.runCurrent()
        assertEquals(1, executionCount)

        gate.complete("success")
        testScheduler.advanceUntilIdle()

        assertEquals("success", call1.await())
        assertEquals("success", call2.await())
        assertEquals(1, executionCount)
    }

    @Test
    fun `subsequent execution after completion starts a new task`() = runTest(testDispatcher) {
        val flight = SingleFlight<Int>(testScope, testDispatcher)
        var counter = 0

        val res1 = flight.execute { ++counter }
        testScheduler.advanceUntilIdle()
        assertEquals(1, res1)
        assertEquals(1, counter)

        val res2 = flight.execute { ++counter }
        testScheduler.advanceUntilIdle()
        assertEquals(2, res2)
        assertEquals(2, counter)
    }

    @Test
    fun `caller cancellation does not cancel shared task or affect other callers`() =
        runTest(testDispatcher) {
            val flight = SingleFlight<String>(testScope, testDispatcher)
            val gate = CompletableDeferred<String>()
            var executionCount = 0

            val caller1 = launch {
                flight.execute {
                    executionCount++
                    gate.await()
                }
            }
            var caller2Result: String? = null
            val caller2 = launch {
                caller2Result = flight.execute {
                    executionCount++
                    gate.await()
                }
            }

            testScheduler.runCurrent()
            assertEquals(1, executionCount)

            // Cancel caller1
            caller1.cancel()
            testScheduler.runCurrent()

            // Complete shared task
            gate.complete("isolated")
            testScheduler.advanceUntilIdle()
            caller2.join()

            assertEquals("isolated", caller2Result)
            assertEquals(1, executionCount)
        }
}
