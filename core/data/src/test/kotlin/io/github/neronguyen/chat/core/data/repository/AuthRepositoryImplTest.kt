package io.github.neronguyen.chat.core.data.repository

import arrow.core.Either
import io.github.neronguyen.chat.core.database.dao.ChatDao
import io.github.neronguyen.chat.core.database.entity.ChatMessageEntity
import io.github.neronguyen.chat.core.database.entity.ChatParticipantEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomEntity
import io.github.neronguyen.chat.core.database.entity.ChatRoomParticipantCrossRef
import io.github.neronguyen.chat.core.database.model.ChatRoomWithDetails
import io.github.neronguyen.chat.core.datastore.TokenDataSource
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.MessageDeliveryStatus
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.network.AuthNetworkDataSource
import io.github.neronguyen.chat.core.network.util.NetworkError
import io.github.neronguyenvn.nerochat.contract.auth.error.AuthServerError
import io.github.neronguyenvn.nerochat.contract.auth.AuthenticatedUserDto
import io.github.neronguyenvn.nerochat.contract.auth.LoginRequest
import io.github.neronguyenvn.nerochat.contract.auth.RefreshTokenRequest
import io.github.neronguyenvn.nerochat.contract.auth.RegisterRequest
import io.github.neronguyenvn.nerochat.contract.auth.UserDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class AuthRepositoryImplTest {

    private class FakeChatDao : ChatDao {
        var clearAllChatDataCalled = false

        override fun getChatsWithDetails(): Flow<List<ChatRoomWithDetails>> = emptyFlow()
        override fun getChatWithDetails(roomId: String): Flow<ChatRoomWithDetails?> = emptyFlow()
        override fun getRecentMessages(roomId: String, limit: Int): Flow<List<ChatMessageEntity>> =
            emptyFlow()

        override suspend fun getMessageById(id: String): ChatMessageEntity? = null
        override suspend fun getOldestSentMessageTimestamp(roomId: String): Long? = null
        override suspend fun getNewestSentMessageTimestamp(roomId: String): Long? = null
        override suspend fun getNewestMessageForRoom(roomId: String): ChatMessageEntity? = null
        override suspend fun getCrossRefsForRooms(roomIds: List<String>): List<ChatRoomParticipantCrossRef> =
            emptyList()

        override suspend fun upsertRoom(room: ChatRoomEntity) {}
        override suspend fun upsertRooms(rooms: List<ChatRoomEntity>) {}
        override suspend fun upsertParticipant(participant: ChatParticipantEntity) {}
        override suspend fun upsertParticipants(participants: List<ChatParticipantEntity>) {}
        override suspend fun upsertCrossRefs(crossRefs: List<ChatRoomParticipantCrossRef>) {}
        override suspend fun upsertMessage(message: ChatMessageEntity) {}
        override suspend fun upsertMessages(messages: List<ChatMessageEntity>) {}
        override suspend fun updateDeliveryStatus(
            messageId: String,
            status: MessageDeliveryStatus
        ) {
        }

        override suspend fun markSendingMessagesAsFailed(
            sendingStatus: MessageDeliveryStatus,
            failedStatus: MessageDeliveryStatus
        ) {
        }

        override suspend fun deleteMessage(messageId: String) {}
        override suspend fun clearSentMessagesForRoom(roomId: String) {}
        override suspend fun deleteParticipantCrossRef(roomId: String, userId: String) {}
        override suspend fun updateRoomLastActivity(
            roomId: String,
            messageId: String?,
            timestamp: Long?
        ) {
        }

        override suspend fun deleteCrossRefs(crossRefs: List<ChatRoomParticipantCrossRef>) {}
        override suspend fun deleteRoom(roomId: String) {}
        override suspend fun deleteCrossRefsForRoom(roomId: String) {}
        override suspend fun deleteMessagesForRoom(roomId: String) {}
        override suspend fun clearRooms() {}
        override suspend fun clearMessages() {}
        override suspend fun clearParticipants() {}
        override suspend fun clearCrossRefs() {}

        override suspend fun clearAllChatData() {
            clearAllChatDataCalled = true
        }
    }

    private fun createRepository(
        networkDataSource: AuthNetworkDataSource,
        tokenDataSource: TokenDataSource,
        chatDao: ChatDao = FakeChatDao()
    ): AuthRepositoryImpl = AuthRepositoryImpl(networkDataSource, tokenDataSource, chatDao)



    private class FakeTokenDataSource : TokenDataSource {
        var savedRefreshToken: String? = null
        var savedAccessToken: String? = null
        var savedUser: User? = null
        var clearCalled = false

        override val authState: Flow<AuthState> = emptyFlow()

        override suspend fun getRefreshToken(): String? = savedRefreshToken
        override suspend fun getAccessToken(): String? = savedAccessToken

        override suspend fun saveAuthData(user: User, accessToken: String, refreshToken: String) {
            savedUser = user
            savedAccessToken = accessToken
            savedRefreshToken = refreshToken
        }

        override suspend fun saveTokens(accessToken: String, refreshToken: String) {
            savedAccessToken = accessToken
            savedRefreshToken = refreshToken
        }

        override suspend fun clearAuthData() {
            clearCalled = true
            savedRefreshToken = null
            savedAccessToken = null
            savedUser = null
        }
    }

    private class FakeAuthNetworkDataSource : AuthNetworkDataSource {
        val refreshRequests = mutableListOf<RefreshTokenRequest>()
        val logoutRequests = mutableListOf<RefreshTokenRequest>()
        var refreshResult: (RefreshTokenRequest) -> Either<NetworkError<AuthServerError.RefreshToken>, AuthenticatedUserDto> =
            { (reqToken) ->
                Either.Left(NetworkError.Transport.NoInternet)
            }
        var refreshDelayMs: Long = 0
        var logoutDelayMs: Long = 0

        override suspend fun login(request: LoginRequest): Either<NetworkError<AuthServerError.Login>, AuthenticatedUserDto> {
            return Either.Left(NetworkError.Transport.NoInternet)
        }

        override suspend fun register(request: RegisterRequest): Either<NetworkError<AuthServerError.Register>, UserDto> {
            return Either.Left(NetworkError.Transport.NoInternet)
        }

        override suspend fun refreshToken(request: RefreshTokenRequest): Either<NetworkError<AuthServerError.RefreshToken>, AuthenticatedUserDto> {
            synchronized(refreshRequests) {
                refreshRequests.add(request)
            }
            if (refreshDelayMs > 0) {
                delay(refreshDelayMs.milliseconds)
            }
            return refreshResult(request)
        }

        override suspend fun logout(request: RefreshTokenRequest): Either<NetworkError<AuthServerError.Logout>, Unit> {
            synchronized(logoutRequests) {
                logoutRequests.add(request)
            }
            if (logoutDelayMs > 0) {
                delay(logoutDelayMs.milliseconds)
            }
            return Either.Right(Unit)
        }
    }

    @Test
    fun refreshToken_noRefreshTokenStored_returnsError() = runTest {
        val tokenDataSource = FakeTokenDataSource()
        val networkDataSource = FakeAuthNetworkDataSource()
        val repository = createRepository(networkDataSource, tokenDataSource)

        val result = repository.refresh("stale_access_token")

        assertNull(result)
    }

    @Test
    fun refreshToken_staleTokenMatchesCurrent_performsRefreshAndSavesData() = runTest {
        val tokenDataSource = FakeTokenDataSource().apply {
            savedAccessToken = "old_access_token"
            savedRefreshToken = "old_refresh_token"
        }
        val networkDataSource = FakeAuthNetworkDataSource().apply {
            refreshResult = { (reqToken) ->
                Either.Right(
                    AuthenticatedUserDto(
                        user = UserDto(
                            id = "user_1",
                            email = "user@test.com",
                            displayName = "User One",
                            isEmailVerified = true,
                        ),
                        accessToken = "new_access_token",
                        refreshToken = "new_refresh_token",
                    ),
                )
            }
        }
        val repository = createRepository(networkDataSource, tokenDataSource)

        val result = repository.refresh("old_access_token")

        assertEquals("new_access_token", result)
        assertEquals("new_refresh_token", tokenDataSource.savedRefreshToken)
        assertEquals("new_access_token", tokenDataSource.savedAccessToken)
        assertEquals(1, networkDataSource.refreshRequests.size)
    }

    @Test
    fun refreshToken_staleTokenDiffersFromCurrentAccessToken_returnsCurrentAccessTokenWithoutNetworkCall() =
        runTest {
            val tokenDataSource = FakeTokenDataSource().apply {
                savedAccessToken = "already_refreshed_access_token"
                savedRefreshToken = "current_refresh_token"
            }
            val networkDataSource = FakeAuthNetworkDataSource()
            val repository = createRepository(networkDataSource, tokenDataSource)

            val result = repository.refresh("stale_access_token")

            assertEquals("already_refreshed_access_token", result)
            assertEquals(0, networkDataSource.refreshRequests.size)
        }

    @Test
    fun refreshToken_concurrentCallsWithSameStaleToken_secondCallerUsesUpdatedAccessToken() =
        runTest {
            val tokenDataSource = FakeTokenDataSource().apply {
                savedAccessToken = "stale_token"
                savedRefreshToken = "refresh_token_1"
            }

            val networkDataSource = FakeAuthNetworkDataSource().apply {
                refreshDelayMs = 50
                refreshResult = {
                    Either.Right(
                        AuthenticatedUserDto(
                            user = UserDto(
                                id = "user_1",
                                email = "user@test.com",
                                displayName = "User One",
                                isEmailVerified = true,
                            ),
                            accessToken = "new_access_token",
                            refreshToken = "refresh_token_2",
                        ),
                    )
                }
            }

            val repository = createRepository(networkDataSource, tokenDataSource)

            val call1 = async { repository.refresh("stale_token") }
            val call2 = async { repository.refresh("stale_token") }

            val results = awaitAll(call1, call2)

            assertEquals("new_access_token", results[0])
            assertEquals("new_access_token", results[1])

            assertEquals(1, networkDataSource.refreshRequests.size)
            assertEquals("refresh_token_2", tokenDataSource.savedRefreshToken)
        }

    @Test
    fun refreshToken_failure_returnsNull() = runTest {
        val tokenDataSource = FakeTokenDataSource().apply {
            savedAccessToken = "stale_token"
            savedRefreshToken = "invalid_refresh_token"
        }
        val networkDataSource = FakeAuthNetworkDataSource().apply {
            refreshResult = { Either.Left(NetworkError.Transport.Timeout) }
        }
        val repository = createRepository(networkDataSource, tokenDataSource)

        val result = repository.refresh("stale_token")

        assertNull(result)
        // Ensure network timeout does NOT evict the session
        assertEquals("invalid_refresh_token", tokenDataSource.savedRefreshToken)
    }

    @Test
    fun refreshToken_unauthorizedError_clearsAuthDataAndEvictsSession() = runTest {
        val tokenDataSource = FakeTokenDataSource().apply {
            savedAccessToken = "stale_token"
            savedRefreshToken = "invalid_refresh_token"
        }
        val networkDataSource = FakeAuthNetworkDataSource().apply {
            refreshResult = {
                Either.Left(
                    NetworkError.Business(
                        error = AuthServerError.InvalidToken,
                        message = "Refresh token expired or invalid"
                    )
                )
            }
        }
        val repository = createRepository(networkDataSource, tokenDataSource)

        val result = repository.refresh("stale_token")

        assertNull(result)
        assertTrue(tokenDataSource.clearCalled)
        assertNull(tokenDataSource.savedRefreshToken)
        assertNull(tokenDataSource.savedAccessToken)
    }

    @Test
    fun logout_withTokenStored_callsNetworkLogoutAndClearsAuthData() = runTest {
        val tokenDataSource = FakeTokenDataSource().apply {
            savedAccessToken = "access_token"
            savedRefreshToken = "refresh_token"
        }
        val networkDataSource = FakeAuthNetworkDataSource()
        val repository = createRepository(networkDataSource, tokenDataSource)

        val result = repository.logout()

        assertTrue(result.isRight())
        assertEquals(1, networkDataSource.logoutRequests.size)
        assertEquals("refresh_token", networkDataSource.logoutRequests[0].refreshToken)
        assertTrue(tokenDataSource.clearCalled)
        assertNull(tokenDataSource.savedAccessToken)
        assertNull(tokenDataSource.savedRefreshToken)
    }

    @Test
    fun logout_withoutTokenStored_clearsAuthDataWithoutNetworkLogout() = runTest {
        val tokenDataSource = FakeTokenDataSource()
        val networkDataSource = FakeAuthNetworkDataSource()
        val repository = createRepository(networkDataSource, tokenDataSource)

        val result = repository.logout()

        assertTrue(result.isRight())
        assertEquals(0, networkDataSource.logoutRequests.size)
        assertTrue(tokenDataSource.clearCalled)
    }

    @Test
    fun logout_concurrentWithRefreshToken_serializedWithMutex() = runTest {
        val tokenDataSource = FakeTokenDataSource().apply {
            savedAccessToken = "stale_token"
            savedRefreshToken = "valid_refresh_token"
        }
        val networkDataSource = FakeAuthNetworkDataSource().apply {
            logoutDelayMs = 50
        }
        val repository = createRepository(networkDataSource, tokenDataSource)

        // Launch logout and refresh token concurrently
        val logoutCall = async { repository.logout() }
        val refreshCall = async { repository.refresh("stale_token") }

        val logoutResult = logoutCall.await()
        val refreshResult = refreshCall.await()

        assertTrue(logoutResult.isRight())
        // Since logout acquired lock first and cleared data, refresh returns null
        assertNull(refreshResult)
        assertTrue(tokenDataSource.clearCalled)
    }

    @Test
    fun logout_clearsChatDaoCache() = runTest {
        val fakeDao = FakeChatDao()
        val tokenDataSource = FakeTokenDataSource()
        val networkDataSource = FakeAuthNetworkDataSource()
        val repository = createRepository(networkDataSource, tokenDataSource, fakeDao)

        val result = repository.logout()

        assertTrue(result.isRight())
        assertTrue(fakeDao.clearAllChatDataCalled)
    }
}

