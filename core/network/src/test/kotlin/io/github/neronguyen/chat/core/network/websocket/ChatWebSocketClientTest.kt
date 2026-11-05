package io.github.neronguyen.chat.core.network.websocket

import app.cash.turbine.test
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState
import io.github.neronguyenvn.nerochat.contract.chat.WsClientMessage
import io.github.neronguyenvn.nerochat.contract.chat.WsServerEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatWebSocketClientTest {

    private lateinit var server: MockWebServer
    private lateinit var okHttpClient: OkHttpClient
    private var serverWebSocket: WebSocket? = null

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        okHttpClient = OkHttpClient.Builder().build()
        serverWebSocket = null
    }

    @AfterTest
    fun tearDown() {
        serverWebSocket?.close(1000, "tearDown")
        server.shutdown()
    }

    private fun createClient(serverUrl: String = server.url("/").toString()): ChatWebSocketClient {
        return ChatWebSocketClient(
            okHttpClient = okHttpClient,
            baseUrl = serverUrl,
        )
    }

    private fun webSocketUpgradeResponse(
        onOpenCallback: ((WebSocket) -> Unit)? = null,
        onMessageCallback: ((WebSocket, String) -> Unit)? = null,
    ): MockResponse {
        return MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                serverWebSocket = webSocket
                onOpenCallback?.invoke(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessageCallback?.invoke(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }
        })
    }

    @Test
    fun `connect establishes websocket session and transitions to Connected`() = runTest {
        val serverReceivedOpen = LinkedBlockingQueue<Boolean>()
        server.enqueue(
            webSocketUpgradeResponse(
                onOpenCallback = { serverReceivedOpen.put(true) }
            )
        )

        val client = createClient()
        client.connectionState.test {
            assertEquals(SocketConnectionState.Disconnected, awaitItem())
            client.connect()
            assertEquals(SocketConnectionState.Connecting, awaitItem())
            assertEquals(SocketConnectionState.Connected, awaitItem())

            assertTrue(serverReceivedOpen.poll(2, TimeUnit.SECONDS) == true)
            client.disconnect()
            assertEquals(SocketConnectionState.Disconnected, awaitItem())
        }
    }

    @Test
    fun `sendMessage serializes WsClientMessage with correct type discriminator`() = runTest {
        val serverMessages = LinkedBlockingQueue<String>()
        server.enqueue(
            webSocketUpgradeResponse(
                onMessageCallback = { _, text -> serverMessages.put(text) }
            )
        )

        val client = createClient()
        client.connect()
        // Wait until connected
        client.connectionState.first { it is SocketConnectionState.Connected }

        val message = WsClientMessage.NewMessage(
            messageId = "0192e210-6310-753e-b816-17b5f25a3d70",
            roomId = "0192e210-6310-753e-b816-17b5f25a3d71",
            content = "Hello, world!"
        )

        val sent = client.sendMessage(message)
        assertTrue(sent)

        val received = serverMessages.poll(2, TimeUnit.SECONDS)
        assertTrue(received != null)
        assertTrue(received.contains("\"type\":\"new_message\""))
        assertTrue(received.contains("\"messageId\":\"0192e210-6310-753e-b816-17b5f25a3d70\""))
        assertTrue(received.contains("\"roomId\":\"0192e210-6310-753e-b816-17b5f25a3d71\""))
        assertTrue(received.contains("\"content\":\"Hello, world!\""))

        client.disconnect()
    }

    @Test
    fun `incomingEvents emits all WsServerEvent variants and ignores unparseable text`() = runTest {
        val serverReady = LinkedBlockingQueue<WebSocket>()

        server.enqueue(
            webSocketUpgradeResponse(
                onOpenCallback = { ws -> serverReady.put(ws) }
            )
        )

        val client = createClient()
        client.connect()
        val ws = serverReady.poll(2, TimeUnit.SECONDS)
        assertTrue(ws != null)

        client.incomingEvents.test {
            // 1. Invalid text (must be ignored)
            ws.send("not json at all")

            // 2. NewMessage event
            val newMessageJson = """{"type":"new_message","message":{"id":"018f3a2b-8a7e-7000-8000-000000000010","roomId":"018f3a2b-8a7e-7000-8000-000000000020","senderId":"018f3a2b-8a7e-7000-8000-000000000001","content":"Broadcast message","createdAt":"2026-11-03T20:00:00Z"}}"""
            ws.send(newMessageJson)
            val event1 = awaitItem()
            val newMsg = assertIs<WsServerEvent.NewMessage>(event1)
            assertEquals("018f3a2b-8a7e-7000-8000-000000000010", newMsg.message.id)
            assertEquals("Broadcast message", newMsg.message.content)

            // 3. MessageDeleted event
            val deletedJson = """{"type":"message_deleted","roomId":"018f3a2b-8a7e-7000-8000-000000000020","messageId":"018f3a2b-8a7e-7000-8000-000000000010"}"""
            ws.send(deletedJson)
            val event2 = awaitItem()
            val deleted = assertIs<WsServerEvent.MessageDeleted>(event2)
            assertEquals("018f3a2b-8a7e-7000-8000-000000000010", deleted.messageId)

            // 4. ParticipantJoined event
            val joinedJson = """{"type":"participant_joined","roomId":"018f3a2b-8a7e-7000-8000-000000000020","newUsers":[{"userId":"018f3a2b-8a7e-7000-8000-000000000002","email":"bob@example.com","displayName":"Bob"}]}"""
            ws.send(joinedJson)
            val event3 = awaitItem()
            val joined = assertIs<WsServerEvent.ParticipantJoined>(event3)
            assertEquals("018f3a2b-8a7e-7000-8000-000000000020", joined.roomId)
            assertEquals(1, joined.newUsers.size)
            assertEquals("bob@example.com", joined.newUsers[0].email)

            // 5. ParticipantLeft event
            val leftJson = """{"type":"participant_left","roomId":"018f3a2b-8a7e-7000-8000-000000000020","leftUser":{"userId":"018f3a2b-8a7e-7000-8000-000000000002","email":"bob@example.com","displayName":"Bob"}}"""
            ws.send(leftJson)
            val event4 = awaitItem()
            val left = assertIs<WsServerEvent.ParticipantLeft>(event4)
            assertEquals("018f3a2b-8a7e-7000-8000-000000000020", left.roomId)
            assertEquals("bob@example.com", left.leftUser.email)

            // 6. Error event
            val errorJson = """{"type":"error","error":"ChatServerError.ChatRoomNotFound","message":"Room not found","roomId":"018f3a2b-8a7e-7000-8000-000000000020"}"""
            ws.send(errorJson)
            val event5 = awaitItem()
            val errorEvent = assertIs<WsServerEvent.Error>(event5)
            assertEquals("ChatServerError.ChatRoomNotFound", errorEvent.error.value)
            assertEquals("Room not found", errorEvent.message)

            cancelAndIgnoreRemainingEvents()
        }

        client.disconnect()
    }

    @Test
    fun `server close code 1008 policy violation triggers AuthenticationFailed`() = runTest {
        val serverReady = LinkedBlockingQueue<WebSocket>()

        server.enqueue(
            webSocketUpgradeResponse(
                onOpenCallback = { ws -> serverReady.put(ws) }
            )
        )

        val client = createClient()
        client.connectionState.test {
            assertEquals(SocketConnectionState.Disconnected, awaitItem())
            client.connect()
            assertEquals(SocketConnectionState.Connecting, awaitItem())
            assertEquals(SocketConnectionState.Connected, awaitItem())

            val ws = serverReady.poll(2, TimeUnit.SECONDS)
            assertTrue(ws != null)
            // Server terminates session with 1008 POLICY_VIOLATION
            ws.close(1008, "Token expired")

            val state = awaitItem()
            val authFailed = assertIs<SocketConnectionState.AuthenticationFailed>(state)
            assertEquals(1008, authFailed.code)
            assertEquals("Token expired", authFailed.reason)
        }
    }

    @Test
    fun `server 401 during HTTP upgrade triggers AuthenticationFailed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val client = createClient()
        client.connectionState.test {
            assertEquals(SocketConnectionState.Disconnected, awaitItem())
            client.connect()
            assertEquals(SocketConnectionState.Connecting, awaitItem())

            val state = awaitItem()
            val authFailed = assertIs<SocketConnectionState.AuthenticationFailed>(state)
            assertEquals(401, authFailed.code)
        }
    }

    @Test
    fun `normal closure 1000 transitions to Disconnected`() = runTest {
        val serverReady = LinkedBlockingQueue<WebSocket>()

        server.enqueue(
            webSocketUpgradeResponse(
                onOpenCallback = { ws -> serverReady.put(ws) }
            )
        )

        val client = createClient()
        client.connectionState.test {
            assertEquals(SocketConnectionState.Disconnected, awaitItem())
            client.connect()
            assertEquals(SocketConnectionState.Connecting, awaitItem())
            assertEquals(SocketConnectionState.Connected, awaitItem())

            val ws = serverReady.poll(2, TimeUnit.SECONDS)
            assertTrue(ws != null)
            ws.close(1000, "Normal closure")

            assertEquals(SocketConnectionState.Disconnected, awaitItem())
        }
    }
}
