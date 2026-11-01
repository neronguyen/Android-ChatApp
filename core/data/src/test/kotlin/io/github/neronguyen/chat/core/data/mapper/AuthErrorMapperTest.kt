package io.github.neronguyen.chat.core.data.mapper

import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.util.NetworkError
import io.github.neronguyenvn.nerochat.contract.auth.error.AuthServerError
import io.github.neronguyenvn.nerochat.contract.error.CommonServerError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class AuthErrorMapperTest {

    @Test
    fun `pure AuthServerError toDomain and toDataError map accurately`() {
        assertEquals(AuthError.InvalidCredentials, AuthServerError.InvalidCredentials.toDomain())
        assertEquals(AuthError.EmailNotVerified, AuthServerError.EmailNotVerified.toDomain())
        assertEquals(AuthError.EmailAlreadyInUse, AuthServerError.UserAlreadyExists.toDomain())
        assertEquals(DataError.Remote.Unauthorized, (AuthServerError.InvalidToken as AuthServerError.RefreshToken).toDataError())
        assertEquals(DataError.Remote.Unauthorized, (AuthServerError.InvalidToken as AuthServerError.Logout).toDataError())
        assertEquals(DataError.Remote.Unauthorized, AuthServerError.UserNotFound.toDataError())
    }

    @Test
    fun `Auth login domain mapping maps InvalidCredentials and EmailNotVerified correctly`() {
        val invalidCreds: NetworkError<AuthServerError.Login> = NetworkError.Business(
            error = AuthServerError.InvalidCredentials,
            message = "Invalid credentials"
        )
        assertEquals(AuthError.InvalidCredentials, invalidCreds.toLoginError())

        val emailNotVerified: NetworkError<AuthServerError.Login> = NetworkError.Business(
            error = AuthServerError.EmailNotVerified,
            message = "Email not verified"
        )
        assertEquals(AuthError.EmailNotVerified, emailNotVerified.toLoginError())
    }

    @Test
    fun `Auth login domain mapping maps Common RateLimitExceeded with retry duration`() {
        val rateLimited: NetworkError<AuthServerError.Login> = NetworkError.Common(
            error = CommonServerError.RateLimitExceeded,
            message = "Too many attempts",
            retryAfterSeconds = 120L
        )
        val domainError = rateLimited.toLoginError()

        assertIs<AuthError.Data>(domainError)
        val innerData = domainError.error
        assertIs<DataError.Remote.RateLimited>(innerData)
        assertEquals(120.seconds, innerData.retryAfter)
    }

    @Test
    fun `Auth login domain mapping maps Unknown 429 fallback to RateLimited data error`() {
        val unknown429: NetworkError<AuthServerError.Login> = NetworkError.Unknown(status = 429, retryAfterSeconds = 30L)
        val domainError = unknown429.toLoginError()

        assertIs<AuthError.Data>(domainError)
        val innerData = domainError.error
        assertIs<DataError.Remote.RateLimited>(innerData)
        assertEquals(30.seconds, innerData.retryAfter)
    }

    @Test
    fun `Auth register domain mapping maps UserAlreadyExists to EmailAlreadyInUse`() {
        val userExists: NetworkError<AuthServerError.Register> = NetworkError.Business(
            error = AuthServerError.UserAlreadyExists,
            message = "User already exists"
        )
        assertEquals(AuthError.EmailAlreadyInUse, userExists.toRegisterError())
    }

    @Test
    fun `Auth refreshToken toDataError maps InvalidToken to Unauthorized`() {
        val invalidToken: NetworkError<AuthServerError.RefreshToken> = NetworkError.Business(
            error = AuthServerError.InvalidToken,
            message = "Token invalid"
        )
        assertEquals(DataError.Remote.Unauthorized, invalidToken.toDataError())
    }

    @Test
    fun `Auth logout toDataError maps InvalidToken to Unauthorized`() {
        val invalidToken: NetworkError<AuthServerError.Logout> = NetworkError.Business(
            error = AuthServerError.InvalidToken,
            message = "Token invalid"
        )
        assertEquals(DataError.Remote.Unauthorized, invalidToken.toDataError())
    }
}
