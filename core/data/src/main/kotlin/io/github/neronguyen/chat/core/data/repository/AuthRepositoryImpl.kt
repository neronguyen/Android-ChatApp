package io.github.neronguyen.chat.core.data.repository

import arrow.core.Either
import io.github.neronguyen.chat.core.data.mapper.toDataError
import io.github.neronguyen.chat.core.data.mapper.toDomain
import io.github.neronguyen.chat.core.data.mapper.toDomainUser
import io.github.neronguyen.chat.core.data.mapper.toLoginError
import io.github.neronguyen.chat.core.data.mapper.toRegisterError
import io.github.neronguyen.chat.core.database.dao.ChatDao
import io.github.neronguyen.chat.core.datastore.TokenDataSource
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.AuthNetworkDataSource
import io.github.neronguyen.chat.core.network.interceptor.TokenRefresher
import io.github.neronguyenvn.nerochat.contract.auth.LoginRequest
import io.github.neronguyenvn.nerochat.contract.auth.RefreshTokenRequest
import io.github.neronguyenvn.nerochat.contract.auth.RegisterRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AuthRepositoryImpl @Inject constructor(
    private val networkDataSource: AuthNetworkDataSource,
    private val tokenDataSource: TokenDataSource,
    private val chatDao: ChatDao,
) : AuthRepository, TokenRefresher {

    private val refreshMutex = Mutex()

    override fun getAuthState(): Flow<AuthState> {
        return tokenDataSource.authState
    }

    override suspend fun refresh(staleToken: String): String? {
        return refreshToken(staleToken).getOrNull()
    }

    override suspend fun login(
        email: String,
        password: String,
    ): Either<AuthError.Login, User> {
        return networkDataSource.login(LoginRequest(email = email, password = password))
            .mapLeft { it.toLoginError() }
            .map { response ->
                val user = response.toDomainUser()
                tokenDataSource.saveAuthData(user, response.accessToken, response.refreshToken)
                user
            }
    }

    override suspend fun register(
        email: String,
        displayName: String,
        password: String,
    ): Either<AuthError.Register, User> {
        return networkDataSource.register(
            RegisterRequest(
                email = email,
                displayName = displayName,
                password = password,
            ),
        ).mapLeft { it.toRegisterError() }
        .map { userDto -> userDto.toDomain() }
    }

    override suspend fun logout(): Either<DataError, Unit> {
        return refreshMutex.withLock {
            val currentRefreshToken = tokenDataSource.getRefreshToken()
            if (currentRefreshToken != null) {
                networkDataSource.logout(RefreshTokenRequest(currentRefreshToken))
            }

            tokenDataSource.clearAuthData()
            chatDao.clearAllChatData()
            Either.Right(Unit)
        }
    }

    private suspend fun refreshToken(staleToken: String): Either<DataError, String> {
        return refreshMutex.withLock {
            val currentAccessToken = tokenDataSource.getAccessToken()
            if (currentAccessToken != null && currentAccessToken != staleToken) {
                return Either.Right(currentAccessToken)
            }

            val currentRefreshToken = tokenDataSource.getRefreshToken()
            if (currentRefreshToken.isNullOrBlank()) {
                tokenDataSource.clearAuthData()
                chatDao.clearAllChatData()
                return Either.Left(DataError.Local.NotAuthenticated)
            }

            val result = networkDataSource.refreshToken(RefreshTokenRequest(currentRefreshToken))
            result.fold(
                ifLeft = { networkError ->
                    val dataError = networkError.toDataError()
                    if (dataError is DataError.Remote.Unauthorized) {
                        tokenDataSource.clearAuthData()
                        chatDao.clearAllChatData()
                    }
                    Either.Left(dataError)
                },
                ifRight = { response ->
                    tokenDataSource.saveAuthData(
                        response.toDomainUser(),
                        response.accessToken,
                        response.refreshToken,
                    )
                    Either.Right(response.accessToken)
                },
            )
        }
    }
}
