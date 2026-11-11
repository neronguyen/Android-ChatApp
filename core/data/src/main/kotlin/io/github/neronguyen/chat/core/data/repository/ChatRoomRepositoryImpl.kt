package io.github.neronguyen.chat.core.data.repository

import arrow.core.Either
import io.github.neronguyen.chat.core.common.coroutine.KeyedSingleFlight
import io.github.neronguyen.chat.core.common.coroutine.SingleFlight
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ChatRoomRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val networkDataSource: ChatNetworkDataSource,
    @AppScope private val appScope: CoroutineScope,
    @Dispatcher(ChatDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher
) : ChatRoomRepository {

    private val syncChatsFlight = SingleFlight<Either<DataError, List<String>>>(
        scope = appScope,
        dispatcher = defaultDispatcher
    )
    private val syncRoomFlight = KeyedSingleFlight<String, Either<ChatError.SyncRoom, Unit>>(
        scope = appScope,
        dispatcher = defaultDispatcher
    )

    override fun getChats(): Flow<List<ChatRoom>> {
        return chatDao.getChatsWithDetails().map { relations ->
            relations.map { it.toDomain() }
        }.flowOn(defaultDispatcher)
    }

    override suspend fun syncChats(): Either<DataError, Unit> {
        return syncChatsAndGetChangedRooms().void()
    }

    override suspend fun syncChatsAndGetChangedRooms(): Either<DataError, List<String>> =
        syncChatsFlight.execute {
            executeSyncChatsWithChangedRooms()
        }

    private suspend fun executeSyncChatsWithChangedRooms(): Either<DataError, List<String>> {
        val response = networkDataSource.getChatRoomsForUser()
        return response.fold(
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
                roomDtos.forEach { dto ->
                    participants.addAll(dto.participants)
                    participants.add(dto.creator)
                }
                val participantEntities = participants.map { it.toEntity() }

                val lastMessages = roomDtos.mapNotNull {
                    it.lastMessage?.toEntity(MessageDeliveryStatus.SENT)
                }

                val crossRefs = roomDtos.flatMap { dto ->
                    val allParticipantIds = buildSet {
                        dto.participants.forEach { add(it.userId) }
                        add(dto.creator.userId)
                    }

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

    override suspend fun syncChatRoom(roomId: String): Either<ChatError.SyncRoom, Unit> =
        syncRoomFlight.execute(roomId) {
            networkDataSource.getChatRoom(roomId)
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

private fun <A, B> Either<A, B>.void(): Either<A, Unit> = map { }
