package io.github.neronguyen.chat.core.common.coroutine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class ConcurrencyExtensionsTest {

    @Test
    fun `mapConcurrent preserves order and bounds concurrency to maxConcurrency`() = runTest {
        val items = (1..10).toList()
        val activeCount = AtomicInteger(0)
        val maxObservedActive = AtomicInteger(0)

        val results = items.mapConcurrent(maxConcurrency = 3) { item ->
            val current = activeCount.incrementAndGet()
            maxObservedActive.updateAndGet { maxOf(it, current) }
            delay(50.milliseconds)
            activeCount.decrementAndGet()
            item * 10
        }

        assertEquals(items.map { it * 10 }, results)
        assertTrue(
            "Max active ($maxObservedActive) should be <= 3 and > 1",
            maxObservedActive.get() in (2..3),
        )
    }

    @Test
    fun `mapConcurrent on empty collection returns empty list immediately`() = runTest {
        var invoked = false
        val results = emptyList<Int>().mapConcurrent(maxConcurrency = 2) {
            invoked = true
            it
        }

        assertTrue(results.isEmpty())
        assertEquals(false, invoked)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mapConcurrent with non-positive maxConcurrency throws IllegalArgumentException`() =
        runTest {
            listOf(1, 2).mapConcurrent(maxConcurrency = 0) { it }
        }

    @Test
    fun `mapConcurrent executes up to maxConcurrency tasks simultaneously before gated completion`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val started = AtomicInteger(0)

            val job = launch {
                (1..5).mapConcurrent(maxConcurrency = 2) {
                    started.incrementAndGet()
                    gate.await()
                }
            }

            testScheduler.runCurrent()
            assertEquals(2, started.get())

            gate.complete(Unit)
            testScheduler.advanceUntilIdle()
            assertEquals(5, started.get())
            job.cancel()
        }
}
