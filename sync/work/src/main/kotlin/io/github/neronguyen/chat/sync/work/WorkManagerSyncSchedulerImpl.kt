package io.github.neronguyen.chat.sync.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.neronguyen.chat.core.data.sync.ChatSyncScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WorkManagerSyncSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ChatSyncScheduler {

    private val workManager by lazy { WorkManager.getInstance(context) }

    override fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<ChatSyncWorker>(
            PERIODIC_SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES,
            PERIODIC_SYNC_FLEX_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_SYNC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    override fun enqueueOneTimeCatchUpSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeRequest = OneTimeWorkRequestBuilder<ChatSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                ONE_TIME_BACKOFF_SECONDS, TimeUnit.SECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_ONETIME_SYNC_NAME,
            ExistingWorkPolicy.KEEP,
            oneTimeRequest
        )
    }

    override fun cancelAllSyncWork() {
        workManager.cancelUniqueWork(UNIQUE_PERIODIC_SYNC_NAME)
        workManager.cancelUniqueWork(UNIQUE_ONETIME_SYNC_NAME)
    }

    companion object {
        const val UNIQUE_PERIODIC_SYNC_NAME = "chat_periodic_sync"
        const val UNIQUE_ONETIME_SYNC_NAME = "chat_onetime_catchup_sync"
        private const val PERIODIC_SYNC_INTERVAL_MINUTES = 15L
        private const val PERIODIC_SYNC_FLEX_MINUTES = 5L
        private const val ONE_TIME_BACKOFF_SECONDS = 30L
    }
}
