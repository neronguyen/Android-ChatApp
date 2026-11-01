package io.github.neronguyen.chat.core.data.realtime

import io.github.neronguyen.chat.core.model.RealtimeConnectionState
import kotlinx.coroutines.flow.Flow

interface RealtimeConnectionManager {
    val connectionState: Flow<RealtimeConnectionState>
    fun initialize()
}
