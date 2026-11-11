package io.github.neronguyen.chat.core.data.util

import kotlinx.coroutines.flow.Flow

internal interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
