package io.github.neronguyen.chat.core.common.coroutine

import javax.inject.Inject
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Calculates exponential backoff delay with jitter.
 *
 * Designed for reconnection loops and transient failure retries.
 *
 * @property initialDelay The base delay for the first backoff attempt.
 * @property maxDelay The upper bound cap for calculated delay.
 * @property factor Multiplier for exponential scaling (defaults to 2.0).
 * @property maxExponentialAttempts Attempt index cap where exponential scaling saturates.
 * @property jitterRatio Fraction of capped delay applied symmetrically as jitter (defaults to 0.2 for ±20%).
 * @property randomProvider Provider for normalized random values in [-1.0, 1.0].
 */
class ExponentialBackoff(
    val initialDelay: Duration = DEFAULT_INITIAL_DELAY,
    val maxDelay: Duration = DEFAULT_MAX_DELAY,
    val factor: Double = DEFAULT_FACTOR,
    val maxExponentialAttempts: Int = DEFAULT_MAX_EXPONENTIAL_ATTEMPTS,
    val jitterRatio: Double = DEFAULT_JITTER_RATIO,
    private val randomProvider: () -> Double = { Random.nextDouble(-1.0, 1.0) },
) {
    @Inject
    constructor() : this(
        initialDelay = DEFAULT_INITIAL_DELAY,
        maxDelay = DEFAULT_MAX_DELAY,
        factor = DEFAULT_FACTOR,
        maxExponentialAttempts = DEFAULT_MAX_EXPONENTIAL_ATTEMPTS,
        jitterRatio = DEFAULT_JITTER_RATIO,
        randomProvider = { Random.nextDouble(-1.0, 1.0) },
    )

    init {
        require(initialDelay > Duration.ZERO) { "initialDelay must be positive" }
        require(maxDelay >= initialDelay) { "maxDelay must be >= initialDelay" }
        require(factor >= 1.0) { "factor must be >= 1.0" }
        require(maxExponentialAttempts >= 0) { "maxExponentialAttempts must be >= 0" }
        require(jitterRatio in (0.0..1.0)) { "jitterRatio must be in [0.0, 1.0]" }
    }

    /**
     * Calculates the backoff duration for the given 0-indexed [attempt].
     */
    fun calculateDelay(attempt: Int): Duration {
        require(attempt >= 0) { "attempt must be >= 0" }
        val cappedAttempt = attempt.coerceAtMost(maxExponentialAttempts)
        val expMultiplier = factor.pow(cappedAttempt.toDouble())
        val expDelayMs = (initialDelay.inWholeMilliseconds * expMultiplier).toLong()
        val cappedDelayMs = expDelayMs.coerceAtMost(maxDelay.inWholeMilliseconds)
        val jitterMs = (cappedDelayMs * jitterRatio * randomProvider()).toLong()
        val resultMs = (cappedDelayMs + jitterMs).coerceAtLeast(initialDelay.inWholeMilliseconds)
        return resultMs.milliseconds
    }

    companion object {
        val DEFAULT_INITIAL_DELAY: Duration = 1_000.milliseconds
        val DEFAULT_MAX_DELAY: Duration = 30_000.milliseconds
        const val DEFAULT_FACTOR: Double = 2.0
        const val DEFAULT_MAX_EXPONENTIAL_ATTEMPTS: Int = 5
        const val DEFAULT_JITTER_RATIO: Double = 0.2
    }
}
