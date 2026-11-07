package io.github.neronguyen.chat

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import io.github.neronguyen.chat.core.data.realtime.ChatRealtimeSyncManager
import io.github.neronguyen.chat.core.data.realtime.RealtimeConnectionManager
import io.github.neronguyen.chat.core.data.sync.ChatSyncManager
import javax.inject.Inject

@HiltAndroidApp
class ChatApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var realtimeConnectionManager: RealtimeConnectionManager

    @Inject
    lateinit var chatRealtimeSyncManager: ChatRealtimeSyncManager

    @Inject
    lateinit var chatSyncManager: ChatSyncManager

    override fun onCreate() {
        super.onCreate()
        realtimeConnectionManager.initialize()
        chatRealtimeSyncManager.initialize()
        chatSyncManager.initialize()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(context: Context): ImageLoader {
        return imageLoader
    }
}
