package io.github.neronguyen.chat.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.neronguyen.chat.core.database.entity.ChatMessageEntity
import io.github.neronguyen.chat.core.database.entity.ChatParticipantEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomParticipantCrossRef
import io.github.neronguyen.chat.core.database.model.ChatRoomWithDetails
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // --- Queries ---

    @Transaction
    @Query("SELECT * FROM chat_rooms ORDER BY lastActivityAt DESC")
    fun getChatsWithDetails(): Flow<List<ChatRoomWithDetails>>

    @Transaction
    @Query("SELECT * FROM chat_rooms WHERE id = :roomId")
    fun getChatWithDetails(roomId: String): Flow<ChatRoomWithDetails?>

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE roomId = :roomId
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
        """
    )
    fun getRecentMessages(
        roomId: String,
        limit: Int
    ): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): ChatMessageEntity?

    @Query(
        """
        SELECT MIN(timestamp) FROM chat_messages 
        WHERE roomId = :roomId AND deliveryStatus = 'SENT'
    """
    )
    suspend fun getOldestSentMessageTimestamp(roomId: String): Long?

    @Query(
        """
        SELECT MAX(timestamp) FROM chat_messages 
        WHERE roomId = :roomId AND deliveryStatus = 'SENT'
    """
    )
    suspend fun getNewestSentMessageTimestamp(roomId: String): Long?

    @Query(
        """
        SELECT * FROM chat_messages
        WHERE roomId = :roomId
        ORDER BY timestamp DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getNewestMessageForRoom(roomId: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_room_participants WHERE roomId IN (:roomIds)")
    suspend fun getCrossRefsForRooms(roomIds: List<String>): List<ChatRoomParticipantCrossRef>

    // --- Upserts ---

    @Upsert
    suspend fun upsertRoom(room: ChatRoomEntity)

    @Upsert
    suspend fun upsertRooms(rooms: List<ChatRoomEntity>)

    @Upsert
    suspend fun upsertParticipant(participant: ChatParticipantEntity)

    @Upsert
    suspend fun upsertParticipants(participants: List<ChatParticipantEntity>)

    @Upsert
    suspend fun upsertCrossRefs(crossRefs: List<ChatRoomParticipantCrossRef>)

    @Upsert
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Upsert
    suspend fun upsertMessages(messages: List<ChatMessageEntity>)

    // --- Updates & Deletes ---

    @Query("UPDATE chat_messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: MessageDeliveryStatus)

    @Query(
        """
        UPDATE chat_messages 
        SET deliveryStatus = :failedStatus 
        WHERE deliveryStatus = :sendingStatus
        """
    )
    suspend fun markSendingMessagesAsFailed(
        sendingStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENDING,
        failedStatus: MessageDeliveryStatus = MessageDeliveryStatus.FAILED
    )

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId AND deliveryStatus = 'SENT'")
    suspend fun clearSentMessagesForRoom(roomId: String)

    @Query("DELETE FROM chat_room_participants WHERE roomId = :roomId AND userId = :userId")
    suspend fun deleteParticipantCrossRef(roomId: String, userId: String)

    @Query(
        """
        UPDATE chat_rooms
        SET lastMessageId = :messageId,
        lastActivityAt = :timestamp
        WHERE id = :roomId
        """
    )
    suspend fun updateRoomLastActivity(
        roomId: String,
        messageId: String?,
        timestamp: Long?
    )

    @Query("DELETE FROM chat_rooms WHERE id = :roomId")
    suspend fun deleteRoom(roomId: String)

    @Query("DELETE FROM chat_room_participants WHERE roomId = :roomId")
    suspend fun deleteCrossRefsForRoom(roomId: String)

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId")
    suspend fun deleteMessagesForRoom(roomId: String)

    @Query("DELETE FROM chat_rooms")
    suspend fun clearRooms()

    @Query("DELETE FROM chat_messages")
    suspend fun clearMessages()

    @Query("DELETE FROM chat_participants")
    suspend fun clearParticipants()

    @Query("DELETE FROM chat_room_participants")
    suspend fun clearCrossRefs()

    @Delete
    suspend fun deleteCrossRefs(crossRefs: List<ChatRoomParticipantCrossRef>)

    // --- Transactions ---

    @Transaction
    suspend fun clearAllChatData() {
        clearCrossRefs()
        clearMessages()
        clearRooms()
        clearParticipants()
    }

    @Transaction
    suspend fun removeRoomAndData(roomId: String) {
        deleteCrossRefsForRoom(roomId)
        deleteMessagesForRoom(roomId)
        deleteRoom(roomId)
    }


    @Transaction
    suspend fun insertSendingMessage(message: ChatMessageEntity) {
        upsertMessage(message)
        updateRoomLastActivity(
            roomId = message.roomId,
            messageId = message.id,
            timestamp = message.timestamp
        )
    }

    @Transaction
    suspend fun syncRecentMessagesTransaction(
        roomId: String,
        remoteMessages: List<ChatMessageEntity>
    ) {
        require(remoteMessages.all { it.roomId == roomId }) {
            "All remote messages must belong to roomId=$roomId"
        }

        if (remoteMessages.isEmpty()) {
            clearSentMessagesForRoom(roomId)
        } else {
            val newestLocalTimestamp = getNewestSentMessageTimestamp(roomId)
            val oldestRemoteTimestamp = remoteMessages.minOf { it.timestamp }

            val hasMessageGap =
                newestLocalTimestamp != null &&
                        oldestRemoteTimestamp > newestLocalTimestamp

            if (hasMessageGap) {
                clearSentMessagesForRoom(roomId)
            }

            upsertMessages(remoteMessages)
        }

        val newestMessage = getNewestMessageForRoom(roomId)

        updateRoomLastActivity(
            roomId = roomId,
            messageId = newestMessage?.id,
            timestamp = newestMessage?.timestamp
        )
    }

    @Transaction
    suspend fun upsertIncomingOrAckedMessage(message: ChatMessageEntity) {
        upsertMessage(message)
        updateRoomLastActivity(
            roomId = message.roomId,
            messageId = message.id,
            timestamp = message.timestamp
        )
    }

    @Transaction
    suspend fun insertFullChat(
        room: ChatRoomEntity,
        participants: List<ChatParticipantEntity>,
        lastMessage: ChatMessageEntity?
    ) {
        upsertParticipants(participants)
        upsertRoom(room)
        lastMessage?.let { upsertMessage(it) }

        val crossRefs = participants.distinctBy { it.userId }.map { participant ->
            ChatRoomParticipantCrossRef(roomId = room.id, userId = participant.userId)
        }

        upsertCrossRefs(crossRefs)
    }

    @Transaction
    suspend fun insertFullChats(
        rooms: List<ChatRoomEntity>,
        participants: List<ChatParticipantEntity>,
        lastMessages: List<ChatMessageEntity>,
        crossRefs: List<ChatRoomParticipantCrossRef>
    ) {
        if (rooms.isEmpty()) return

        upsertParticipants(participants)
        upsertRooms(rooms)
        upsertMessages(lastMessages)

        val roomIds = rooms.map { it.id }
        val existingCrossRefs = roomIds.chunked(500).flatMap { chunk ->
            getCrossRefsForRooms(chunk)
        }
        val newCrossRefSet = crossRefs.toSet()

        val staleCrossRefs = existingCrossRefs.filter { it !in newCrossRefSet }
        if (staleCrossRefs.isNotEmpty()) {
            staleCrossRefs.chunked(500).forEach { chunk ->
                deleteCrossRefs(chunk)
            }
        }

        crossRefs.chunked(500).forEach { chunk ->
            upsertCrossRefs(chunk)
        }
    }

    @Transaction
    suspend fun handleParticipantJoined(
        roomId: String,
        newUsers: List<ChatParticipantEntity>,
        addedBy: ChatParticipantEntity? = null
    ) {
        addedBy?.let { upsertParticipant(it) }
        if (newUsers.isNotEmpty()) {
            upsertParticipants(newUsers)
            val crossRefs = newUsers.map { participant ->
                ChatRoomParticipantCrossRef(roomId = roomId, userId = participant.userId)
            }
            upsertCrossRefs(crossRefs)
        }
    }

    @Transaction
    suspend fun handleParticipantLeft(
        roomId: String,
        leftUser: ChatParticipantEntity,
        kickedBy: ChatParticipantEntity? = null
    ) {
        kickedBy?.let { upsertParticipant(it) }
        deleteParticipantCrossRef(roomId = roomId, userId = leftUser.userId)
    }
}
