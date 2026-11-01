package io.github.neronguyen.chat.core.network

import arrow.core.Either
import io.github.neronguyen.chat.core.network.util.NetworkError
import io.github.neronguyenvn.nerochat.contract.auth.AuthenticatedUserDto
import io.github.neronguyenvn.nerochat.contract.auth.LoginRequest
import io.github.neronguyenvn.nerochat.contract.auth.RefreshTokenRequest
import io.github.neronguyenvn.nerochat.contract.auth.RegisterRequest
import io.github.neronguyenvn.nerochat.contract.auth.UserDto
import io.github.neronguyenvn.nerochat.contract.auth.error.AuthServerError

interface AuthNetworkDataSource {
    suspend fun register(request: RegisterRequest): Either<NetworkError<AuthServerError.Register>, UserDto>
    suspend fun login(request: LoginRequest): Either<NetworkError<AuthServerError.Login>, AuthenticatedUserDto>
    suspend fun refreshToken(request: RefreshTokenRequest): Either<NetworkError<AuthServerError.RefreshToken>, AuthenticatedUserDto>
    suspend fun logout(request: RefreshTokenRequest): Either<NetworkError<AuthServerError.Logout>, Unit>
}
