package io.github.neronguyen.chat.e2e

import android.util.Log
import io.github.neronguyenvn.nerochat.contract.ContractJson
import io.github.neronguyenvn.nerochat.contract.auth.AuthenticatedUserDto
import io.github.neronguyenvn.nerochat.contract.auth.EmailRequest
import io.github.neronguyenvn.nerochat.contract.auth.LoginRequest
import io.github.neronguyenvn.nerochat.contract.auth.RegisterRequest
import io.github.neronguyenvn.nerochat.contract.auth.VerifyEmailRequest
import io.github.neronguyenvn.nerochat.contract.chat.ChatParticipantDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatRoomDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatRoomType
import io.github.neronguyenvn.nerochat.contract.chat.WsClientMessage
import io.github.neronguyenvn.nerochat.contract.chat.WsServerEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class E2EPeerClient(
    backendBaseUrl: String = "http://localhost:8080",
    mailpitBaseUrl: String = "http://localhost:8025",
) : Closeable {

    private val httpBaseUrl = backendBaseUrl.trimEnd('/')
    private val mailBaseUrl = mailpitBaseUrl.trimEnd('/')
    private val wsUrl = when {
        httpBaseUrl.startsWith("https://") -> httpBaseUrl.replaceFirst("https://", "wss://")
        httpBaseUrl.startsWith("http://") -> httpBaseUrl.replaceFirst("http://", "ws://")
        else -> httpBaseUrl
    } + "/ws/chat"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var bobAccessToken: String? = null

    @Volatile
    private var bobUserId: String? = null

    @Volatile
    private var webSocket: WebSocket? = null

    private val wsErrors = CopyOnWriteArrayList<String>()

    fun ensureVerifiedPeerBob(
        email: String = DEFAULT_BOB_EMAIL,
        displayName: String = DEFAULT_BOB_DISPLAY_NAME,
        password: String = DEFAULT_BOB_PASSWORD,
    ) {
        val existingAuth = tryLogin(email, password)
        if (existingAuth != null) {
            bobAccessToken = existingAuth.accessToken
            bobUserId = existingAuth.user.id
            awaitParticipantProjection(email = email, bearerToken = existingAuth.accessToken)
            Log.i(TAG, "[BOB] Online as $email")
            return
        }

        val previousMsgId = findLatestMessageIdFor(email)
        val registerStatus = postJson(
            url = "$httpBaseUrl/api/auth/register",
            body = ContractJson.encodeToString(
                RegisterRequest(email = email, displayName = displayName, password = password),
            ),
        ).first

        when (registerStatus) {
            in 200..299 -> Unit
            409 -> {
                val (resendStatus, resendBody) = postJson(
                    url = "$httpBaseUrl/api/auth/resend-verification",
                    body = ContractJson.encodeToString(EmailRequest(email = email)),
                )
                check(resendStatus in 200..299) {
                    "Failed to resend verification email for $email: HTTP $resendStatus $resendBody"
                }
            }

            else -> error("Failed to register peer $email: HTTP $registerStatus")
        }

        val otp = fetchLatestOtp(email = email, excludeMessageId = previousMsgId)
        val (verifyStatus, verifyBody) = postJson(
            url = "$httpBaseUrl/api/auth/verify-email",
            body = ContractJson.encodeToString(VerifyEmailRequest(email = email, code = otp)),
        )
        check(verifyStatus in 200..299) {
            "Failed to verify peer $email: HTTP $verifyStatus $verifyBody"
        }

        val auth = tryLogin(email, password)
            ?: error("Peer $email login failed immediately after verification")
        bobAccessToken = auth.accessToken
        bobUserId = auth.user.id
        awaitParticipantProjection(email = email, bearerToken = auth.accessToken)
        Log.i(TAG, "[BOB] Online as $email")
    }

    fun fetchLatestOtp(
        email: String,
        excludeMessageId: String? = null,
        timeoutMs: Long = 20_000L,
    ): String {
        Log.i(SETUP_TAG, "[MAILPIT] Polling OTP email for $email...")
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastError: String? = null

        while (System.currentTimeMillis() < deadline) {
            try {
                val summaries = fetchMailpitSummariesFor(email)
                    .filter { it.id != excludeMessageId }
                val newest = summaries.maxByOrNull { parseCreatedEpochMillis(it.created) }
                if (newest != null) {
                    val otp = fetchOtpFromMessage(newest.id)
                    if (otp != null) {
                        Log.i(
                            SETUP_TAG,
                            "[MAILPIT] OTP fetched for $email: $otp (msgId=${newest.id.take(8)})"
                        )
                        return otp
                    }
                    lastError = "No 6-digit OTP found in Mailpit message ${newest.id}"
                } else {
                    lastError = "No Mailpit message addressed to $email"
                }
            } catch (e: Exception) {
                lastError = "${e::class.simpleName}: ${e.message}"
            }
            Thread.sleep(300L)
        }

        error("Timed out waiting for OTP email to $email after ${timeoutMs}ms ($lastError)")
    }

    fun resolveDirectRoomId(
        peerEmail: String,
        timeoutMs: Long = 15_000L,
    ): String {
        val token = requireBobToken()
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastError: String? = null

        while (System.currentTimeMillis() < deadline) {
            try {
                val (status, body) = getWithAuth("$httpBaseUrl/api/chat/rooms", token)
                if (status in 200..299) {
                    val rooms = ContractJson.decodeFromString<List<ChatRoomDto>>(body)
                    val match = rooms.firstOrNull { room ->
                        room.type == ChatRoomType.DIRECT &&
                                room.participants.any {
                                    it.email.equals(
                                        peerEmail,
                                        ignoreCase = true,
                                    )
                                }
                    }
                    if (match != null) {
                        Log.i(TAG, "[BOB] Joined direct room ${match.id.take(8)} with $peerEmail")
                        match.lastMessage?.let { latest ->
                            Log.i(TAG, "[BOB] Latest msg from peer: \"${latest.content}\"")
                        }
                        return match.id
                    }
                    lastError = "No DIRECT room with $peerEmail in ${rooms.size} rooms"
                } else {
                    lastError = "HTTP $status $body"
                }
            } catch (e: Exception) {
                lastError = "${e::class.simpleName}: ${e.message}"
            }
            Thread.sleep(300L)
        }

        error("Timed out resolving DIRECT room with $peerEmail ($lastError)")
    }

    fun connectWebSocket(timeoutMs: Long = 10_000L) {
        val token = requireBobToken()
        webSocket?.close(1000, "Reconnecting")
        webSocket = null
        wsErrors.clear()

        val openLatch = CountDownLatch(1)
        var failure: Throwable? = null

        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer $token")
            .build()

        val socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    openLatch.countDown()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching {
                        ContractJson.decodeFromString<WsServerEvent>(text)
                    }.onSuccess { event ->
                        when (event) {
                            is WsServerEvent.NewMessage -> {
                                val msg = event.message
                                if (msg.senderId == bobUserId) {
                                    Log.i(TAG, "[BOB-WS] Server ACK: \"${msg.content}\"")
                                } else {
                                    Log.i(TAG, "[BOB-WS] Received from peer: \"${msg.content}\"")
                                }
                            }

                            is WsServerEvent.ParticipantJoined -> {
                                Log.i(
                                    TAG,
                                    "[BOB-WS] Room ${event.roomId.take(8)} participant joined"
                                )
                            }

                            is WsServerEvent.Error -> {
                                wsErrors.add("${event.error}: ${event.message}")
                                Log.w(TAG, "[BOB-WS] Error ${event.error}: ${event.message}")
                            }

                            else -> Unit
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    failure = IOException("WebSocket failure (${response?.code}): ${t.message}", t)
                    openLatch.countDown()
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (openLatch.count > 0) {
                        failure = IOException("WebSocket closing before open ($code): $reason")
                        openLatch.countDown()
                    }
                }
            },
        )

        if (!openLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            socket.cancel()
            error("Timed out connecting Bob WebSocket to $wsUrl")
        }
        failure?.let { throw it }
        webSocket = socket
        Log.i(TAG, "[BOB-WS] Connected /ws/chat")
    }

    fun sendDirectTextMessage(roomId: String, content: String): String {
        val socket = checkNotNull(webSocket) { "Bob WebSocket is not connected" }
        val messageId = UUID.randomUUID().toString()
        val payload = ContractJson.encodeToString<WsClientMessage>(
            WsClientMessage.NewMessage(
                roomId = roomId,
                content = content,
                messageId = messageId,
            ),
        )
        check(socket.send(payload)) {
            "WebSocket.send returned false for roomId=$roomId (errors=$wsErrors)"
        }
        Log.i(TAG, "[BOB-WS] Sending: \"$content\"")
        return messageId
    }

    override fun close() {
        if (webSocket != null) {
            Log.i(TAG, "[BOB-WS] Disconnected /ws/chat")
        }
        runCatching { webSocket?.close(1000, "Test finished") }
        webSocket = null
        runCatching { client.dispatcher.executorService.shutdown() }
        runCatching { client.connectionPool.evictAll() }
    }

    private fun awaitParticipantProjection(
        email: String,
        bearerToken: String,
        timeoutMs: Long = 15_000L,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastStatus = -1
        var lastBody = ""
        while (System.currentTimeMillis() < deadline) {
            val (status, body) = getWithAuth(
                url = "$httpBaseUrl/api/chat/participants?email=$email",
                bearerToken = bearerToken,
            )
            if (status in 200..299) {
                ContractJson.decodeFromString<ChatParticipantDto>(body)
                return
            }
            lastStatus = status
            lastBody = body
            Thread.sleep(300L)
        }
        error("Timed out waiting for RabbitMQ participant projection for $email: HTTP $lastStatus $lastBody")
    }

    private fun tryLogin(email: String, password: String): AuthenticatedUserDto? {
        val (status, body) = postJson(
            url = "$httpBaseUrl/api/auth/login",
            body = ContractJson.encodeToString(LoginRequest(email = email, password = password)),
        )
        return if (status in 200..299) {
            ContractJson.decodeFromString<AuthenticatedUserDto>(body)
        } else {
            null
        }
    }

    private fun findLatestMessageIdFor(email: String): String? =
        runCatching {
            fetchMailpitSummariesFor(email)
                .maxByOrNull { parseCreatedEpochMillis(it.created) }
                ?.id
        }.getOrNull()

    private fun fetchMailpitSummariesFor(email: String): List<MailpitMessageSummary> {
        val (status, body) = get("$mailBaseUrl/api/v1/messages")
        check(status in 200..299) { "Mailpit /api/v1/messages returned HTTP $status" }
        val response = ContractJson.decodeFromString<MailpitMessagesResponse>(body)
        return response.messages.filter { summary ->
            summary.to.any { recipient -> recipient.address.equals(email, ignoreCase = true) }
        }
    }

    private fun fetchOtpFromMessage(messageId: String): String? {
        val (status, body) = get("$mailBaseUrl/api/v1/message/$messageId")
        check(status in 200..299) { "Mailpit /api/v1/message/$messageId returned HTTP $status" }
        val detail = ContractJson.decodeFromString<MailpitMessageDetail>(body)

        val textMatch = detail.text?.let { OTP_REGEX.find(it)?.value }
        if (textMatch != null) return textMatch

        val sanitizedHtml = detail.html
            ?.replace(STYLE_BLOCK_REGEX, " ")
            ?.replace(HTML_TAG_REGEX, " ")
            .orEmpty()
        return OTP_REGEX.find(sanitizedHtml)?.value
    }

    private fun parseCreatedEpochMillis(created: String): Long =
        runCatching { Instant.parse(created).toEpochMilli() }.getOrDefault(0L)

    private fun requireBobToken(): String =
        checkNotNull(bobAccessToken) { "ensureVerifiedPeerBob() must succeed before calling chat APIs" }

    private fun get(url: String): Pair<Int, String> {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            return response.code to response.body.string()
        }
    }

    private fun getWithAuth(url: String, bearerToken: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $bearerToken")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            return response.code to response.body.string()
        }
    }

    private fun postJson(url: String, body: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            return response.code to response.body.string()
        }
    }

    @Serializable
    private data class MailpitMessagesResponse(
        @SerialName("messages") val messages: List<MailpitMessageSummary> = emptyList(),
    )

    @Serializable
    private data class MailpitMessageSummary(
        @SerialName("ID") val id: String,
        @SerialName("Created") val created: String = "",
        @SerialName("To") val to: List<MailpitRecipient> = emptyList(),
    )

    @Serializable
    private data class MailpitRecipient(
        @SerialName("Address") val address: String = "",
    )

    @Serializable
    private data class MailpitMessageDetail(
        @SerialName("Text") val text: String? = null,
        @SerialName("HTML") val html: String? = null,
    )

    companion object {
        const val TAG = "E2E_PEER"
        private const val SETUP_TAG = "E2E_SMOKE"
        const val DEFAULT_BOB_EMAIL = "bob@example.com"
        const val DEFAULT_BOB_DISPLAY_NAME = "Bob E2E"
        const val DEFAULT_BOB_PASSWORD = "BobPassword123!"

        private val OTP_REGEX = Regex("""\b\d{6}\b""")
        private val STYLE_BLOCK_REGEX = Regex("""(?is)<style[^>]*>.*?</style>""")
        private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
    }
}
