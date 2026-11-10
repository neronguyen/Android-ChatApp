package io.github.neronguyen.chat.sync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import arrow.core.Either
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.neronguyen.chat.core.data.sync.ChatSyncManager
import io.github.neronguyen.chat.core.model.error.DataError

@HiltWorker
internal class ChatSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val chatSyncManager: ChatSyncManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return when (val result = chatSyncManager.performCatchUpSync()) {
            is Either.Right -> Result.success()
            is Either.Left -> {
                if (result.value.isRetryable() && runAttemptCount < MAX_RUN_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }

    companion object {
        const val MAX_RUN_ATTEMPTS = 3
    }
}

internal fun DataError.isRetryable(): Boolean {
    return when (this) {
        is DataError.Connectivity.NoInternet,
        is DataError.Connectivity.Timeout,
        is DataError.Remote.RateLimited,
        is DataError.Remote.Unavailable -> true
        else -> false
    }
}
