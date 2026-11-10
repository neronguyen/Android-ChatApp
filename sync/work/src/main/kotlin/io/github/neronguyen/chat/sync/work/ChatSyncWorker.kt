package io.github.neronguyen.chat.sync.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import arrow.core.Either
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.neronguyen.chat.core.data.sync.ChatSyncManager
import io.github.neronguyen.chat.core.model.error.isRetryable

@HiltWorker
internal class ChatSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val chatSyncManager: ChatSyncManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return executeSyncWork(chatSyncManager, runAttemptCount)
    }

    companion object {
        const val MAX_RUN_ATTEMPTS = 3
    }
}

internal suspend fun executeSyncWork(
    chatSyncManager: ChatSyncManager,
    runAttemptCount: Int,
): ListenableWorker.Result {
    return when (val result = chatSyncManager.performCatchUpSync()) {
        is Either.Right -> ListenableWorker.Result.success()
        is Either.Left -> {
            if (result.value.isRetryable() && runAttemptCount < ChatSyncWorker.MAX_RUN_ATTEMPTS) {
                ListenableWorker.Result.retry()
            } else {
                ListenableWorker.Result.failure()
            }
        }
    }
}
