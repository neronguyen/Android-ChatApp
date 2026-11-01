package io.github.neronguyen.chat.feature.chat.presentation.account

import arrow.core.Either
import io.github.neronguyen.chat.core.data.realtime.RealtimeConnectionManager
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.RealtimeConnectionState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.model.error.DataError
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
    private lateinit var fakeRealtimeConnectionManager: FakeRealtimeConnectionManager
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
        fakeRealtimeConnectionManager = FakeRealtimeConnectionManager(
            initialState = RealtimeConnectionState.CONNECTED,
        )
        viewModel = AccountViewModel(
            authRepository = fakeAuthRepository,
            realtimeConnectionManager = fakeRealtimeConnectionManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init maps user details, initials, email verification, and realtime connection state`() =
        runTest {
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Alex Lin", state.displayName)
            assertEquals("alex.lin@example.com", state.email)
            assertEquals("AL", state.initials)
            assertTrue(state.isEmailVerified)
            assertEquals(RealtimeConnectionState.CONNECTED, state.connectionState)
            assertFalse(state.isSigningOut)
            assertFalse(state.showSignOutConfirmation)
            assertNull(state.signOutError)
        }

    @Test
    fun `realtime connection state changes update uiState`() = runTest {
        testScheduler.advanceUntilIdle()
        assertEquals(RealtimeConnectionState.CONNECTED, viewModel.uiState.value.connectionState)

        fakeRealtimeConnectionManager.connectionStateFlow.value =
            RealtimeConnectionState.DISCONNECTED
        testScheduler.advanceUntilIdle()

        assertEquals(RealtimeConnectionState.DISCONNECTED, viewModel.uiState.value.connectionState)
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

    private class FakeRealtimeConnectionManager(
        initialState: RealtimeConnectionState = RealtimeConnectionState.CONNECTED
    ) : RealtimeConnectionManager {
        val connectionStateFlow = MutableStateFlow(initialState)
        override val connectionState: Flow<RealtimeConnectionState> = connectionStateFlow
        override fun initialize() {}
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
