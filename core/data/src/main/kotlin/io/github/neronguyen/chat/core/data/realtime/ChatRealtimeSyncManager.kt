package io.github.neronguyen.chat.core.data.realtime

import android.database.sqlite.SQLiteConstraintException
import io.github.neronguyen.chat.core.common.network.AppScope
import io.github.neronguyen.chat.core.common.network.ChatDispatchers
import io.github.neronguyen.chat.core.common.network.Dispatcher
import io.github.neronguyen.chat.core.data.mapper.toEntity
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.database.dao.ChatDao
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.network.ChatRealtimeDataSource
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState.Disconnected
import io.github.neronguyen.chat.core.network.model.websocket.SocketConnectionState.Failed
import io.github.neronguyenvn.nerochat.contract.chat.ChatMessageDto
import io.github.neronguyenvn.nerochat.contract.chat.WsServerEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@Singleton
class ChatRealtimeSyncManager @Inject constructor(
    private val realtimeDataSource: ChatRealtimeDataSource,
    private val chatDao: ChatDao,
    private val chatRoomRepository: ChatRoomRepository,
    @AppScope private val appScope: CoroutineScope,
    @Dispatcher(ChatDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) {
    private val initialized = AtomicBoolean(false)
    private val pendingMutex = Mutex()
    private val pendingMessages = mutableMapOf<String, MutableList<ChatMessageDto>>()
    private val fetchingRooms = mutableSetOf<String>()

    fun initialize() {
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) return

        appScope.launch(ioDispatcher) {
            realtimeDataSource.incomingEvents.collect { event ->
                when (event) {
                    is WsServerEvent.NewMessage -> handleNewMessage(event)
                    is WsServerEvent.MessageDeleted -> handleMessageDeleted(event)
                    is WsServerEvent.ParticipantJoined -> handleParticipantJoined(event)
                    is WsServerEvent.ParticipantLeft -> handleParticipantLeft(event)
                    is WsServerEvent.Error -> {
                        event.messageId?.let { messageId ->
                            chatDao.updateDeliveryStatus(messageId, MessageDeliveryStatus.FAILED)
                        }
                    }
                }
            }
        }

        appScope.launch(ioDispatcher) {
            realtimeDataSource.connectionState.collect { state ->
                if (state is Failed || state is Disconnected) {
                    chatDao.markSendingMessagesAsFailed()
                }
            }
        }
    }

    private suspend fun handleNewMessage(event: WsServerEvent.NewMessage) {
        val message = event.message
        val roomId = message.roomId

        if (chatDao.roomExists(roomId)) {
            upsertMessageSafely(message)
            return
        }

        // Room is not yet cached locally in SQLite. Buffer message and single-flight fetch metadata.
        val shouldTriggerFetch = pendingMutex.withLock {
            if (chatDao.roomExists(roomId)) {
                null
            } else {
                val queue = pendingMessages.getOrPut(roomId) { mutableListOf() }
                if (queue.size < MAX_PENDING_MESSAGES_PER_ROOM) {
                    queue.add(message)
                }
                fetchingRooms.add(roomId)
            }
        }

        if (shouldTriggerFetch == null) {
            upsertMessageSafely(message)
            return
        }

        if (shouldTriggerFetch) {
            appScope.launch(ioDispatcher) {
                fetchRoomAndDrainQueue(roomId)
            }
        }
    }

    private suspend fun fetchRoomAndDrainQueue(roomId: String) {
        val result = chatRoomRepository.syncChatRoom(roomId)

        val queuedMessages = pendingMutex.withLock {
            fetchingRooms.remove(roomId)
            pendingMessages.remove(roomId).orEmpty()
        }

        if (result.isRight()) {
            for (msg in queuedMessages) {
                upsertMessageSafely(msg)
            }
        }
    }

    private suspend fun upsertMessageSafely(message: ChatMessageDto) {
        try {
            val entity =
                message.toEntity(status = if (message.deletedAt != null) MessageDeliveryStatus.DELETED else MessageDeliveryStatus.SENT)
            chatDao.upsertIncomingOrAckedMessage(entity)
        } catch (_: SQLiteConstraintException) {
            // Defensive guard against concurrent room deletion or FK violation
        }
    }

    private suspend fun handleMessageDeleted(event: WsServerEvent.MessageDeleted) {
        chatDao.updateDeliveryStatus(event.messageId, MessageDeliveryStatus.DELETED)
    }

    private suspend fun handleParticipantJoined(event: WsServerEvent.ParticipantJoined) {
        if (chatDao.roomExists(event.roomId)) {
            upsertParticipantJoinedSafely(event)
            return
        }

        appScope.launch(ioDispatcher) {
            val result = chatRoomRepository.syncChatRoom(event.roomId)
            if (result.isRight()) {
                upsertParticipantJoinedSafely(event)
            }
        }
    }

    private suspend fun upsertParticipantJoinedSafely(event: WsServerEvent.ParticipantJoined) {
        try {
            chatDao.handleParticipantJoined(
                roomId = event.roomId,
                newUsers = event.newUsers.map { it.toEntity() },
                addedBy = event.addedBy?.toEntity()
            )
        } catch (_: SQLiteConstraintException) {
            // Defensive guard
        }
    }

    private suspend fun handleParticipantLeft(event: WsServerEvent.ParticipantLeft) {
        chatDao.handleParticipantLeft(
            roomId = event.roomId,
            leftUser = event.leftUser.toEntity(),
            kickedBy = event.kickedBy?.toEntity()
        )
    }

    companion object {
        const val MAX_PENDING_MESSAGES_PER_ROOM = 50
    }
}
