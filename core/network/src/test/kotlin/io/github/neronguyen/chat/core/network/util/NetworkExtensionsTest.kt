package io.github.neronguyen.chat.core.network.util

import arrow.core.Either
import io.github.neronguyenvn.nerochat.contract.auth.error.AuthServerError
import io.github.neronguyenvn.nerochat.contract.error.ApiErrorDto
import io.github.neronguyenvn.nerochat.contract.error.CommonServerError
import io.github.neronguyenvn.nerochat.contract.error.ErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkExtensionsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun <T> createErrorResponse(
        code: Int,
        body: String = "",
        headers: Map<String, String> = emptyMap(),
        mediaType: String = "application/json",
    ): Response<T> {
        val rawBuilder = okhttp3.Response.Builder()
            .code(code)
            .message("Error $code")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://api.example.com").build())
            .body(body.toResponseBody(mediaType.toMediaType()))

        headers.forEach { (name, value) ->
            rawBuilder.header(name, value)
        }

        return Response.error(body.toResponseBody(mediaType.toMediaType()), rawBuilder.build())
    }

    @Test
    fun `safeCall parses successful body`() = runTest {
        val result: Either<NetworkError<AuthServerError.Login>, String> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { Response.success("success_data") }
        )

        assertIs<Either.Right<String>>(result)
        assertEquals("success_data", result.value)
    }

    @Test
    fun `safeCall handles Unit return type on 204 No Content`() = runTest {
        val result: Either<NetworkError<AuthServerError.Login>, Unit> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { Response.success<Unit>(204, null) }
        )

        assertIs<Either.Right<Unit>>(result)
        assertEquals(Unit, result.value)
    }

    @Test
    fun `safeCall maps HTTP 429 with Retry-After header to NetworkError Unknown with retryAfterSeconds`() = runTest {
        val response = createErrorResponse<String>(
            code = 429,
            body = "Too many requests",
            headers = mapOf("Retry-After" to "60"),
            mediaType = "text/plain"
        )

        val result: Either<NetworkError<AuthServerError.Login>, String> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { response }
        )

        assertIs<Either.Left<NetworkError.Unknown>>(result)
        val error = result.value
        assertEquals(429, error.status)
        assertEquals(60L, error.retryAfterSeconds)
    }

    @Test
    fun `safeCall maps recognized contract error to NetworkError Business`() = runTest {
        val errorBody = json.encodeToString(
            ApiErrorDto(
                error = AuthServerError.InvalidCredentials,
                message = "Invalid credentials",
                fieldErrors = emptyMap(),
                retryAfterSeconds = null
            )
        )

        val response = createErrorResponse<String>(
            code = 401,
            body = errorBody,
        )

        val result: Either<NetworkError<AuthServerError.Login>, String> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { response }
        )

        assertIs<Either.Left<NetworkError.Business<AuthServerError.Login>>>(result)
        val businessError = result.value
        assertEquals(AuthServerError.InvalidCredentials, businessError.error)
        assertEquals("Invalid credentials", businessError.message)
    }

    @Test
    fun `safeCall extracts retryAfterSeconds from ApiErrorDto body when header is absent`() = runTest {
        val errorBody = json.encodeToString(
            ApiErrorDto(
                error = CommonServerError.RateLimitExceeded,
                message = "Too many requests",
                fieldErrors = emptyMap(),
                retryAfterSeconds = 45L
            )
        )

        val response = createErrorResponse<String>(
            code = 429,
            body = errorBody,
        )

        val result: Either<NetworkError<AuthServerError.Login>, String> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { response }
        )

        assertIs<Either.Left<NetworkError.Common>>(result)
        val commonError = result.value
        assertEquals(CommonServerError.RateLimitExceeded, commonError.error)
        assertEquals(45L, commonError.retryAfterSeconds)
    }

    @Test
    fun `safeCall prioritizes retryAfterSeconds from ApiErrorDto body over header`() = runTest {
        val errorBody = json.encodeToString(
            ApiErrorDto(
                error = CommonServerError.RateLimitExceeded,
                message = "Too many requests",
                fieldErrors = emptyMap(),
                retryAfterSeconds = 30L
            )
        )

        val response = createErrorResponse<String>(
            code = 429,
            body = errorBody,
            headers = mapOf("Retry-After" to "60")
        )

        val result: Either<NetworkError<AuthServerError.Login>, String> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { response }
        )

        assertIs<Either.Left<NetworkError.Common>>(result)
        val commonError = result.value
        assertEquals(CommonServerError.RateLimitExceeded, commonError.error)
        assertEquals(30L, commonError.retryAfterSeconds)
    }

    @Test
    fun `safeCallCommon decodes CommonServerError as NetworkError Common`() = runTest {
        val errorBody = json.encodeToString(
            ApiErrorDto(
                error = CommonServerError.Unauthorized,
                message = "Unauthorized",
            )
        )

        val response = createErrorResponse<String>(code = 401, body = errorBody)

        val result: Either<NetworkError<Nothing>, String> = safeCallCommon(
            json = json,
            execute = { response }
        )

        assertIs<Either.Left<NetworkError.Common>>(result)
        val commonError = result.value
        assertEquals(CommonServerError.Unauthorized, commonError.error)
        assertEquals("Unauthorized", commonError.message)
    }

    @Test
    fun `safeCall maps CommonServerError response to NetworkError Common`() = runTest {
        val errorBody = json.encodeToString(
            ApiErrorDto(
                error = CommonServerError.ValidationFailed,
                message = "Invalid input",
                fieldErrors = mapOf("email" to "Invalid format")
            )
        )

        val response = createErrorResponse<String>(code = 400, body = errorBody)

        val result: Either<NetworkError<AuthServerError.Login>, String> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { response }
        )

        assertIs<Either.Left<NetworkError.Common>>(result)
        val commonError = result.value
        assertEquals(CommonServerError.ValidationFailed, commonError.error)
        assertEquals("Invalid input", commonError.message)
        assertEquals("Invalid format", commonError.fieldErrors["email"])
    }

    @Test
    fun `safeCall maps unrecognized contract error to NetworkError Unknown`() = runTest {
        val errorBody = """
            {
                "error": "Custom.GatewayError",
                "message": "Something unusual happened",
                "fieldErrors": {}
            }
        """.trimIndent()

        val response = createErrorResponse<String>(code = 400, body = errorBody)

        val result: Either<NetworkError<AuthServerError.Login>, String> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { response }
        )

        assertIs<Either.Left<NetworkError.Unknown>>(result)
        val error = result.value
        assertEquals(400, error.status)
        assertEquals(ErrorCode("Custom.GatewayError"), error.code)
        assertEquals("Something unusual happened", error.message)
        assertNull(error.retryAfterSeconds)
    }

    @Test
    fun `safeCall maps non-JSON error to NetworkError Unknown`() = runTest {
        val response = createErrorResponse<String>(
            code = 502,
            body = "<html>Bad Gateway</html>",
            mediaType = "text/html"
        )

        val result: Either<NetworkError<AuthServerError.Login>, String> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { response }
        )

        assertIs<Either.Left<NetworkError.Unknown>>(result)
        val error = result.value
        assertEquals(502, error.status)
        assertNull(error.retryAfterSeconds)
    }

    @Test
    fun `safeCall maps SocketTimeoutException to NetworkError Transport Timeout`() = runTest {
        val result: Either<NetworkError<AuthServerError.Login>, String> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { throw SocketTimeoutException("Read timed out") }
        )

        assertIs<Either.Left<NetworkError.Transport>>(result)
        assertEquals(NetworkError.Transport.Timeout, result.value)
    }

    @Test
    fun `safeCall maps UnknownHostException to NetworkError Transport NoInternet`() = runTest {
        val result: Either<NetworkError<AuthServerError.Login>, String> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { throw UnknownHostException("Unable to resolve host") }
        )

        assertIs<Either.Left<NetworkError.Transport>>(result)
        assertEquals(NetworkError.Transport.NoInternet, result.value)
    }

    @Test
    fun `safeCall maps ConnectException to NetworkError Transport NoInternet`() = runTest {
        val result: Either<NetworkError<AuthServerError.Login>, String> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { throw ConnectException("Connection refused") }
        )

        assertIs<Either.Left<NetworkError.Transport>>(result)
        assertEquals(NetworkError.Transport.NoInternet, result.value)
    }

    @Test
    fun `safeCall maps UnresolvedAddressException to NetworkError Transport NoInternet`() = runTest {
        val result: Either<NetworkError<AuthServerError.Login>, String> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { throw UnresolvedAddressException() }
        )

        assertIs<Either.Left<NetworkError.Transport>>(result)
        assertEquals(NetworkError.Transport.NoInternet, result.value)
    }

    @Test
    fun `safeCall maps SerializationException to NetworkError Transport Serialization`() = runTest {
        val result: Either<NetworkError<AuthServerError.Login>, String> = safeCall(
            json = json,
            contract = AuthServerError.Login,
            execute = { throw SerializationException("Malformed JSON") }
        )

        assertIs<Either.Left<NetworkError.Transport>>(result)
        assertEquals(NetworkError.Transport.Serialization, result.value)
    }

    @Test
    fun `safeCall rethrows IllegalStateException without swallowing`() = runTest {
        assertFailsWith<IllegalStateException> {
            safeCall<String, AuthServerError.Login>(json = json, contract = AuthServerError.Login) {
                throw IllegalStateException("Programmer bug in response processing")
            }
        }
    }

    @Test
    fun `safeCall rethrows NullPointerException without swallowing`() = runTest {
        assertFailsWith<NullPointerException> {
            safeCall<String, AuthServerError.Login>(json = json, contract = AuthServerError.Login) {
                throw NullPointerException("Null pointer encountered")
            }
        }
    }

    @Test
    fun `safeCall does not swallow CancellationException`() = runTest {
        assertFailsWith<CancellationException> {
            safeCall<String, AuthServerError.Login>(json = json, contract = AuthServerError.Login) {
                throw CancellationException("Coroutine was cancelled")
            }
        }
    }
}
