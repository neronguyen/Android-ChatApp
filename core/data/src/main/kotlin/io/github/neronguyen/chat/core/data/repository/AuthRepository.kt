package io.github.neronguyen.chat.core.data.repository

import arrow.core.Either
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.model.error.DataError
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    fun getAuthState(): Flow<AuthState>

    suspend fun login(email: String, password: String): Either<AuthError.Login, User>

    suspend fun register(
        email: String,
        displayName: String,
        password: String
    ): Either<AuthError.Register, User>

    suspend fun verifyEmail(email: String, code: String): Either<AuthError.VerifyEmail, Unit>

    suspend fun resendVerification(email: String): Either<DataError, Unit>

    suspend fun forgotPassword(email: String): Either<DataError, Unit>

    suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Either<AuthError.ResetPassword, Unit>

    suspend fun logout(): Either<DataError, Unit>
}
