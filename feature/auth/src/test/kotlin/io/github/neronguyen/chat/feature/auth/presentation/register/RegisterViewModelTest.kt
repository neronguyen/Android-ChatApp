package io.github.neronguyen.chat.feature.auth.presentation.register

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import arrow.core.Either
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidationError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: RegisterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = RegisterViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when fields changed, states are updated`() {
        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.displayNameState.setTextAndPlaceCursorAtEnd("Test User")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")

        assertEquals("test@example.com", viewModel.emailState.text.toString())
        assertEquals("Test User", viewModel.displayNameState.text.toString())
        assertEquals("password123", viewModel.passwordState.text.toString())
    }

    @Test
    fun `when register succeeds, isSuccess is true`() = runTest {
        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.displayNameState.setTextAndPlaceCursorAtEnd("Test User")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")
        viewModel.onEvent(RegisterUiEvent.Register)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSuccess)
        assertFalse(state.isLoading)
    }

    @Test
    fun `when onRegisterSuccessHandled called, isSuccess is reset to false and other fields are preserved`() =
        runTest {
            viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
            viewModel.displayNameState.setTextAndPlaceCursorAtEnd("Test User")
            viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")
            viewModel.onEvent(RegisterUiEvent.Register)

            testDispatcher.scheduler.advanceUntilIdle()

            val successState = viewModel.uiState.value
            assertTrue(successState.isSuccess)

            viewModel.onRegisterSuccessHandled()

            val handledState = viewModel.uiState.value
            assertFalse(handledState.isSuccess)
            assertEquals(successState.isLoading, handledState.isLoading)
            assertEquals(successState.validationError, handledState.validationError)
            assertEquals(successState.authError, handledState.authError)

            viewModel.onRegisterSuccessHandled()
            val rehandledState = viewModel.uiState.value
            assertFalse(rehandledState.isSuccess)
            assertEquals(handledState, rehandledState)
        }

    @Test
    fun `two immediate submissions start only one register request`() = runTest {
        val completable = CompletableDeferred<Either<AuthError.Register, User>>()
        fakeRepository.registerCompletable = completable

        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.displayNameState.setTextAndPlaceCursorAtEnd("Test User")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")

        viewModel.onEvent(RegisterUiEvent.Register)
        viewModel.onEvent(RegisterUiEvent.Register)

        testScheduler.runCurrent()

        assertEquals(1, fakeRepository.registerCallCount)
        assertTrue(viewModel.uiState.value.isLoading)

        completable.complete(Either.Right(User("1", "test@example.com", "Test User", false)))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `submission during active request starts no additional request`() = runTest {
        val completable = CompletableDeferred<Either<AuthError.Register, User>>()
        fakeRepository.registerCompletable = completable

        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.displayNameState.setTextAndPlaceCursorAtEnd("Test User")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")

        viewModel.onEvent(RegisterUiEvent.Register)
        testScheduler.runCurrent()
        assertEquals(1, fakeRepository.registerCallCount)
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.onEvent(RegisterUiEvent.Register)
        testScheduler.runCurrent()
        assertEquals(1, fakeRepository.registerCallCount)

        completable.complete(Either.Right(User("1", "test@example.com", "Test User", false)))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `invalid input starts no request and leaves isLoading false`() = runTest {
        viewModel.emailState.setTextAndPlaceCursorAtEnd("")
        viewModel.displayNameState.setTextAndPlaceCursorAtEnd("Test User")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")

        viewModel.onEvent(RegisterUiEvent.Register)

        assertEquals(0, fakeRepository.registerCallCount)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(AuthValidationError.EmptyEmail, viewModel.uiState.value.validationError)
    }

    @Test
    fun `short or non-alphanumeric password sets InvalidPassword validation error`() = runTest {
        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.displayNameState.setTextAndPlaceCursorAtEnd("Test User")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("short")

        viewModel.onEvent(RegisterUiEvent.Register)

        assertEquals(0, fakeRepository.registerCallCount)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(AuthValidationError.InvalidPassword, viewModel.uiState.value.validationError)
    }

    @Test
    fun `after failed request, new submission can start another request`() = runTest {
        var completable = CompletableDeferred<Either<AuthError.Register, User>>()
        fakeRepository.registerCompletable = completable

        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.displayNameState.setTextAndPlaceCursorAtEnd("Test User")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")

        viewModel.onEvent(RegisterUiEvent.Register)
        testScheduler.runCurrent()
        assertEquals(1, fakeRepository.registerCallCount)

        completable.complete(Either.Left(AuthError.EmailAlreadyInUse))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(AuthError.EmailAlreadyInUse, viewModel.uiState.value.authError)

        completable = CompletableDeferred()
        fakeRepository.registerCompletable = completable

        viewModel.onEvent(RegisterUiEvent.Register)
        testScheduler.runCurrent()
        assertEquals(2, fakeRepository.registerCallCount)
        assertTrue(viewModel.uiState.value.isLoading)

        completable.complete(Either.Right(User("1", "test@example.com", "Test User", false)))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isSuccess)
    }
}

private class FakeAuthRepository : AuthRepository {
    var registerCallCount = 0
    var registerCompletable: CompletableDeferred<Either<AuthError.Register, User>>? = null

    override fun getAuthState(): Flow<AuthState> = flowOf(AuthState.Unauthenticated)

    override suspend fun login(
        email: String,
        password: String,
    ): Either<AuthError.Login, User> {
        val user = User("1", email, "Test User", true)
        return Either.Right(user)
    }

    override suspend fun register(
        email: String,
        displayName: String,
        password: String,
    ): Either<AuthError.Register, User> {
        registerCallCount++
        val completable = registerCompletable
        return if (completable != null) {
            completable.await()
        } else {
            Either.Right(User("1", email, displayName, false))
        }
    }

    override suspend fun logout(): Either<DataError, Unit> = Either.Right(Unit)
}
