package io.github.neronguyen.chat.core.network.retrofit

import arrow.core.Either
import io.github.neronguyen.chat.core.network.AuthNetworkDataSource
import io.github.neronguyen.chat.core.network.BuildConfig
import io.github.neronguyen.chat.core.network.di.BaseOkHttp
import io.github.neronguyen.chat.core.network.di.RefreshOkHttp
import io.github.neronguyen.chat.core.network.util.NetworkError
import io.github.neronguyen.chat.core.network.util.safeCall
import io.github.neronguyenvn.nerochat.contract.auth.AuthenticatedUserDto
import io.github.neronguyenvn.nerochat.contract.auth.LoginRequest
import io.github.neronguyenvn.nerochat.contract.auth.RefreshTokenRequest
import io.github.neronguyenvn.nerochat.contract.auth.RegisterRequest
import io.github.neronguyenvn.nerochat.contract.auth.UserDto
import io.github.neronguyenvn.nerochat.contract.auth.error.AuthServerError
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

private interface RetrofitAuthNetworkApi {

    @POST("api/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<UserDto>

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthenticatedUserDto>

    @POST("api/auth/refresh-token")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<AuthenticatedUserDto>

    @POST("api/auth/logout")
    suspend fun logout(
        @Body request: RefreshTokenRequest
    ): Response<Unit>
}

@Singleton
internal class RetrofitAuthNetwork @Inject constructor(
    private val networkJson: Json,
    @BaseOkHttp baseClient: dagger.Lazy<OkHttpClient>,
    @RefreshOkHttp refreshClient: dagger.Lazy<OkHttpClient>,
) : AuthNetworkDataSource {

    private val publicApi by lazy { createApi(baseClient.get()) }
    private val refreshApi by lazy { createApi(refreshClient.get()) }

    override suspend fun register(request: RegisterRequest): Either<NetworkError<AuthServerError.Register>, UserDto> =
        safeCall(networkJson, AuthServerError.Register) { publicApi.register(request) }

    override suspend fun login(request: LoginRequest): Either<NetworkError<AuthServerError.Login>, AuthenticatedUserDto> =
        safeCall(networkJson, AuthServerError.Login) { publicApi.login(request) }

    override suspend fun refreshToken(request: RefreshTokenRequest): Either<NetworkError<AuthServerError.RefreshToken>, AuthenticatedUserDto> =
        safeCall(networkJson, AuthServerError.RefreshToken) { refreshApi.refreshToken(request) }

    override suspend fun logout(request: RefreshTokenRequest): Either<NetworkError<AuthServerError.Logout>, Unit> =
        safeCall(networkJson, AuthServerError.Logout) { publicApi.logout(request) }

    private fun createApi(client: OkHttpClient): RetrofitAuthNetworkApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(networkJson.asConverterFactory(contentType))
            .build()
            .create(RetrofitAuthNetworkApi::class.java)
    }
}
