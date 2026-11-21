package io.github.neronguyen.chat.feature.auth.presentation.register

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import arrow.core.Either
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.testing.FakeAuthRepository
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidationError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        fakeRepository.registerHandler = { _, _, _ -> completable.await() }

        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.displayNameState.setTextAndPlaceCursorAtEnd("Test User")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")

        viewModel.onEvent(RegisterUiEvent.Register)
        viewModel.onEvent(RegisterUiEvent.Register)

        testScheduler.runCurrent()

        assertEquals(1, fakeRepository.registerCalls.size)
        assertTrue(viewModel.uiState.value.isLoading)

        completable.complete(Either.Right(User("1", "test@example.com", "Test User", false)))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `submission during active request starts no additional request`() = runTest {
        val completable = CompletableDeferred<Either<AuthError.Register, User>>()
        fakeRepository.registerHandler = { _, _, _ -> completable.await() }

        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.displayNameState.setTextAndPlaceCursorAtEnd("Test User")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")

        viewModel.onEvent(RegisterUiEvent.Register)
        testScheduler.runCurrent()
        assertEquals(1, fakeRepository.registerCalls.size)
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.onEvent(RegisterUiEvent.Register)
        testScheduler.runCurrent()
        assertEquals(1, fakeRepository.registerCalls.size)

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

        assertEquals(0, fakeRepository.registerCalls.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(AuthValidationError.EmptyEmail, viewModel.uiState.value.validationError)
    }

    @Test
    fun `short or non-alphanumeric password sets InvalidPassword validation error`() = runTest {
        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.displayNameState.setTextAndPlaceCursorAtEnd("Test User")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("short")

        viewModel.onEvent(RegisterUiEvent.Register)

        assertEquals(0, fakeRepository.registerCalls.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(AuthValidationError.InvalidPassword, viewModel.uiState.value.validationError)
    }

    @Test
    fun `after failed request, new submission can start another request`() = runTest {
        var completable = CompletableDeferred<Either<AuthError.Register, User>>()
        fakeRepository.registerHandler = { _, _, _ -> completable.await() }

        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.displayNameState.setTextAndPlaceCursorAtEnd("Test User")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")

        viewModel.onEvent(RegisterUiEvent.Register)
        testScheduler.runCurrent()
        assertEquals(1, fakeRepository.registerCalls.size)

        completable.complete(Either.Left(AuthError.EmailAlreadyInUse))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(AuthError.EmailAlreadyInUse, viewModel.uiState.value.authError)

        completable = CompletableDeferred()

        viewModel.onEvent(RegisterUiEvent.Register)
        testScheduler.runCurrent()
        assertEquals(2, fakeRepository.registerCalls.size)
        assertTrue(viewModel.uiState.value.isLoading)

        completable.complete(Either.Right(User("1", "test@example.com", "Test User", false)))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isSuccess)
    }
}
