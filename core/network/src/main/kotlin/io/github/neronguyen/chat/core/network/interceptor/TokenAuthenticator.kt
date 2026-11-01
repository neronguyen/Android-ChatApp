package io.github.neronguyen.chat.core.network.interceptor

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

@Singleton
internal class TokenAuthenticator @Inject constructor(
    private val tokenProvider: TokenProvider,
    private val tokenRefresherProvider: dagger.Lazy<TokenRefresher>
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 3) {
            return null
        }

        val failedToken = response.request.header(AuthInterceptor.HEADER_AUTHORIZATION)
            ?.removePrefix("${AuthInterceptor.TOKEN_TYPE_BEARER} ")
            ?.trim()

        val currentToken = runBlocking {
            tokenProvider.getAccessToken()
        }

        // Token was already refreshed by another concurrent request
        val validToken = if (!currentToken.isNullOrBlank() && currentToken != failedToken) {
            currentToken
        } else {
            runBlocking {
                tokenRefresherProvider.get().refresh(staleToken = failedToken.orEmpty())
            }
        } ?: return null

        return response.request
            .newBuilder()
            .header(
                AuthInterceptor.HEADER_AUTHORIZATION,
                "${AuthInterceptor.TOKEN_TYPE_BEARER} $validToken"
            )
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
