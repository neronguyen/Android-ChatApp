package io.github.neronguyen.chat.feature.chat.presentation.account

import arrow.core.Either
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.feature.chat.presentation.FakeChatSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var fakeChatSyncManager: FakeChatSyncManager
    private lateinit var viewModel: AccountViewModel

    private val testUser = User(
        id = "user-123",
        email = "alex.lin@example.com",
        displayName = "Alex Lin",
        isEmailVerified = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuthRepository = FakeAuthRepository(
            initialAuthState = AuthState.Authenticated(testUser, "access-token"),
        )
        fakeChatSyncManager = FakeChatSyncManager(
            initialState = ChatSyncState.Synced,
        )
        viewModel = AccountViewModel(
            authRepository = fakeAuthRepository,
            chatSyncManager = fakeChatSyncManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init maps user details, initials, email verification, and sync state`() =
        runTest {
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Alex Lin", state.displayName)
            assertEquals("alex.lin@example.com", state.email)
            assertEquals("AL", state.initials)
            assertTrue(state.isEmailVerified)
            assertEquals(ChatSyncState.Synced, state.syncState)
            assertFalse(state.isSigningOut)
            assertFalse(state.showSignOutConfirmation)
            assertNull(state.signOutError)
        }

    @Test
    fun `sync state changes update uiState`() = runTest {
        testScheduler.advanceUntilIdle()
        assertEquals(ChatSyncState.Synced, viewModel.uiState.value.syncState)

        fakeChatSyncManager.syncStateFlow.value = ChatSyncState.Offline
        testScheduler.advanceUntilIdle()

        assertEquals(ChatSyncState.Offline, viewModel.uiState.value.syncState)
    }

    @Test
    fun `RequestSignOut and DismissSignOutConfirmation toggle showSignOutConfirmation`() = runTest {
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showSignOutConfirmation)

        viewModel.onEvent(AccountUiEvent.RequestSignOut)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showSignOutConfirmation)

        viewModel.onEvent(AccountUiEvent.DismissSignOutConfirmation)
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showSignOutConfirmation)
    }

    @Test
    fun `ConfirmSignOut calls logout and finishes successfully`() = runTest {
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(AccountUiEvent.RequestSignOut)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showSignOutConfirmation)

        viewModel.onEvent(AccountUiEvent.ConfirmSignOut)
        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeAuthRepository.logoutCallCount)
        assertFalse(viewModel.uiState.value.isSigningOut)
        assertFalse(viewModel.uiState.value.showSignOutConfirmation)
        assertNull(viewModel.uiState.value.signOutError)
    }

    @Test
    fun `ConfirmSignOut handles error and updates signOutError`() = runTest {
        fakeAuthRepository.logoutResult = Either.Left(DataError.Connectivity.NoInternet)
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(AccountUiEvent.ConfirmSignOut)
        testScheduler.advanceUntilIdle()

        assertEquals(1, fakeAuthRepository.logoutCallCount)
        assertFalse(viewModel.uiState.value.isSigningOut)
        assertEquals(DataError.Connectivity.NoInternet, viewModel.uiState.value.signOutError)

        viewModel.onEvent(AccountUiEvent.DismissError)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.signOutError)
    }

    @Test
    fun `initials derive from email when displayName is blank`() = runTest {
        val blankNameUser = testUser.copy(displayName = "")
        fakeAuthRepository.authStateFlow.value = AuthState.Authenticated(blankNameUser, "token")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.displayName)
        assertEquals("alex.lin@example.com", state.email)
        assertEquals("AL", state.initials)
    }

    private class FakeAuthRepository(
        initialAuthState: AuthState = AuthState.Unauthenticated,
    ) : AuthRepository {
        val authStateFlow = MutableStateFlow(initialAuthState)
        var logoutCallCount = 0
        var logoutResult: Either<DataError, Unit> = Either.Right(Unit)

        override fun getAuthState(): Flow<AuthState> = authStateFlow

        override suspend fun login(
            email: String,
            password: String
        ): Either<AuthError.Login, User> = Either.Right(
            User("user-1", email, "User", true)
        )

        override suspend fun register(
            email: String,
            displayName: String,
            password: String
        ): Either<AuthError.Register, User> = Either.Right(
            User("user-1", email, displayName, false)
        )

        override suspend fun logout(): Either<DataError, Unit> {
            logoutCallCount++
            if (logoutResult.isRight()) {
                authStateFlow.value = AuthState.Unauthenticated
            }
            return logoutResult
        }
    }
}
