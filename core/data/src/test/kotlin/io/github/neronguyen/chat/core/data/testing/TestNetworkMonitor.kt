package io.github.neronguyen.chat.core.data.testing

import io.github.neronguyen.chat.core.data.util.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TestNetworkMonitor(initialOnline: Boolean = true) : NetworkMonitor {
    private val _isOnline = MutableStateFlow(initialOnline)
    override val isOnline: Flow<Boolean> = _isOnline.asStateFlow()

    fun setConnected(isConnected: Boolean) {
        _isOnline.value = isConnected
    }
}
