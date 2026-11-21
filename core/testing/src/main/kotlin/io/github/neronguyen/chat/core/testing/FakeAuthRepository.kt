package io.github.neronguyen.chat.core.testing

import arrow.core.Either
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.model.error.DataError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository(
    initialAuthState: AuthState = AuthState.Unauthenticated,
) : AuthRepository {

    val authStateFlow = MutableStateFlow(initialAuthState)

    var loginHandler: suspend (email: String, password: String) -> Either<AuthError.Login, User> =
        { email, _ ->
            Either.Right(
                TestFixtures.user(
                    id = "user-1",
                    email = email,
                    displayName = "Elena Rostova",
                    isEmailVerified = true,
                ),
            )
        }

    var registerHandler: suspend (
        email: String,
        displayName: String,
        password: String,
    ) -> Either<AuthError.Register, User> = { email, displayName, _ ->
        Either.Right(
            TestFixtures.user(
                id = "user-1",
                email = email,
                displayName = displayName,
                isEmailVerified = false,
            ),
        )
    }

    var verifyEmailHandler: suspend (email: String, code: String) -> Either<AuthError.VerifyEmail, Unit> =
        { _, _ -> Either.Right(Unit) }

    var resendVerificationHandler: suspend (email: String) -> Either<DataError, Unit> =
        { Either.Right(Unit) }

    var forgotPasswordHandler: suspend (email: String) -> Either<DataError, Unit> =
        { Either.Right(Unit) }

    var resetPasswordHandler: suspend (
        email: String,
        code: String,
        newPassword: String,
    ) -> Either<AuthError.ResetPassword, Unit> = { _, _, _ -> Either.Right(Unit) }

    var logoutHandler: suspend () -> Either<DataError, Unit> = { Either.Right(Unit) }

    val loginCalls = mutableListOf<Pair<String, String>>()
    val registerCalls = mutableListOf<Triple<String, String, String>>()
    val verifyEmailCalls = mutableListOf<Pair<String, String>>()
    val resendVerificationCalls = mutableListOf<String>()
    val forgotPasswordCalls = mutableListOf<String>()
    val resetPasswordCalls = mutableListOf<Triple<String, String, String>>()
    var logoutCallCount = 0

    override fun getAuthState(): Flow<AuthState> = authStateFlow.asStateFlow()

    override suspend fun login(email: String, password: String): Either<AuthError.Login, User> {
        loginCalls.add(email to password)
        return loginHandler(email, password)
    }

    override suspend fun register(
        email: String,
        displayName: String,
        password: String,
    ): Either<AuthError.Register, User> {
        registerCalls.add(Triple(email, displayName, password))
        return registerHandler(email, displayName, password)
    }

    override suspend fun verifyEmail(
        email: String,
        code: String,
    ): Either<AuthError.VerifyEmail, Unit> {
        verifyEmailCalls.add(email to code)
        return verifyEmailHandler(email, code)
    }

    override suspend fun resendVerification(email: String): Either<DataError, Unit> {
        resendVerificationCalls.add(email)
        return resendVerificationHandler(email)
    }

    override suspend fun forgotPassword(email: String): Either<DataError, Unit> {
        forgotPasswordCalls.add(email)
        return forgotPasswordHandler(email)
    }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Either<AuthError.ResetPassword, Unit> {
        resetPasswordCalls.add(Triple(email, code, newPassword))
        return resetPasswordHandler(email, code, newPassword)
    }

    override suspend fun logout(): Either<DataError, Unit> {
        logoutCallCount++
        val result = logoutHandler()
        if (result.isRight()) {
            authStateFlow.value = AuthState.Unauthenticated
        }
        return result
    }
}
