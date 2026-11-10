package io.github.neronguyen.chat.core.common.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coalesces concurrent calls to an asynchronous operation keyed by [K] so that concurrent
 * callers sharing the same key share a single in-flight execution, preventing duplicate workloads.
 *
 * - Callers with the same key reuse an active execution ([Deferred.isActive]).
 * - Callers with different keys execute concurrently without blocking each other.
 * - Caller cancellation does not cancel the shared task running in [scope].
 * - When an execution finishes or fails, it is removed from the in-flight map safely using [NonCancellable].
 */
class KeyedSingleFlight<K, T>(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<K, Deferred<T>>()

    suspend fun execute(key: K, block: suspend CoroutineScope.() -> T): T {
        val deferred = mutex.withLock {
            inFlight[key]?.takeIf { it.isActive } ?: scope.async(dispatcher) {
                try {
                    block()
                } finally {
                    val currentJob = coroutineContext[Job]
                    withContext(NonCancellable) {
                        mutex.withLock {
                            if (inFlight[key] === currentJob) {
                                @Suppress("DeferredResultUnused")
                                inFlight.remove(key)
                            }
                        }
                    }
                }
            }.also { inFlight[key] = it }
        }
        return deferred.await()
    }
}
