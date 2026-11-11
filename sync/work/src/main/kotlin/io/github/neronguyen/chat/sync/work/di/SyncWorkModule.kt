package io.github.neronguyen.chat.sync.work.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.neronguyen.chat.core.data.sync.ChatSyncScheduler
import io.github.neronguyen.chat.sync.work.WorkManagerSyncSchedulerImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SyncWorkModule {

    @Binds
    @Singleton
    internal abstract fun bindChatSyncScheduler(
        impl: WorkManagerSyncSchedulerImpl
    ): ChatSyncScheduler
}
