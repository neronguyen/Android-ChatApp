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

internal fun AuthServerError.VerifyEmail.toDomain(retryAfterSeconds: Long? = null): AuthError.VerifyEmail = when (this) {
    AuthServerError.InvalidCode -> AuthError.InvalidCode
    AuthServerError.CodeExpired -> AuthError.CodeExpired
    AuthServerError.MaxAttemptsExceeded -> AuthError.MaxAttemptsExceeded(retryAfterSeconds)
}

internal fun AuthServerError.ResetPassword.toDomain(retryAfterSeconds: Long? = null): AuthError.ResetPassword = when (this) {
    AuthServerError.InvalidCode -> AuthError.InvalidCode
    AuthServerError.CodeExpired -> AuthError.CodeExpired
    AuthServerError.MaxAttemptsExceeded -> AuthError.MaxAttemptsExceeded(retryAfterSeconds)
    AuthServerError.SamePassword -> AuthError.SamePassword
}

internal fun NetworkError<AuthServerError.Login>.toLoginError(): AuthError.Login =
    toDomainError(AuthError::Data, AuthServerError.Login::toDomain)

internal fun NetworkError<AuthServerError.Register>.toRegisterError(): AuthError.Register =
    toDomainError(AuthError::Data, AuthServerError.Register::toDomain)

internal fun NetworkError<AuthServerError.VerifyEmail>.toVerifyEmailError(): AuthError.VerifyEmail = when (this) {
    is NetworkError.Business -> error.toDomain(retryAfterSeconds)
    is NetworkError.Common -> AuthError.Data(toDataError())
    is NetworkError.Transport -> AuthError.Data(toDataError())
    is NetworkError.Unknown -> AuthError.Data(toDataError())
}

internal fun NetworkError<AuthServerError.ResetPassword>.toResetPasswordError(): AuthError.ResetPassword = when (this) {
    is NetworkError.Business -> error.toDomain(retryAfterSeconds)
    is NetworkError.Common -> AuthError.Data(toDataError())
    is NetworkError.Transport -> AuthError.Data(toDataError())
    is NetworkError.Unknown -> AuthError.Data(toDataError())
}

@JvmName("toRefreshTokenDataError")
internal fun NetworkError<AuthServerError.RefreshToken>.toDataError(): DataError =
    toDataError(AuthServerError.RefreshToken::toDataError)

@JvmName("toLogoutDataError")
internal fun NetworkError<AuthServerError.Logout>.toDataError(): DataError =
    toDataError(AuthServerError.Logout::toDataError)
