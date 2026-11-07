package io.github.neronguyen.chat.core.data.sync

internal interface ChatSyncScheduler {
    fun schedulePeriodicSync()
    fun enqueueOneTimeCatchUpSync()
    fun cancelAllSyncWork()
}
