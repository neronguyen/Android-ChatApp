package io.github.neronguyen.chat.core.network.interceptor

fun interface TokenRefresher {
    suspend fun refresh(staleToken: String): String?
}
