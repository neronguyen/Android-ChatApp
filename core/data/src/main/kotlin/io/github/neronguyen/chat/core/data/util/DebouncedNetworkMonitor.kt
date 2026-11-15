package io.github.neronguyen.chat.core.data.util

import io.github.neronguyen.chat.core.common.network.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Decorator around [ConnectivityManagerNetworkMonitor] that applies an asymmetric offline
 * debounce and shares a single upstream subscription across all `:core:data` consumers:
 * - Initial emission (whether `true` or `false`) and `false -> true` recoveries pass with 0ms delay.
 * - `true -> false` transitions debounce by [offlineDebounce] so transient radio handovers do not
 *   flap UI banners or WebSocket connections.
 */
@Singleton
internal class DebouncedNetworkMonitor internal constructor(
    delegate: NetworkMonitor,
    appScope: CoroutineScope,
    offlineDebounce: Duration = DEFAULT_OFFLINE_DEBOUNCE,
) : NetworkMonitor {

    @Inject
    constructor(
        delegate: ConnectivityManagerNetworkMonitor,
        @AppScope appScope: CoroutineScope,
    ) : this(
        delegate = delegate,
        appScope = appScope,
        offlineDebounce = DEFAULT_OFFLINE_DEBOUNCE,
    )

    override val isOnline: Flow<Boolean> = delegate.isOnline
        .debounceOffline(offlineDebounce)
        .shareIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = STOP_TIMEOUT_MS,
                replayExpirationMillis = 0L,
            ),
            replay = 1,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<Boolean>.debounceOffline(timeout: Duration): Flow<Boolean> = flow {
        var hasEmitted = false
        distinctUntilChanged()
            .transformLatest { online ->
                if (online || !hasEmitted) {
                    hasEmitted = true
                    emit(online)
                } else {
                    delay(timeout)
                    emit(false)
                }
            }
            .distinctUntilChanged()
            .collect(::emit)
    }

    companion object {
        internal val DEFAULT_OFFLINE_DEBOUNCE: Duration = 700.milliseconds
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
