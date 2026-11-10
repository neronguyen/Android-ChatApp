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
 * Coalesces concurrent calls to an asynchronous operation so that concurrent callers
 * share a single in-flight execution, preventing duplicate concurrent workloads.
 *
 * - Callers reuse [inFlight] if active ([Deferred.isActive]).
 * - Cancellation of a caller coroutine awaiting [execute] does not cancel the shared task in [scope].
 * - Finished or cancelled [inFlight] instances are cleared safely using [NonCancellable].
 */
class SingleFlight<T>(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var inFlight: Deferred<T>? = null

    suspend fun execute(block: suspend CoroutineScope.() -> T): T {
        val deferred = mutex.withLock {
            inFlight?.takeIf { it.isActive } ?: scope.async(dispatcher) {
                try {
                    block()
                } finally {
                    val currentJob = coroutineContext[Job]
                    withContext(NonCancellable) {
                        mutex.withLock {
                            if (inFlight === currentJob) {
                                inFlight = null
                            }
                        }
                    }
                }
            }.also { inFlight = it }
        }
        return deferred.await()
    }
}
