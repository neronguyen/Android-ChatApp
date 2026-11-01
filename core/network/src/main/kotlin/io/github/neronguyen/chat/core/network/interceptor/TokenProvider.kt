package io.github.neronguyen.chat.core.network.interceptor

fun interface TokenProvider {
    suspend fun getAccessToken(): String?
}
