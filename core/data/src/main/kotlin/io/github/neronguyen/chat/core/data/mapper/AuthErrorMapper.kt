package io.github.neronguyen.chat.core.data.mapper

import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.util.NetworkError
import io.github.neronguyenvn.nerochat.contract.auth.error.AuthServerError

internal fun AuthServerError.Login.toDomain(): AuthError.Login = when (this) {
    AuthServerError.InvalidCredentials -> AuthError.InvalidCredentials
    AuthServerError.EmailNotVerified -> AuthError.EmailNotVerified
}

internal fun AuthServerError.Register.toDomain(): AuthError.Register = when (this) {
    AuthServerError.UserAlreadyExists -> AuthError.EmailAlreadyInUse
}

internal fun AuthServerError.RefreshToken.toDataError(): DataError = when (this) {
    AuthServerError.InvalidToken,
    AuthServerError.UserNotFound -> DataError.Remote.Unauthorized
}

internal fun AuthServerError.Logout.toDataError(): DataError = when (this) {
    AuthServerError.InvalidToken -> DataError.Remote.Unauthorized
}

internal fun NetworkError<AuthServerError.Login>.toLoginError(): AuthError.Login =
    toDomainError(AuthError::Data, AuthServerError.Login::toDomain)

internal fun NetworkError<AuthServerError.Register>.toRegisterError(): AuthError.Register =
    toDomainError(AuthError::Data, AuthServerError.Register::toDomain)

@JvmName("toRefreshTokenDataError")
internal fun NetworkError<AuthServerError.RefreshToken>.toDataError(): DataError =
    toDataError(AuthServerError.RefreshToken::toDataError)

@JvmName("toLogoutDataError")
internal fun NetworkError<AuthServerError.Logout>.toDataError(): DataError =
    toDataError(AuthServerError.Logout::toDataError)
