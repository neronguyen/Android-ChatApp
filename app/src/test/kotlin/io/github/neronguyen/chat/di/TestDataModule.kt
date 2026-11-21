package io.github.neronguyen.chat.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.neronguyen.chat.core.data.di.DataModule
import io.github.neronguyen.chat.core.data.realtime.RealtimeConnectionManager
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.data.repository.ChatMessageRepository
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.data.sync.ChatSyncManager
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.RealtimeConnectionState
import io.github.neronguyen.chat.core.network.interceptor.TokenProvider
import io.github.neronguyen.chat.core.network.interceptor.TokenRefresher
import io.github.neronguyen.chat.core.testing.FakeAuthRepository
import io.github.neronguyen.chat.core.testing.FakeChatMessageRepository
import io.github.neronguyen.chat.core.testing.FakeChatRoomRepository
import io.github.neronguyen.chat.core.testing.FakeChatSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DataModule::class],
)
object TestDataModule {

    @Provides
    @Singleton
    fun provideFakeAuthRepository(): FakeAuthRepository = FakeAuthRepository()

    @Provides
    @Singleton
    fun provideAuthRepository(fake: FakeAuthRepository): AuthRepository = fake

    @Provides
    @Singleton
    fun provideTokenRefresher(): TokenRefresher = TokenRefresher { null }

    @Provides
    @Singleton
    fun provideTokenProvider(): TokenProvider = TokenProvider { null }

    @Provides
    @Singleton
    fun provideFakeChatRoomRepository(): FakeChatRoomRepository = FakeChatRoomRepository()

    @Provides
    @Singleton
    fun provideChatRoomRepository(fake: FakeChatRoomRepository): ChatRoomRepository = fake

    @Provides
    @Singleton
    fun provideFakeChatMessageRepository(): FakeChatMessageRepository = FakeChatMessageRepository()

    @Provides
    @Singleton
    fun provideChatMessageRepository(fake: FakeChatMessageRepository): ChatMessageRepository = fake

    @Provides
    @Singleton
    fun provideFakeChatSyncManager(): FakeChatSyncManager =
        FakeChatSyncManager(initialState = ChatSyncState.Synced)

    @Provides
    @Singleton
    fun provideChatSyncManager(fake: FakeChatSyncManager): ChatSyncManager = fake

    @Provides
    @Singleton
    fun provideRealtimeConnectionManager(): RealtimeConnectionManager =
        object : RealtimeConnectionManager {
            override val connectionState: StateFlow<RealtimeConnectionState> =
                MutableStateFlow(RealtimeConnectionState.DISCONNECTED)

            override fun initialize() {}
            override fun reconnect() {}
        }
}
