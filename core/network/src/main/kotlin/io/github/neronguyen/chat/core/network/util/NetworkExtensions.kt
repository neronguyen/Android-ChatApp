package io.github.neronguyen.chat.core.network.util

import arrow.core.Either
import io.github.neronguyenvn.nerochat.contract.error.ApiErrorDto
import io.github.neronguyenvn.nerochat.contract.error.CommonServerError
import io.github.neronguyenvn.nerochat.contract.error.ErrorContract
import io.github.neronguyenvn.nerochat.contract.error.ServerError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

internal fun <S : ServerError> ApiErrorDto.toNetworkError(
    contract: ErrorContract<S>?,
    status: Int,
    headerRetryAfterSeconds: Long? = null,
): NetworkError<S> {
    val effectiveRetryAfter = retryAfterSeconds ?: headerRetryAfterSeconds

    if (contract != null) {
        val businessError = contract.fromCode(error)
        if (businessError != null) {
            return NetworkError.Business(
                error = businessError,
                message = message,
            )
        }
    }

    val common = CommonServerError.fromCode(error)
    if (common != null) {
        return NetworkError.Common(
            error = common,
            message = message,
            fieldErrors = fieldErrors,
            retryAfterSeconds = effectiveRetryAfter,
        )
    }

    return NetworkError.Unknown(
        status = status,
        retryAfterSeconds = effectiveRetryAfter,
        code = error,
        message = message,
    )
}

internal suspend fun <T, S : ServerError> safeCallInternal(
    json: Json,
    isUnit: Boolean,
    contract: ErrorContract<S>?,
    execute: suspend () -> Response<T>,
): Either<NetworkError<S>, T> {
    val response = try {
        execute()
    } catch (_: SerializationException) {
        return Either.Left(NetworkError.Transport.Serialization)
    } catch (e: IOException) {
        return Either.Left(e.toTransportError())
    } catch (_: UnresolvedAddressException) {
        return Either.Left(NetworkError.Transport.NoInternet)
    }

    val retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()

    if (response.isSuccessful) {
        val body = response.body()
        return when {
            body != null -> Either.Right(body)
            isUnit -> Either.Right(Unit as T)
            else -> Either.Left(
                NetworkError.Unknown(
                    status = response.code(),
                    retryAfterSeconds = retryAfterSeconds,
                )
            )
        }
    }

    val errorBodyString = try {
        response.errorBody()?.string()
    } catch (_: IOException) {
        null
    }

    if (errorBodyString.isNullOrBlank()) {
        return Either.Left(
            NetworkError.Unknown(
                status = response.code(),
                retryAfterSeconds = retryAfterSeconds,
            )
        )
    }

    val dto = try {
        json.decodeFromString<ApiErrorDto>(errorBodyString)
    } catch (_: Exception) {
        null
    }

    return if (dto != null) {
        Either.Left(dto.toNetworkError(contract, response.code(), retryAfterSeconds))
    } else {
        Either.Left(
            NetworkError.Unknown(
                status = response.code(),
                retryAfterSeconds = retryAfterSeconds,
            )
        )
    }
}

internal suspend inline fun <reified T, S : ServerError> safeCall(
    json: Json,
    contract: ErrorContract<S>,
    noinline execute: suspend () -> Response<T>,
): Either<NetworkError<S>, T> = safeCallInternal(
    json = json,
    isUnit = T::class == Unit::class,
    contract = contract,
    execute = execute,
)

@JvmName("safeCallCommon")
internal suspend inline fun <reified T> safeCallCommon(
    json: Json,
    noinline execute: suspend () -> Response<T>,
): Either<NetworkError<Nothing>, T> = safeCallInternal(
    json = json,
    isUnit = T::class == Unit::class,
    contract = null,
    execute = execute,
)

internal fun IOException.toTransportError(): NetworkError.Transport = when (this) {
    is SocketTimeoutException -> NetworkError.Transport.Timeout
    is UnknownHostException,
    is ConnectException -> NetworkError.Transport.NoInternet
    else -> NetworkError.Transport.NoInternet
}
