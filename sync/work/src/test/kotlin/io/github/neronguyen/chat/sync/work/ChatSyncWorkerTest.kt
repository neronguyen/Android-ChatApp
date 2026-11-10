package io.github.neronguyen.chat.sync.work

import androidx.work.ListenableWorker
import arrow.core.Either
import io.github.neronguyen.chat.core.data.sync.ChatSyncManager
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.error.DataError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSyncWorkerTest {

    @Test
    fun `isRetryable correctly identifies transient network and server errors`() {
        assertTrue(DataError.Connectivity.NoInternet.isRetryable())
        assertTrue(DataError.Connectivity.Timeout.isRetryable())
        assertTrue(DataError.Remote.RateLimited().isRetryable())
        assertTrue(DataError.Remote.Unavailable.isRetryable())

        assertFalse(DataError.Remote.Unauthorized.isRetryable())
        assertFalse(DataError.Remote.Forbidden.isRetryable())
        assertFalse(DataError.Unexpected.Serialization.isRetryable())
        assertFalse(DataError.Unexpected.UnknownCode(500).isRetryable())
        assertFalse(DataError.Local.NotAuthenticated.isRetryable())
    }

    @Test
    fun `executeSyncWork returns success when performCatchUpSync succeeds`() = runTest {
        val manager = FakeChatSyncManager(Either.Right(Unit))

        val result = executeSyncWork(manager, runAttemptCount = 0)

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, manager.callCount)
    }

    @Test
    fun `executeSyncWork returns retry for retryable error below MAX_RUN_ATTEMPTS`() = runTest {
        val manager = FakeChatSyncManager(Either.Left(DataError.Connectivity.NoInternet))

        assertEquals(ListenableWorker.Result.retry(), executeSyncWork(manager, runAttemptCount = 0))
        assertEquals(ListenableWorker.Result.retry(), executeSyncWork(manager, runAttemptCount = 2))
    }

    @Test
    fun `executeSyncWork returns failure when runAttemptCount reaches MAX_RUN_ATTEMPTS or error is non-retryable`() =
        runTest {
            val retryableManager = FakeChatSyncManager(Either.Left(DataError.Connectivity.Timeout))
            assertEquals(
                ListenableWorker.Result.failure(),
                executeSyncWork(retryableManager, runAttemptCount = ChatSyncWorker.MAX_RUN_ATTEMPTS)
            )

            val nonRetryableManager =
                FakeChatSyncManager(Either.Left(DataError.Remote.Unauthorized))
            assertEquals(
                ListenableWorker.Result.failure(),
                executeSyncWork(nonRetryableManager, runAttemptCount = 0)
            )
        }

    private class FakeChatSyncManager(
        private val result: Either<DataError, Unit>
    ) : ChatSyncManager {
        var callCount = 0
        override val syncState: StateFlow<ChatSyncState> = MutableStateFlow(ChatSyncState.Idle)
        override fun initialize() {}
        override suspend fun performCatchUpSync(): Either<DataError, Unit> {
            callCount++
            return result
        }
    }
}
