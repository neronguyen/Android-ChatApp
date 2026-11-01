package io.github.neronguyen.chat.core.data.repository

import arrow.core.Either
import io.github.neronguyen.chat.core.model.ChatMessage
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import kotlinx.coroutines.flow.Flow

interface ChatMessageRepository {
    fun getMessages(roomId: String, limit: Int): Flow<List<ChatMessage>>
    suspend fun syncRecentMessages(roomId: String): Either<DataError, Boolean>
    suspend fun loadOlderMessages(roomId: String): Either<DataError, Boolean>
    suspend fun sendMessage(roomId: String, message: String)
    suspend fun retryMessage(messageId: String)
    suspend fun deleteMessage(messageId: String): Either<ChatError.DeleteMessage, Unit>
}
