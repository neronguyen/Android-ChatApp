package io.github.neronguyen.chat.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.neronguyen.chat.core.data.realtime.RealtimeConnectionManager
import io.github.neronguyen.chat.core.data.realtime.RealtimeConnectionManagerImpl
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.data.repository.AuthRepositoryImpl
import io.github.neronguyen.chat.core.data.repository.ChatMessageRepository
import io.github.neronguyen.chat.core.data.repository.ChatMessageRepositoryImpl
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepositoryImpl
import io.github.neronguyen.chat.core.datastore.TokenDataSource
import io.github.neronguyen.chat.core.network.interceptor.TokenProvider
import io.github.neronguyen.chat.core.network.interceptor.TokenRefresher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    internal abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    internal abstract fun bindTokenRefresher(
        impl: AuthRepositoryImpl
    ): TokenRefresher

    @Binds
    @Singleton
    internal abstract fun bindChatRoomRepository(
        impl: ChatRoomRepositoryImpl
    ): ChatRoomRepository

    @Binds
    @Singleton
    internal abstract fun bindChatMessageRepository(
        impl: ChatMessageRepositoryImpl
    ): ChatMessageRepository

    @Binds
    @Singleton
    internal abstract fun bindRealtimeConnectionManager(
        impl: RealtimeConnectionManagerImpl
    ): RealtimeConnectionManager

    companion object {
        @Provides
        @Singleton
        internal fun provideTokenProvider(
            tokenDataSource: TokenDataSource
        ): TokenProvider = TokenProvider { tokenDataSource.getAccessToken() }
    }
}
