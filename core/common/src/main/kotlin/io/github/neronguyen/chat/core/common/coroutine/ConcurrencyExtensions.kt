package io.github.neronguyen.chat.core.common.coroutine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Transforms elements of this [Iterable] concurrently using [supervisorScope],
 * bounding maximum active concurrent coroutines to [maxConcurrency].
 *
 * - Preserves input iteration order in the returned [List].
 * - Uses [supervisorScope] so individual child failures do not automatically cancel sibling tasks
 *   when [transform] returns a domain result type (such as `Either`).
 */
suspend fun <T, R> Iterable<T>.mapConcurrent(
    maxConcurrency: Int,
    transform: suspend CoroutineScope.(T) -> R,
): List<R> {
    require(maxConcurrency > 0) { "maxConcurrency must be > 0, but was $maxConcurrency" }
    val elements = (this as? Collection<T>) ?: toList()
    if (elements.isEmpty()) return emptyList()

    val semaphore = Semaphore(maxConcurrency)
    return supervisorScope {
        elements.map { item ->
            async {
                semaphore.withPermit {
                    transform(item)
                }
            }
        }.awaitAll()
    }
}
