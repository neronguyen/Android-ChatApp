package io.github.neronguyen.chat.core.data.mapper

import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.util.NetworkError
import io.github.neronguyenvn.nerochat.contract.error.CommonServerError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class DataErrorMapperTest {

    @Test
    fun `NetworkError Unknown 429 maps to RateLimited with duration`() {
        val unknown = NetworkError.Unknown(status = 429, retryAfterSeconds = 60L)
        val dataError = unknown.toDataError()

        assertIs<DataError.Remote.RateLimited>(dataError)
        assertEquals(60.seconds, dataError.retryAfter)
    }

    @Test
    fun `NetworkError Unknown 429 without Retry-After maps to RateLimited with null duration`() {
        val unknown = NetworkError.Unknown(status = 429, retryAfterSeconds = null)
        val dataError = unknown.toDataError()

        assertIs<DataError.Remote.RateLimited>(dataError)
        assertNull(dataError.retryAfter)
    }

    @Test
    fun `NetworkError Unknown 401 maps to Unauthorized`() {
        val unknown = NetworkError.Unknown(status = 401)
        assertEquals(DataError.Remote.Unauthorized, unknown.toDataError())
    }

    @Test
    fun `NetworkError Unknown 403 maps to Forbidden`() {
        val unknown = NetworkError.Unknown(status = 403)
        assertEquals(DataError.Remote.Forbidden, unknown.toDataError())
    }

    @Test
    fun `NetworkError Unknown 500 and 503 map to Unavailable`() {
        assertEquals(DataError.Remote.Unavailable, NetworkError.Unknown(status = 500).toDataError())
        assertEquals(DataError.Remote.Unavailable, NetworkError.Unknown(status = 503).toDataError())
    }

    @Test
    fun `NetworkError Unknown maps to UnknownCode`() {
        val unknown = NetworkError.Unknown(status = 400)
        val dataError = unknown.toDataError()

        assertIs<DataError.Unexpected.UnknownCode>(dataError)
        assertEquals(400, dataError.status)
    }

    @Test
    fun `NetworkError Common maps to appropriate DataErrors`() {
        assertEquals(
            DataError.Remote.Unauthorized,
            NetworkError.Common(CommonServerError.Unauthorized, "Unauthorized").toDataError()
        )
        assertEquals(
            DataError.Remote.RateLimited(60.seconds),
            NetworkError.Common(
                CommonServerError.RateLimitExceeded,
                "Rate limit exceeded",
                retryAfterSeconds = 60L
            ).toDataError()
        )
        assertEquals(
            DataError.Unexpected.RequestRejected(),
            NetworkError.Common(CommonServerError.BadRequest, "Bad request").toDataError()
        )
        assertEquals(
            DataError.Unexpected.RequestRejected(),
            NetworkError.Common(CommonServerError.ValidationFailed, "Validation failed").toDataError()
        )
        assertEquals(
            DataError.Unexpected.RequestRejected(mapOf("displayName" to "Display name must not be blank")),
            NetworkError.Common(
                CommonServerError.ValidationFailed,
                "Validation failed",
                fieldErrors = mapOf("displayName" to "Display name must not be blank")
            ).toDataError()
        )
        assertEquals(
            DataError.Remote.Unavailable,
            NetworkError.Common(CommonServerError.InternalServerError, "Server error").toDataError()
        )
    }

    @Test
    fun `NetworkError Transport mappers return appropriate Connectivity or Unexpected errors`() {
        assertEquals(DataError.Connectivity.NoInternet, NetworkError.Transport.NoInternet.toDataError())
        assertEquals(DataError.Connectivity.Timeout, NetworkError.Transport.Timeout.toDataError())
        assertEquals(DataError.Unexpected.Serialization, NetworkError.Transport.Serialization.toDataError())
    }

    @Test
    fun `NetworkError with Nothing toDataError maps Common, Unknown 429 and Transport`() {
        val unauthorized: NetworkError<Nothing> = NetworkError.Common(
            error = CommonServerError.Unauthorized,
            message = "Unauthorized"
        )
        assertEquals(DataError.Remote.Unauthorized, unauthorized.toDataError())

        val gateway429: NetworkError<Nothing> =
            NetworkError.Unknown(status = 429, retryAfterSeconds = 45L)
        val rateLimited = gateway429.toDataError()
        assertIs<DataError.Remote.RateLimited>(rateLimited)
        assertEquals(45.seconds, rateLimited.retryAfter)

        val offline: NetworkError<Nothing> = NetworkError.Transport.NoInternet
        assertEquals(DataError.Connectivity.NoInternet, offline.toDataError())
    }
}
