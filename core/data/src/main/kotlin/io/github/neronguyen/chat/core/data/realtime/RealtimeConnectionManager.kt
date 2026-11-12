package io.github.neronguyen.chat.core.data.realtime

import io.github.neronguyen.chat.core.model.RealtimeConnectionState
import kotlinx.coroutines.flow.StateFlow

interface RealtimeConnectionManager {
    val connectionState: StateFlow<RealtimeConnectionState>
    fun initialize()
    fun reconnect()
}
