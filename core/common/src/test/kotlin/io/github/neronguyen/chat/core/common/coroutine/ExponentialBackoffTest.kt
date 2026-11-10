package io.github.neronguyen.chat.core.common.coroutine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ExponentialBackoffTest {

    @Test
    fun `calculateDelay with zero jitter calculates exact powers of two`() {
        val backoff = ExponentialBackoff(
            initialDelay = 1.seconds,
            maxDelay = 30.seconds,
            factor = 2.0,
            jitterRatio = 0.0,
        )

        assertEquals(1.seconds, backoff.calculateDelay(0))
        assertEquals(2.seconds, backoff.calculateDelay(1))
        assertEquals(4.seconds, backoff.calculateDelay(2))
        assertEquals(8.seconds, backoff.calculateDelay(3))
        assertEquals(16.seconds, backoff.calculateDelay(4))
        assertEquals(30.seconds, backoff.calculateDelay(5)) // 32s capped to 30s
        assertEquals(30.seconds, backoff.calculateDelay(6)) // capped at attempt 5 and 30s
    }

    @Test
    fun `calculateDelay with maximum negative jitter floors at initialDelay`() {
        val backoff = ExponentialBackoff(
            initialDelay = 1_000.milliseconds,
            maxDelay = 30_000.milliseconds,
            jitterRatio = 0.5,
            randomProvider = { -1.0 },
        )

        // Attempt 0: 1000 - 500 = 500, but floored at initialDelay 1000ms
        assertEquals(1_000.milliseconds, backoff.calculateDelay(0))

        // Attempt 1: 2000 - 1000 = 1000ms
        assertEquals(1_000.milliseconds, backoff.calculateDelay(1))

        // Attempt 2: 4000 - 2000 = 2000ms
        assertEquals(2_000.milliseconds, backoff.calculateDelay(2))
    }

    @Test
    fun `calculateDelay with maximum positive jitter stays within bounds`() {
        val backoff = ExponentialBackoff(
            initialDelay = 1_000.milliseconds,
            maxDelay = 30_000.milliseconds,
            jitterRatio = 0.2,
            randomProvider = { 1.0 },
        )

        // Attempt 1: 2000 + 400 = 2400ms
        assertEquals(2_400.milliseconds, backoff.calculateDelay(1))

        // Attempt 2: 4000 + 800 = 4800ms
        assertEquals(4_800.milliseconds, backoff.calculateDelay(2))

        // Attempt 5: 30000 + 6000 = 36000ms
        assertEquals(36_000.milliseconds, backoff.calculateDelay(5))
    }

    @Test
    fun `calculateDelay stays within jitter tolerance with random values`() {
        val backoff = ExponentialBackoff(
            initialDelay = 1_000.milliseconds,
            maxDelay = 30_000.milliseconds,
            jitterRatio = 0.2,
        )

        for (attempt in 0..10) {
            val delay = backoff.calculateDelay(attempt)
            assertTrue("Delay $delay should be >= 1s", delay >= 1.seconds)
            assertTrue("Delay $delay should be <= 36s", delay <= 36.seconds)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative attempt throws IllegalArgumentException`() {
        val backoff = ExponentialBackoff()
        backoff.calculateDelay(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid jitterRatio throws IllegalArgumentException`() {
        ExponentialBackoff(jitterRatio = 1.5)
    }
}
