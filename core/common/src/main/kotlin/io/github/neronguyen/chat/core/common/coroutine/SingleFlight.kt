package io.github.neronguyen.chat.core.common.coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Coalesces concurrent calls to an asynchronous operation so that concurrent callers
 * share a single in-flight execution, preventing duplicate concurrent workloads.
 *
 * - Concurrent callers reuse the active execution.
 * - Cancellation of a caller coroutine awaiting [execute] does not cancel the shared task in [scope].
 * - Finished or cancelled executions are cleared safely using [kotlinx.coroutines.NonCancellable].
 */
class SingleFlight<T>(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
) {
    private val keyedFlight = KeyedSingleFlight<Unit, T>(scope, dispatcher)

    suspend fun execute(block: suspend CoroutineScope.() -> T): T =
        keyedFlight.execute(Unit, block)
}
