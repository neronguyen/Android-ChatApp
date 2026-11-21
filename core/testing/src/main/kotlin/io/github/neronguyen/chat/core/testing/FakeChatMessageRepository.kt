package io.github.neronguyen.chat.core.testing

import arrow.core.Either
import io.github.neronguyen.chat.core.data.repository.ChatMessageRepository
import io.github.neronguyen.chat.core.model.ChatMessage
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeChatMessageRepository(
    initialMessages: List<ChatMessage> = emptyList(),
) : ChatMessageRepository {

    val messagesFlow = MutableStateFlow(initialMessages)

    val sentMessages = mutableListOf<Pair<String, String>>()
    val retriedMessageIds = mutableListOf<String>()
    val deletedMessageIds = mutableListOf<String>()
    val syncRecentCalls = mutableListOf<String>()
    val loadOlderCalls = mutableListOf<String>()

    var syncRecentMessagesHandler: suspend (roomId: String) -> Either<DataError, Boolean> =
        { Either.Right(false) }

    var loadOlderMessagesHandler: suspend (roomId: String) -> Either<DataError, Boolean> =
        { Either.Right(false) }

    var sendMessageHandler: suspend (roomId: String, content: String) -> Either<DataError, Unit> =
        { roomId, content ->
            val nextId = "msg-${messagesFlow.value.size + 1}"
            messagesFlow.value = listOf(
                TestFixtures.message(
                    id = nextId,
                    roomId = roomId,
                    senderId = "user-me",
                    content = content,
                    deliveryStatus = MessageDeliveryStatus.SENT,
                ),
            ) + messagesFlow.value
            Either.Right(Unit)
        }

    var retryMessageHandler: suspend (messageId: String) -> Either<DataError, Unit> =
        { messageId ->
            messagesFlow.value = messagesFlow.value.map { msg ->
                if (msg.id == messageId) msg.copy(deliveryStatus = MessageDeliveryStatus.SENT) else msg
            }
            Either.Right(Unit)
        }

    var deleteMessageHandler: suspend (messageId: String) -> Either<ChatError.DeleteMessage, Unit> =
        { messageId ->
            messagesFlow.value = messagesFlow.value.filterNot { it.id == messageId }
            Either.Right(Unit)
        }

    override fun getMessages(roomId: String, limit: Int): Flow<List<ChatMessage>> =
        messagesFlow.map { msgs -> msgs.filter { it.roomId == roomId }.take(limit) }

    override suspend fun syncRecentMessages(roomId: String): Either<DataError, Boolean> {
        syncRecentCalls.add(roomId)
        return syncRecentMessagesHandler(roomId)
    }

    override suspend fun loadOlderMessages(roomId: String): Either<DataError, Boolean> {
        loadOlderCalls.add(roomId)
        return loadOlderMessagesHandler(roomId)
    }

    override suspend fun sendMessage(roomId: String, content: String): Either<DataError, Unit> {
        sentMessages.add(roomId to content)
        return sendMessageHandler(roomId, content)
    }

    override suspend fun retryMessage(messageId: String): Either<DataError, Unit> {
        retriedMessageIds.add(messageId)
        return retryMessageHandler(messageId)
    }

    override suspend fun deleteMessage(messageId: String): Either<ChatError.DeleteMessage, Unit> {
        deletedMessageIds.add(messageId)
        return deleteMessageHandler(messageId)
    }
}
