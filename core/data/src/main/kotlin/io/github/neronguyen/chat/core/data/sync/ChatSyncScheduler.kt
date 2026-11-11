package io.github.neronguyen.chat.core.data.sync

interface ChatSyncScheduler {
    fun schedulePeriodicSync()
    fun enqueueOneTimeCatchUpSync()
    fun cancelAllSyncWork()
}
