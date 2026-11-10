package io.github.neronguyen.chat.core.data.repository

import arrow.core.Either
import io.github.neronguyen.chat.core.common.network.AppScope
import io.github.neronguyen.chat.core.common.network.ChatDispatchers
import io.github.neronguyen.chat.core.common.network.Dispatcher
import io.github.neronguyen.chat.core.data.mapper.toAddParticipantsError
import io.github.neronguyen.chat.core.data.mapper.toCreateRoomError
import io.github.neronguyen.chat.core.data.mapper.toDataError
import io.github.neronguyen.chat.core.data.mapper.toDomain
import io.github.neronguyen.chat.core.data.mapper.toEntity
import io.github.neronguyen.chat.core.data.mapper.toFindParticipantError
import io.github.neronguyen.chat.core.data.mapper.toLeaveRoomError
import io.github.neronguyen.chat.core.data.mapper.toSyncRoomError
import io.github.neronguyen.chat.core.database.dao.ChatDao
import io.github.neronguyen.chat.core.database.entity.ChatRoomParticipantCrossRef
import io.github.neronguyen.chat.core.model.ChatParticipant
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.ChatNetworkDataSource
import io.github.neronguyenvn.nerochat.contract.chat.ChatParticipantDto
import io.github.neronguyenvn.nerochat.contract.chat.ChatRoomDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ChatRoomRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val networkDataSource: ChatNetworkDataSource,
    @AppScope private val appScope: CoroutineScope,
    @Dispatcher(ChatDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher
) : ChatRoomRepository {

    private val syncMutex = Mutex()
    private var inFlightSync: Deferred<Either<DataError, List<String>>>? = null

    override fun getChats(): Flow<List<ChatRoom>> {
        return chatDao.getChatsWithDetails().map { relations ->
            relations.map { it.toDomain() }
        }.flowOn(defaultDispatcher)
    }

    override suspend fun syncChats(): Either<DataError, Unit> {
        return syncChatsAndGetChangedRooms().map { }
    }

    override suspend fun syncChatsAndGetChangedRooms(): Either<DataError, List<String>> {
        val deferred = syncMutex.withLock {
            inFlightSync?.takeIf { it.isActive } ?: appScope.async(defaultDispatcher) {
                executeSyncChatsWithChangedRooms()
            }.also { newDeferred ->
                inFlightSync = newDeferred
                newDeferred.invokeOnCompletion {
                    appScope.launch(defaultDispatcher) {
                        syncMutex.withLock {
                            if (inFlightSync == newDeferred) {
                                inFlightSync = null
                            }
                        }
                    }
                }
            }
        }

        return deferred.await()
    }

    private suspend fun executeSyncChatsWithChangedRooms(): Either<DataError, List<String>> {
        return withContext(defaultDispatcher) {
            val response = networkDataSource.getChatRoomsForUser()
            response.fold(
                ifLeft = { networkError ->
                    Either.Left(networkError.toDataError())
                },
                ifRight = { roomDtos ->
                    val localSnapshot = chatDao.getAllRoomsSnapshot().associateBy { it.id }

                    val changedRoomIds = roomDtos.filter { dto ->
                        val local = localSnapshot[dto.id]
                        if (local == null) {
                            true
                        } else {
                            val serverActivity = dto.lastActivityAt.toEpochMilliseconds()
                            val localActivity = local.lastActivityAt
                            val activityAdvanced =
                                localActivity == null || serverActivity > localActivity
                            val serverLastMessageId = dto.lastMessage?.id
                            val messageChanged =
                                serverLastMessageId != null && serverLastMessageId != local.lastMessageId
                            activityAdvanced || messageChanged
                        }
                    }
                        .sortedByDescending { it.lastActivityAt }
                        .map { it.id }

                    val rooms = roomDtos.map { it.toEntity() }

                    val participants = mutableSetOf<ChatParticipantDto>()
                    roomDtos.forEach { participants.addAll(it.participants + it.creator) }
                    val participantEntities = participants.map { it.toEntity() }

                    val lastMessages = roomDtos.mapNotNull {
                        it.lastMessage?.toEntity(MessageDeliveryStatus.SENT)
                    }

                    val crossRefs = roomDtos.flatMap { dto ->
                        val allParticipantIds =
                            (dto.participants.map { it.userId } + dto.creator.userId).distinct()

                        allParticipantIds.map { userId ->
                            ChatRoomParticipantCrossRef(roomId = dto.id, userId = userId)
                        }
                    }

                    chatDao.insertFullChats(
                        rooms = rooms,
                        participants = participantEntities,
                        lastMessages = lastMessages,
                        crossRefs = crossRefs
                    )

                    Either.Right(changedRoomIds)
                }
            )
        }
    }

    override suspend fun findChatParticipantByEmail(email: String): Either<ChatError.FindParticipant, ChatParticipant> {
        return networkDataSource.getParticipantByEmail(email)
            .mapLeft { it.toFindParticipantError() }
            .map { it.toDomain() }
    }

    override suspend fun createDirectChatRoom(
        targetUserId: String,
        message: String
    ): Either<ChatError.CreateRoom, ChatRoom> {
        return networkDataSource.createDirectChatRoom(targetUserId, message)
            .mapLeft { it.toCreateRoomError() }
            .map { dto ->
                saveChatRoomDto(dto)
                dto.toDomain()
            }
    }

    override suspend fun createGroupChatRoom(
        participantIds: List<String>,
        name: String?
    ): Either<ChatError.CreateRoom, ChatRoom> {
        val trimmedName = name?.trim()?.ifEmpty { null }
        return networkDataSource.createGroupChatRoom(participantIds, trimmedName)
            .mapLeft { it.toCreateRoomError() }
            .map { dto ->
                saveChatRoomDto(dto)
                dto.toDomain()
            }
    }

    override fun getChatRoom(roomId: String): Flow<ChatRoom?> {
        return chatDao.getChatWithDetails(roomId).map { it?.toDomain() }.flowOn(defaultDispatcher)
    }

    override suspend fun syncChatRoom(roomId: String): Either<ChatError.SyncRoom, Unit> {
        return networkDataSource.getChatRoom(roomId)
            .mapLeft { it.toSyncRoomError() }
            .map { dto ->
                saveChatRoomDto(dto)
            }
    }

    override suspend fun addParticipantsToGroup(
        roomId: String,
        userIds: List<String>
    ): Either<ChatError.AddParticipants, ChatRoom> {
        return networkDataSource.addParticipantsToGroup(roomId, userIds)
            .mapLeft { it.toAddParticipantsError() }
            .map { dto ->
                saveChatRoomDto(dto)
                dto.toDomain()
            }
    }

    override suspend fun leaveChatRoom(roomId: String): Either<ChatError.LeaveRoom, Unit> {
        return networkDataSource.leaveChatRoom(roomId)
            .mapLeft { it.toLeaveRoomError() }
            .map {
                chatDao.removeRoomAndData(roomId)
            }
    }

    private suspend fun saveChatRoomDto(dto: ChatRoomDto) {
        val participants = (dto.participants + dto.creator)
            .distinctBy { it.userId }
            .map { it.toEntity() }

        chatDao.insertFullChat(
            room = dto.toEntity(),
            participants = participants,
            lastMessage = dto.lastMessage?.toEntity(MessageDeliveryStatus.SENT)
        )
    }
}
