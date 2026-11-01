package io.github.neronguyen.chat.core.network.interceptor

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
internal class AuthInterceptor @Inject constructor(
    private val tokenProvider: TokenProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = runBlocking {
            tokenProvider.getAccessToken()
        }

        val authenticatedRequest =
            if (!token.isNullOrBlank() && request.header(HEADER_AUTHORIZATION) == null) {
                request.newBuilder()
                    .header(HEADER_AUTHORIZATION, "$TOKEN_TYPE_BEARER $token")
                    .build()
            } else {
                request
            }

        return chain.proceed(authenticatedRequest)
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val TOKEN_TYPE_BEARER = "Bearer"
    }
}
