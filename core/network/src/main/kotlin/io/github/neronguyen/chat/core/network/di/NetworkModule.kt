package io.github.neronguyen.chat.core.network.di

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.neronguyen.chat.core.network.AuthNetworkDataSource
import io.github.neronguyen.chat.core.network.BuildConfig
import io.github.neronguyen.chat.core.network.ChatNetworkDataSource
import io.github.neronguyen.chat.core.network.ChatRealtimeDataSource
import io.github.neronguyen.chat.core.network.interceptor.AuthInterceptor
import io.github.neronguyen.chat.core.network.interceptor.TokenAuthenticator
import io.github.neronguyen.chat.core.network.retrofit.RetrofitAuthNetwork
import io.github.neronguyen.chat.core.network.retrofit.RetrofitChatNetwork
import io.github.neronguyen.chat.core.network.websocket.ChatWebSocketClient
import jakarta.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindAuthNetworkDataSource(
        impl: RetrofitAuthNetwork
    ): AuthNetworkDataSource

    @Binds
    @Singleton
    abstract fun bindChatNetworkDataSource(
        impl: RetrofitChatNetwork
    ): ChatNetworkDataSource

    @Binds
    @Singleton
    abstract fun bindChatRealtimeDataSource(
        impl: ChatWebSocketClient
    ): ChatRealtimeDataSource

    internal companion object {

        @Provides
        @Singleton
        @BaseOkHttp
        fun provideBaseOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = if (BuildConfig.DEBUG) {
                            HttpLoggingInterceptor.Level.BODY
                        } else {
                            HttpLoggingInterceptor.Level.NONE
                        }
                    }
                )
                .callTimeout(15.seconds)
                .build()
        }

        @Provides
        @Singleton
        @AuthenticatedOkHttp
        fun provideAuthenticatedOkHttpClient(
            @BaseOkHttp baseClient: OkHttpClient,
            authInterceptor: AuthInterceptor,
            tokenAuthenticator: TokenAuthenticator
        ): OkHttpClient {
            return baseClient.newBuilder()
                .addInterceptor(authInterceptor)
                .authenticator(tokenAuthenticator)
                .build()
        }

        @Provides
        @Singleton
        @RealtimeOkHttp
        fun provideRealtimeOkHttpClient(
            @AuthenticatedOkHttp client: OkHttpClient
        ): OkHttpClient = client.newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(30.seconds)
            .build()

        @Provides
        @Singleton
        @RefreshOkHttp
        fun provideRefreshOkHttpClient(
            @BaseOkHttp client: OkHttpClient
        ): OkHttpClient = client.newBuilder()
            .dispatcher(Dispatcher())
            .build()

        @Provides
        @Singleton
        fun provideImageLoader(
            @ApplicationContext context: Context,
            @BaseOkHttp okHttpClient: OkHttpClient
        ): ImageLoader {
            return ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(okHttpClient))
                }
                .crossfade(true)
                .build()
        }

        @Provides
        @Singleton
        fun provideNetworkJson(): Json = Json {
            ignoreUnknownKeys = true
        }

        @Provides
        @Singleton
        @BaseUrl
        fun provideBaseUrl(): String = BuildConfig.BASE_URL
    }
}
