package io.github.neronguyen.chat.core.network.interceptor

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthInterceptorTest {

    private class FakeTokenProvider : TokenProvider {
        var savedAccessToken: String? = null
        override suspend fun getAccessToken(): String? = savedAccessToken
    }

    private fun createChain(
        initialRequest: Request,
        handler: (Request) -> Response
    ): Pair<Interceptor.Chain, List<Request>> {
        val capturedRequests = mutableListOf<Request>()
        lateinit var proxyChain: Interceptor.Chain
        val handlerInvocation = java.lang.reflect.InvocationHandler { _, method, args ->
            when (method.name) {
                "request" -> initialRequest
                "proceed" -> {
                    val req = args[0] as Request
                    capturedRequests.add(req)
                    handler(req)
                }

                "connectTimeoutMillis", "readTimeoutMillis", "writeTimeoutMillis" -> 0
                "connection" -> null
                "call" -> throw UnsupportedOperationException()
                else -> {
                    if (method.returnType == Interceptor.Chain::class.java) {
                        proxyChain
                    } else if (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == java.lang.Boolean.TYPE) {
                        false
                    } else if (method.returnType == Int::class.javaPrimitiveType || method.returnType == java.lang.Integer.TYPE) {
                        0
                    } else {
                        null
                    }
                }
            }
        }
        proxyChain = Proxy.newProxyInstance(
            Interceptor.Chain::class.java.classLoader,
            arrayOf(Interceptor.Chain::class.java),
            handlerInvocation
        ) as Interceptor.Chain
        return proxyChain to capturedRequests
    }

    private fun createResponse(request: Request, code: Int, body: String = ""): Response {
        val mediaType = "application/json".toMediaType()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Status $code")
            .body(body.toResponseBody(mediaType))
            .build()
    }

    @Test
    fun normalEndpoint_attachesBearerTokenHeader() = runTest {
        val fakeTokenDS = FakeTokenProvider().apply { savedAccessToken = "valid_access_token" }
        val interceptor = AuthInterceptor(fakeTokenDS)

        val request = Request.Builder().url("https://example.com/api/chat").build()
        val (chain, capturedRequests) = createChain(request) { req ->
            createResponse(
                req,
                200,
                "[]"
            )
        }

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(1, capturedRequests.size)
        assertEquals("Bearer valid_access_token", capturedRequests[0].header("Authorization"))
    }

    @Test
    fun normalEndpoint_whenNoToken_doesNotAttachHeader() = runTest {
        val fakeTokenDS = FakeTokenProvider().apply { savedAccessToken = null }
        val interceptor = AuthInterceptor(fakeTokenDS)

        val request = Request.Builder().url("https://example.com/api/chat").build()
        val (chain, capturedRequests) = createChain(request) { req ->
            createResponse(
                req,
                200,
                "[]"
            )
        }

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(1, capturedRequests.size)
        assertNull(capturedRequests[0].header("Authorization"))
    }

    @Test
    fun normalEndpoint_whenHeaderAlreadyPresent_doesNotOverwrite() = runTest {
        val fakeTokenDS = FakeTokenProvider().apply { savedAccessToken = "new_token" }
        val interceptor = AuthInterceptor(fakeTokenDS)

        val request = Request.Builder()
            .url("https://example.com/api/chat")
            .header("Authorization", "Bearer existing_token")
            .build()

        val (chain, capturedRequests) = createChain(request) { req ->
            createResponse(
                req,
                200,
                "[]"
            )
        }

        val response = interceptor.intercept(chain)

        assertEquals(200, response.code)
        assertEquals(1, capturedRequests.size)
        assertEquals("Bearer existing_token", capturedRequests[0].header("Authorization"))
    }
}
