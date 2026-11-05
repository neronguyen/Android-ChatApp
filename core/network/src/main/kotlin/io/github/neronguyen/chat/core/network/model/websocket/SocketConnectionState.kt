package io.github.neronguyen.chat.core.network.model.websocket

sealed interface SocketConnectionState {
    data object Connecting : SocketConnectionState
    data object Connected : SocketConnectionState
    data object Disconnected : SocketConnectionState
    data class AuthenticationFailed(
        val code: Int = 1008,
        val reason: String? = null
    ) : SocketConnectionState
    data class Failed(val throwable: Throwable? = null) : SocketConnectionState
}
