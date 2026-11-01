package io.github.neronguyen.chat.core.data.mapper

import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.util.NetworkError
import io.github.neronguyenvn.nerochat.contract.error.CommonServerError
import io.github.neronguyenvn.nerochat.contract.error.ServerError
import kotlin.time.Duration.Companion.seconds

internal fun NetworkError.Transport.toDataError(): DataError = when (this) {
    NetworkError.Transport.NoInternet -> DataError.Connectivity.NoInternet
    NetworkError.Transport.Timeout -> DataError.Connectivity.Timeout
    NetworkError.Transport.Serialization -> DataError.Unexpected.Serialization
}

internal fun NetworkError.Unknown.toDataError(): DataError = when {
    status == 429 -> DataError.Remote.RateLimited(retryAfterSeconds?.seconds)
    status == 401 -> DataError.Remote.Unauthorized
    status == 403 -> DataError.Remote.Forbidden
    status in 500..599 -> DataError.Remote.Unavailable
    else -> DataError.Unexpected.UnknownCode(status)
}

internal fun NetworkError.Common.toDataError(): DataError = when (error) {
    CommonServerError.Unauthorized -> DataError.Remote.Unauthorized
    CommonServerError.RateLimitExceeded -> DataError.Remote.RateLimited(retryAfterSeconds?.seconds)
    CommonServerError.BadRequest,
    CommonServerError.ValidationFailed -> DataError.Unexpected.RequestRejected(fieldErrors)
    CommonServerError.InternalServerError -> DataError.Remote.Unavailable
}

@JvmName("toNothingDataError")
internal fun NetworkError<Nothing>.toDataError(): DataError = when (this) {
    is NetworkError.Common -> toDataError()
    is NetworkError.Transport -> toDataError()
    is NetworkError.Unknown -> toDataError()
    is NetworkError.Business -> error("Unreachable: NetworkError<Nothing> cannot have Business error")
}

/** Generic splitter: maps Transport/Common/Unknown to DataError and delegates Business<S> to [mapBusinessError]. */
internal inline fun <S : ServerError, D> NetworkError<S>.toDomainError(
    wrapDataError: (DataError) -> D,
    mapBusinessError: (S) -> D,
): D = when (this) {
    is NetworkError.Business -> mapBusinessError(error)
    is NetworkError.Common -> wrapDataError(toDataError())
    is NetworkError.Transport -> wrapDataError(toDataError())
    is NetworkError.Unknown -> wrapDataError(toDataError())
}

/** Specialization of [toDomainError] for endpoints whose errors collapse directly into [DataError]. */
internal inline fun <S : ServerError> NetworkError<S>.toDataError(
    mapBusinessError: (S) -> DataError,
): DataError = toDomainError(
    wrapDataError = { it },
    mapBusinessError = mapBusinessError,
)
