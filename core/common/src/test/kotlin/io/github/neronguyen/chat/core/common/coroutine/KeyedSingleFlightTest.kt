package io.github.neronguyen.chat.core.common.coroutine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyedSingleFlightTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `concurrent executions with same key coalesce into single execution`() =
        runTest(testDispatcher) {
            val flight = KeyedSingleFlight<String, String>(testScope, testDispatcher)
            val gate = CompletableDeferred<String>()
            var executionCount = 0

            val call1 = async {
                flight.execute("keyA") {
                    executionCount++
                    gate.await()
                }
            }
            val call2 = async {
                flight.execute("keyA") {
                    executionCount++
                    gate.await()
                }
            }

            testScheduler.runCurrent()
            assertEquals(1, executionCount)

            gate.complete("success-A")
            testScheduler.advanceUntilIdle()

            assertEquals("success-A", call1.await())
            assertEquals("success-A", call2.await())
            assertEquals(1, executionCount)
        }

    @Test
    fun `concurrent executions with different keys execute concurrently`() =
        runTest(testDispatcher) {
            val flight = KeyedSingleFlight<String, String>(testScope, testDispatcher)
            val gateA = CompletableDeferred<String>()
            val gateB = CompletableDeferred<String>()
            var executionCountA = 0
            var executionCountB = 0

            val callA = async {
                flight.execute("keyA") {
                    executionCountA++
                    gateA.await()
                }
            }
            val callB = async {
                flight.execute("keyB") {
                    executionCountB++
                    gateB.await()
                }
            }

            testScheduler.runCurrent()
            assertEquals(1, executionCountA)
            assertEquals(1, executionCountB)

            gateA.complete("result-A")
            gateB.complete("result-B")
            testScheduler.advanceUntilIdle()

            assertEquals("result-A", callA.await())
            assertEquals("result-B", callB.await())
        }

    @Test
    fun `subsequent execution with same key after completion starts a new task`() =
        runTest(testDispatcher) {
            val flight = KeyedSingleFlight<String, Int>(testScope, testDispatcher)
            var counter = 0

            val res1 = flight.execute("key1") { ++counter }
            testScheduler.advanceUntilIdle()
            assertEquals(1, res1)
            assertEquals(1, counter)

            val res2 = flight.execute("key1") { ++counter }
            testScheduler.advanceUntilIdle()
            assertEquals(2, res2)
            assertEquals(2, counter)
        }

    @Test
    fun `caller cancellation does not cancel shared task for remaining callers`() =
        runTest(testDispatcher) {
            val flight = KeyedSingleFlight<String, String>(testScope, testDispatcher)
            val gate = CompletableDeferred<String>()
            var executed = false

            val caller1Job = launch {
                flight.execute("key") {
                    executed = true
                    gate.await()
                }
            }
            val caller2 = async {
                flight.execute("key") {
                    executed = true
                    gate.await()
                }
            }

            testScheduler.runCurrent()
            assertTrue(executed)

            // Cancel caller 1
            caller1Job.cancel()
            testScheduler.runCurrent()

            // Complete the underlying task
            gate.complete("shared-result")
            testScheduler.advanceUntilIdle()

            // Caller 2 should successfully receive the result
            assertEquals("shared-result", caller2.await())
        }

    @Test
    fun `failure in task removes key from in-flight and allows retry`() = runTest(testDispatcher) {
        val supervisorScope =
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + testDispatcher)
        val flight = KeyedSingleFlight<String, String>(supervisorScope, testDispatcher)
        var attempt = 0

        val call1 = supervisorScope.async {
            flight.execute("key") {
                attempt++
                if (attempt == 1) throw IllegalStateException("transient error")
                "success-retry"
            }
        }

        testScheduler.advanceUntilIdle()
        assertTrue(call1.isCancelled)

        val res2 = flight.execute("key") {
            attempt++
            "success-retry"
        }

        testScheduler.advanceUntilIdle()
        assertEquals("success-retry", res2)
        assertEquals(2, attempt)
    }
}
