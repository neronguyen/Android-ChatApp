package io.github.neronguyen.chat.core.network.interceptor

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenAuthenticatorTest {

    private class FakeTokenProvider : TokenProvider {
        var savedAccessToken: String? = null
        override suspend fun getAccessToken(): String? = savedAccessToken
    }

    private class FakeTokenRefresher : TokenRefresher {
        var refreshResult: (String) -> String? = { null }
        val refreshedStaleTokens = mutableListOf<String>()

        override suspend fun refresh(staleToken: String): String? {
            refreshedStaleTokens.add(staleToken)
            return refreshResult(staleToken)
        }
    }

    private fun createResponse(
        request: Request,
        code: Int = 401,
        priorResponse: Response? = null
    ): Response {
        val mediaType = "application/json".toMediaType()
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Unauthorized")
            .body("".toResponseBody(mediaType))

        if (priorResponse != null) {
            builder.priorResponse(priorResponse)
        }

        return builder.build()
    }

    @Test
    fun authenticate_refreshesTokenAndReturnsNewRequest_whenTokenIsStale() = runTest {
        val fakeTokenProvider = FakeTokenProvider().apply { savedAccessToken = "stale_token" }
        val fakeRefresher = FakeTokenRefresher().apply {
            refreshResult = { "new_access_token" }
        }

        val authenticator = TokenAuthenticator(
            tokenProvider = fakeTokenProvider,
            tokenRefresherProvider = { fakeRefresher }
        )

        val request = Request.Builder()
            .url("https://example.com/api/chat")
            .header("Authorization", "Bearer stale_token")
            .build()
        val response = createResponse(request)

        val newRequest = authenticator.authenticate(null, response)

        assertEquals("Bearer new_access_token", newRequest?.header("Authorization"))
        assertEquals(listOf("stale_token"), fakeRefresher.refreshedStaleTokens)
    }

    @Test
    fun authenticate_reusesNewToken_whenAlreadyRefreshedByConcurrentRequest() = runTest {
        val fakeTokenProvider =
            FakeTokenProvider().apply { savedAccessToken = "already_refreshed_token" }
        val fakeRefresher = FakeTokenRefresher()

        val authenticator = TokenAuthenticator(
            tokenProvider = fakeTokenProvider,
            tokenRefresherProvider = { fakeRefresher }
        )

        val request = Request.Builder()
            .url("https://example.com/api/chat")
            .header("Authorization", "Bearer old_failed_token")
            .build()
        val response = createResponse(request)

        val newRequest = authenticator.authenticate(null, response)

        assertEquals("Bearer already_refreshed_token", newRequest?.header("Authorization"))
        assertEquals(0, fakeRefresher.refreshedStaleTokens.size)
    }

    @Test
    fun authenticate_aborts_whenResponseCountIsThreeOrMore() = runTest {
        val fakeTokenProvider = FakeTokenProvider().apply { savedAccessToken = "token" }
        val fakeRefresher = FakeTokenRefresher()

        val authenticator = TokenAuthenticator(
            tokenProvider = fakeTokenProvider,
            tokenRefresherProvider = { fakeRefresher }
        )

        val request = Request.Builder().url("https://example.com/api/chat").build()
        val res1 = createResponse(request)
        val res2 = createResponse(request, priorResponse = res1)
        val res3 = createResponse(request, priorResponse = res2)

        val newRequest = authenticator.authenticate(null, res3)

        assertNull(newRequest)
        assertEquals(0, fakeRefresher.refreshedStaleTokens.size)
    }

    @Test
    fun authenticate_returnsNull_whenRefreshFails() = runTest {
        val fakeTokenProvider = FakeTokenProvider().apply { savedAccessToken = "stale_token" }
        val fakeRefresher = FakeTokenRefresher().apply {
            refreshResult = { null }
        }

        val authenticator = TokenAuthenticator(
            tokenProvider = fakeTokenProvider,
            tokenRefresherProvider = { fakeRefresher }
        )

        val request = Request.Builder()
            .url("https://example.com/api/chat")
            .header("Authorization", "Bearer stale_token")
            .build()
        val response = createResponse(request)

        val newRequest = authenticator.authenticate(null, response)

        assertNull(newRequest)
        assertEquals(listOf("stale_token"), fakeRefresher.refreshedStaleTokens)
    }
}
