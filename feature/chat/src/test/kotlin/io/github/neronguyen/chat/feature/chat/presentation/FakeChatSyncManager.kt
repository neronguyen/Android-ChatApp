package io.github.neronguyen.chat.feature.chat.presentation

import arrow.core.Either
import io.github.neronguyen.chat.core.data.sync.ChatSyncManager
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.error.DataError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeChatSyncManager(
    initialState: ChatSyncState = ChatSyncState.Idle,
) : ChatSyncManager {
    val syncStateFlow = MutableStateFlow(initialState)
    override val syncState: StateFlow<ChatSyncState> = syncStateFlow.asStateFlow()

    var retryCallCount = 0
    var catchUpCallCount = 0
    var retryResult: Either<DataError, Unit> = Either.Right(Unit)
    var catchUpResult: Either<DataError, Unit> = Either.Right(Unit)

    override fun initialize() {}

    override suspend fun performCatchUpSync(): Either<DataError, Unit> {
        catchUpCallCount++
        return catchUpResult
    }

    override suspend fun retry(): Either<DataError, Unit> {
        retryCallCount++
        return retryResult
    }
}
