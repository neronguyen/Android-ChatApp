package io.github.neronguyen.chat.core.network.websocket

import io.github.neronguyen.chat.core.network.BuildConfig
import io.github.neronguyen.chat.core.network.ChatRealtimeDataSource
import io.github.neronguyen.chat.core.network.di.RealtimeOkHttp
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState.Connected
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState.Connecting
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState.Disconnected
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState.Failed
import io.github.neronguyenvn.nerochat.contract.chat.WsClientMessage
import io.github.neronguyenvn.nerochat.contract.chat.WsServerEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalAtomicApi::class)
@Singleton
internal class ChatWebSocketClient @Inject constructor(
    @RealtimeOkHttp private val okHttpClient: OkHttpClient,
) : ChatRealtimeDataSource {

    private val json = Json {
        classDiscriminator = WsServerEvent.CLASS_DISCRIMINATOR
        ignoreUnknownKeys = true
    }

    private val connectMutex = Mutex()
    private val webSocket = AtomicReference<WebSocket?>(null)

    private val _connectionState =
        MutableStateFlow<SocketConnectionState>(Disconnected)
    override val connectionState: StateFlow<SocketConnectionState> = _connectionState.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<WsServerEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incomingEvents: Flow<WsServerEvent> = _incomingEvents.asSharedFlow()

    override suspend fun connect() = connectMutex.withLock {
        if (webSocket.load() != null) {
            return@withLock
        }

        val currentState = _connectionState.value
        if (currentState is Connected || currentState is Connecting) {
            return@withLock
        }

        _connectionState.value = Connecting

        val rawBaseUrl = BuildConfig.BASE_URL.ifEmpty { "http://10.0.2.2:8080/" }
        val wsBaseUrl = when {
            rawBaseUrl.startsWith("https://") -> rawBaseUrl.replaceFirst("https://", "wss://")
            rawBaseUrl.startsWith("http://") -> rawBaseUrl.replaceFirst("http://", "ws://")
            else -> rawBaseUrl
        }.removeSuffix("/")

        val url = "$wsBaseUrl/ws/chat"

        val request = Request.Builder()
            .url(url)
            .build()

        val newSocket = okHttpClient.newWebSocket(request, createListener())
        webSocket.store(newSocket)
    }

    override suspend fun sendMessage(
        message: WsClientMessage
    ): Boolean {
        val socket = webSocket.load() ?: return false
        if (_connectionState.value !is Connected) {
            return false
        }

        return try {
            val text = json.encodeToString(message)
            socket.send(text)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun disconnect() = connectMutex.withLock {
        // exchange(null) retrieves the current socket and atomically sets it to null.
        // When the old socket subsequently calls onClosed/onFailure, compareAndSet(ws, null) will return false and be ignored.
        val activeSocket = webSocket.exchange(null)
        activeSocket?.close(NORMAL_CLOSURE_STATUS, "Client requested disconnect")
        _connectionState.value = Disconnected
    }

    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (this@ChatWebSocketClient.webSocket.load() !== webSocket) return
            _connectionState.value = Connected
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (this@ChatWebSocketClient.webSocket.load() !== webSocket) return
            try {
                val event = json.decodeFromString<WsServerEvent>(text)
                _incomingEvents.tryEmit(event)
            } catch (_: Exception) {
                // Ignore unparseable frames
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (this@ChatWebSocketClient.webSocket.compareAndSet(
                    expectedValue = webSocket,
                    newValue = null
                )
            ) {
                _connectionState.value = Failed(t)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (this@ChatWebSocketClient.webSocket.compareAndSet(
                    expectedValue = webSocket,
                    newValue = null
                )
            ) {
                _connectionState.value = Disconnected
            }
        }
    }

    companion object {
        private const val NORMAL_CLOSURE_STATUS = 1000
    }
}
