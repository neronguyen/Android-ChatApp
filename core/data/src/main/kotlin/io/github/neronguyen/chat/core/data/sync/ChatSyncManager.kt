package io.github.neronguyen.chat.core.data.sync

import arrow.core.Either
import io.github.neronguyen.chat.core.model.error.DataError

interface ChatSyncManager {
    fun initialize()
    suspend fun performCatchUpSync(): Either<DataError, Unit>
}
