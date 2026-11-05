package io.github.neronguyen.chat.core.network.retrofit

import arrow.core.Either
import io.github.neronguyen.chat.core.network.interceptor.AuthInterceptor
import io.github.neronguyen.chat.core.network.interceptor.TokenProvider
import io.github.neronguyen.chat.core.network.util.NetworkError
import io.github.neronguyenvn.nerochat.contract.auth.AuthenticatedUserDto
import io.github.neronguyenvn.nerochat.contract.auth.LoginRequest
import io.github.neronguyenvn.nerochat.contract.auth.error.AuthServerError
import io.github.neronguyenvn.nerochat.contract.chat.ChatMessageSliceDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatRoomDto
import io.github.neronguyenvn.nerochat.contract.chat.error.ChatServerError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RetrofitNetworkWireTest {

    private lateinit var server: MockWebServer
    private val networkJson = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `RetrofitAuthNetwork login sends proper request and parses response`() = runTest {
        val responseBody = """
            {
              "accessToken": "access-token-123",
              "refreshToken": "refresh-token-456",
              "user": {
                "id": "0192e210-6310-753e-b816-17b5f25a3d70",
                "email": "user@example.com",
                "displayName": "User",
                "isEmailVerified": true
              }
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody)
        )

        val okHttpClient = OkHttpClient.Builder().build()
        val authNetwork = RetrofitAuthNetwork(
            networkJson = networkJson,
            baseClient = { okHttpClient },
            refreshClient = { okHttpClient },
            baseUrl = server.url("/").toString()
        )

        val result = authNetwork.login(LoginRequest("user@example.com", "Password123!"))
        val right = assertIs<Either.Right<AuthenticatedUserDto>>(result)
        val userDto = right.value
        assertEquals("access-token-123", userDto.accessToken)
        assertEquals("0192e210-6310-753e-b816-17b5f25a3d70", userDto.user.id)
        assertTrue(userDto.user.isEmailVerified)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/auth/login", recorded.path)
        assertNull(recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("\"email\":\"user@example.com\""))
    }

    @Test
    fun `RetrofitAuthNetwork login maps 401 ApiErrorDto with flat ErrorCode`() = runTest {
        val errorBody = """
            {
              "error": "AuthServerError.InvalidCredentials",
              "message": "Invalid email or password",
              "details": []
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody)
        )

        val okHttpClient = OkHttpClient.Builder().build()
        val authNetwork = RetrofitAuthNetwork(
            networkJson = networkJson,
            baseClient = { okHttpClient },
            refreshClient = { okHttpClient },
            baseUrl = server.url("/").toString()
        )

        val result = authNetwork.login(LoginRequest("user@example.com", "WrongPassword!"))
        val left = assertIs<Either.Left<NetworkError<AuthServerError.Login>>>(result)
        val networkError = assertIs<NetworkError.Business<AuthServerError.Login>>(left.value)
        assertEquals(AuthServerError.InvalidCredentials, networkError.error)
    }

    @Test
    fun `RetrofitChatNetwork injects Authorization header and parses list of rooms`() = runTest {
        val roomsBody = """
            [
              {
                "id": "0192e210-6310-753e-b816-17b5f25a3d71",
                "name": "General Room",
                "type": "GROUP",
                "creator": {
                  "userId": "0192e210-6310-753e-b816-17b5f25a3d70",
                  "email": "creator@example.com",
                  "displayName": "Creator",
                  "profilePictureUrl": null
                },
                "participants": [],
                "lastMessage": null,
                "lastActivityAt": "2026-11-03T20:00:00Z"
              }
            ]
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(roomsBody)
        )

        val tokenProvider = TokenProvider { "bearer-token-abc" }
        val authInterceptor = AuthInterceptor(tokenProvider)
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        val chatNetwork = RetrofitChatNetwork(
            networkJson = networkJson,
            okHttpClient = { okHttpClient },
            baseUrl = server.url("/").toString()
        )

        val result = chatNetwork.getChatRoomsForUser()
        val right = assertIs<Either.Right<List<ChatRoomDto>>>(result)
        val rooms = right.value
        assertEquals(1, rooms.size)
        assertEquals("General Room", rooms[0].name)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/chat/rooms", recorded.path)
        assertEquals("Bearer bearer-token-abc", recorded.getHeader("Authorization"))
    }

    @Test
    fun `RetrofitChatNetwork getMessages includes roomId and before query param`() = runTest {
        val sliceBody = """
            {
              "messages": [],
              "hasMore": false
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(sliceBody)
        )

        val okHttpClient = OkHttpClient.Builder().build()
        val chatNetwork = RetrofitChatNetwork(
            networkJson = networkJson,
            okHttpClient = { okHttpClient },
            baseUrl = server.url("/").toString()
        )

        val instant = Instant.fromEpochMilliseconds(1730000000000L)
        val result = chatNetwork.getMessagesForChatRoom(
            roomId = "room-99",
            before = instant
        )

        val right = assertIs<Either.Right<ChatMessageSliceDto>>(result)
        assertEquals(0, right.value.messages.size)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path?.startsWith("/api/chat/rooms/room-99/messages?before=") == true)
    }

    @Test
    fun `RetrofitChatNetwork maps ChatServerError RoomNotFound`() = runTest {
        val errorBody = """
            {
              "error": "ChatServerError.ChatRoomNotFound",
              "message": "Chat room was not found",
              "details": []
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody)
        )

        val okHttpClient = OkHttpClient.Builder().build()
        val chatNetwork = RetrofitChatNetwork(
            networkJson = networkJson,
            okHttpClient = { okHttpClient },
            baseUrl = server.url("/").toString()
        )

        val result = chatNetwork.getChatRoom("non-existent-room")
        val left = assertIs<Either.Left<NetworkError<ChatServerError.GetRoom>>>(result)
        val networkError = assertIs<NetworkError.Business<ChatServerError.GetRoom>>(left.value)
        assertEquals(ChatServerError.ChatRoomNotFound, networkError.error)
    }

    @Test
    fun `TokenAuthenticator refreshes token on 401 and transparently retries request`() = runTest {
        // First request receives 401 Unauthorized
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"AuthServerError.InvalidCredentials","message":"Stale","details":[]}""")
        )

        // Retry request receives 200 OK
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        var currentToken = "stale-token"
        val tokenProvider = TokenProvider { currentToken }
        val tokenRefresher = io.github.neronguyen.chat.core.network.interceptor.TokenRefresher { _ ->
            currentToken = "fresh-token-xyz"
            currentToken
        }

        val authInterceptor = AuthInterceptor(tokenProvider)
        val tokenAuthenticator = io.github.neronguyen.chat.core.network.interceptor.TokenAuthenticator(
            tokenProvider = tokenProvider,
            tokenRefresherProvider = { tokenRefresher }
        )

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .build()

        val chatNetwork = RetrofitChatNetwork(
            networkJson = networkJson,
            okHttpClient = { okHttpClient },
            baseUrl = server.url("/").toString()
        )

        val result = chatNetwork.getChatRoomsForUser()
        assertIs<Either.Right<List<ChatRoomDto>>>(result)

        // Verify first request used stale-token
        val req1 = server.takeRequest()
        assertEquals("Bearer stale-token", req1.getHeader("Authorization"))

        // Verify second request used fresh-token-xyz
        val req2 = server.takeRequest()
        assertEquals("Bearer fresh-token-xyz", req2.getHeader("Authorization"))
    }
}
