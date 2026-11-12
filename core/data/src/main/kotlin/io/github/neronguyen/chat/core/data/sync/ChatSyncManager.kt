package io.github.neronguyen.chat.core.data.sync

import arrow.core.Either
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.error.DataError
import kotlinx.coroutines.flow.StateFlow

interface ChatSyncManager {
    val syncState: StateFlow<ChatSyncState>
    fun initialize()
    suspend fun performCatchUpSync(): Either<DataError, Unit>
    suspend fun retry(): Either<DataError, Unit>
}
