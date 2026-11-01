package io.github.neronguyen.chat.core.data.repository

import arrow.core.Either
import io.github.neronguyen.chat.core.common.network.ChatDispatchers
import io.github.neronguyen.chat.core.common.network.Dispatcher
import io.github.neronguyen.chat.core.data.mapper.toDataError
import io.github.neronguyen.chat.core.data.mapper.toDeleteMessageError
import io.github.neronguyen.chat.core.data.mapper.toDomain
import io.github.neronguyen.chat.core.data.mapper.toEntity
import io.github.neronguyen.chat.core.database.dao.ChatDao
import io.github.neronguyen.chat.core.database.entity.ChatMessageEntity
import io.github.neronguyen.chat.core.datastore.TokenDataSource
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.ChatMessage
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.ChatNetworkDataSource
import io.github.neronguyen.chat.core.network.ChatRealtimeDataSource
import io.github.neronguyenvn.nerochat.contract.chat.WsClientMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Singleton
internal class ChatMessageRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val realtimeDataSource: ChatRealtimeDataSource,
    private val networkDataSource: ChatNetworkDataSource,
    private val tokenDataSource: TokenDataSource,
    @Dispatcher(ChatDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher
) : ChatMessageRepository {

    override fun getMessages(roomId: String, limit: Int): Flow<List<ChatMessage>> {
        require(limit > 0) { "limit must be greater than 0" }

        return chatDao.getRecentMessages(roomId, limit).map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(defaultDispatcher)
    }

    override suspend fun syncRecentMessages(roomId: String): Either<DataError, Boolean> {
        return networkDataSource.getMessagesForChatRoom(roomId, before = null)
            .mapLeft { it.toDataError() }
            .map { slice ->
                val remoteEntities = slice.messages.map { it.toEntity(MessageDeliveryStatus.SENT) }
                chatDao.syncRecentMessagesTransaction(roomId, remoteEntities)
                slice.hasMore
            }
    }

    override suspend fun loadOlderMessages(roomId: String): Either<DataError, Boolean> {
        val oldestTimestamp = chatDao.getOldestSentMessageTimestamp(roomId)
            ?: return syncRecentMessages(roomId)

        val before = Instant.fromEpochMilliseconds(oldestTimestamp)

        return networkDataSource.getMessagesForChatRoom(
            roomId = roomId,
            before = before
        ).mapLeft { it.toDataError() }
        .map { slice ->
            if (slice.messages.isNotEmpty()) {
                chatDao.upsertMessages(slice.messages.map { it.toEntity(MessageDeliveryStatus.SENT) })
            }

            slice.hasMore
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun sendMessage(
        roomId: String,
        message: String,
    ) {
        val auth = tokenDataSource.authState.firstOrNull() as? AuthState.Authenticated
            ?: return

        val messageId = Uuid.generateV7().toString()
        val now = System.currentTimeMillis()

        val sendingEntity = ChatMessageEntity(
            id = messageId,
            roomId = roomId,
            senderId = auth.user.id,
            content = message,
            timestamp = now,
            deliveryStatus = MessageDeliveryStatus.SENDING,
        )

        chatDao.insertSendingMessage(sendingEntity)

        val enqueued = realtimeDataSource.sendMessage(
            WsClientMessage.NewMessage(
                roomId = roomId,
                content = message,
                messageId = messageId,
            )
        )
        if (!enqueued) {
            chatDao.updateDeliveryStatus(messageId, MessageDeliveryStatus.FAILED)
        }
    }

    override suspend fun retryMessage(messageId: String) {
        val entity = chatDao.getMessageById(messageId) ?: return

        chatDao.updateDeliveryStatus(messageId, MessageDeliveryStatus.SENDING)

        val enqueued = realtimeDataSource.sendMessage(
            WsClientMessage.NewMessage(
                roomId = entity.roomId,
                content = entity.content,
                messageId = messageId,
            )
        )
        if (!enqueued) {
            chatDao.updateDeliveryStatus(messageId, MessageDeliveryStatus.FAILED)
        }
    }

    override suspend fun deleteMessage(messageId: String): Either<ChatError.DeleteMessage, Unit> {
        return networkDataSource.deleteMessage(messageId)
            .mapLeft { it.toDeleteMessageError() }
            .map {
                chatDao.updateDeliveryStatus(messageId, MessageDeliveryStatus.DELETED)
            }
    }
}
