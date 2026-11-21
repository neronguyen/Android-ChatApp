package io.github.neronguyen.chat.feature.auth.presentation.login

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
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = LoginViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when email and password changed, states are updated`() {
        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")

        assertEquals("test@example.com", viewModel.emailState.text.toString())
        assertEquals("password123", viewModel.passwordState.text.toString())
    }

    @Test
    fun `when empty email or password submitted, validation error is shown`() {
        viewModel.onEvent(LoginUiEvent.Login)

        val state = viewModel.uiState.value
        assertEquals(AuthValidationError.EmptyEmail, state.validationError)
        assertFalse(state.isLoading)
        assertEquals(0, fakeRepository.loginCalls.size)
    }

    @Test
    fun `when invalid email submitted, InvalidEmail validation error is shown`() {
        viewModel.emailState.setTextAndPlaceCursorAtEnd("invalid-email")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")
        viewModel.onEvent(LoginUiEvent.Login)

        val state = viewModel.uiState.value
        assertEquals(AuthValidationError.InvalidEmail, state.validationError)
        assertFalse(state.isLoading)
        assertEquals(0, fakeRepository.loginCalls.size)
    }

    @Test
    fun `when login succeeds, isSuccess is true`() = runTest {
        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")
        viewModel.onEvent(LoginUiEvent.Login)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSuccess)
        assertFalse(state.isLoading)
    }

    @Test
    fun `when onLoginSuccessHandled called, isSuccess is reset to false and other fields are preserved`() =
        runTest {
            viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
            viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")
            viewModel.onEvent(LoginUiEvent.Login)

            testDispatcher.scheduler.advanceUntilIdle()

            val successState = viewModel.uiState.value
            assertTrue(successState.isSuccess)

            viewModel.onLoginSuccessHandled()

            val handledState = viewModel.uiState.value
            assertFalse(handledState.isSuccess)
            assertEquals(successState.isLoading, handledState.isLoading)
            assertEquals(successState.validationError, handledState.validationError)
            assertEquals(successState.authError, handledState.authError)

            viewModel.onLoginSuccessHandled()
            val rehandledState = viewModel.uiState.value
            assertFalse(rehandledState.isSuccess)
            assertEquals(handledState, rehandledState)
        }

    @Test
    fun `two immediate submissions start only one login request`() = runTest {
        val completable = CompletableDeferred<Either<AuthError.Login, User>>()
        fakeRepository.loginHandler = { _, _ -> completable.await() }

        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")

        viewModel.onEvent(LoginUiEvent.Login)
        viewModel.onEvent(LoginUiEvent.Login)

        testScheduler.runCurrent()

        assertEquals(1, fakeRepository.loginCalls.size)
        assertTrue(viewModel.uiState.value.isLoading)

        completable.complete(Either.Right(User("1", "test@example.com", "Test User", true)))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `submission during active request starts no additional request`() = runTest {
        val completable = CompletableDeferred<Either<AuthError.Login, User>>()
        fakeRepository.loginHandler = { _, _ -> completable.await() }

        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")

        viewModel.onEvent(LoginUiEvent.Login)
        testScheduler.runCurrent()
        assertEquals(1, fakeRepository.loginCalls.size)
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.onEvent(LoginUiEvent.Login)
        testScheduler.runCurrent()
        assertEquals(1, fakeRepository.loginCalls.size)

        completable.complete(Either.Right(User("1", "test@example.com", "Test User", true)))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `after failed request, new submission can start another request`() = runTest {
        var completable = CompletableDeferred<Either<AuthError.Login, User>>()
        fakeRepository.loginHandler = { _, _ -> completable.await() }

        viewModel.emailState.setTextAndPlaceCursorAtEnd("test@example.com")
        viewModel.passwordState.setTextAndPlaceCursorAtEnd("password123")

        viewModel.onEvent(LoginUiEvent.Login)
        testScheduler.runCurrent()
        assertEquals(1, fakeRepository.loginCalls.size)

        completable.complete(Either.Left(AuthError.InvalidCredentials))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(AuthError.InvalidCredentials, viewModel.uiState.value.authError)

        completable = CompletableDeferred()

        viewModel.onEvent(LoginUiEvent.Login)
        testScheduler.runCurrent()
        assertEquals(2, fakeRepository.loginCalls.size)
        assertTrue(viewModel.uiState.value.isLoading)

        completable.complete(Either.Right(User("1", "test@example.com", "Test User", true)))
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isSuccess)
    }
}
