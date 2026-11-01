package io.github.neronguyen.chat.core.network.util

import io.github.neronguyenvn.nerochat.contract.error.CommonServerError
import io.github.neronguyenvn.nerochat.contract.error.ErrorCode
import io.github.neronguyenvn.nerochat.contract.error.ServerError

sealed interface NetworkError<out S : ServerError> {
    enum class Transport : NetworkError<Nothing> {
        NoInternet,
        Timeout,
        Serialization,
    }

    /** Non-2xx response that did not match Business or Common (e.g. unknown ErrorCode or 502 HTML page). */
    data class Unknown(
        val status: Int,
        val retryAfterSeconds: Long? = null,
        val code: ErrorCode? = null,
        val message: String? = null,
    ) : NetworkError<Nothing>

    /** Non-2xx response whose ApiErrorDto.error matched a cross-cutting CommonServerError. */
    data class Common(
        val error: CommonServerError,
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
        val retryAfterSeconds: Long? = null,
    ) : NetworkError<Nothing>

    /** Non-2xx response whose ApiErrorDto.error matched the endpoint's business ErrorContract. */
    data class Business<out S : ServerError>(
        val error: S,
        val message: String,
    ) : NetworkError<S>
}
