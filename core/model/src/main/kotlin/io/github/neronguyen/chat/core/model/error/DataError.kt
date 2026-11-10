package io.github.neronguyen.chat.core.model.error

import kotlin.time.Duration

sealed interface DataError {

    /** The request never received a server response. */
    sealed interface Connectivity : DataError {
        data object NoInternet : Connectivity
        data object Timeout : Connectivity
    }

    /** The server answered, but refused for a NON-BUSINESS reason. */
    sealed interface Remote : DataError {
        data object Unauthorized : Remote
        data object Forbidden : Remote
        data class RateLimited(val retryAfter: Duration? = null) : Remote
        data object Unavailable : Remote
    }

    /** Client and server disagree (contract drift, bug, malformed payload). */
    sealed interface Unexpected : DataError {
        data object Serialization : Unexpected
        data class UnknownCode(val status: Int) : Unexpected
        data class RequestRejected(val fieldErrors: Map<String, String> = emptyMap()) : Unexpected
    }

    /** Device-side state problem. */
    sealed interface Local : DataError {
        data object NotAuthenticated : Local
        data object NotFound : Local
    }
}

/**
 * Returns `true` when this [DataError] represents a transient connectivity or server availability
 * failure that is safe to retry with backoff.
 */
fun DataError.isRetryable(): Boolean = when (this) {
    is DataError.Connectivity.NoInternet,
    is DataError.Connectivity.Timeout,
    is DataError.Remote.RateLimited,
    is DataError.Remote.Unavailable -> true

    else -> false
}
