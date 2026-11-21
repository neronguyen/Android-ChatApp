package io.github.neronguyen.chat.feature.auth.presentation.forgot

import arrow.core.Either
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.model.error.DataError
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: ForgotPasswordViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = ForgotPasswordViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty email and default values`() {
        val state = viewModel.uiState.value
        assertEquals("", state.email)
        assertFalse(state.isLoading)
        assertFalse(state.isSuccess)
        assertNull(state.validationError)
        assertNull(state.authError)
        assertNull(state.submittedEmail)
    }

    @Test
    fun `when email changed, state updates and errors cleared`() {
        viewModel.onEvent(ForgotPasswordUiEvent.EmailChanged("test@example.com"))
        assertEquals("test@example.com", viewModel.uiState.value.email)
        assertNull(viewModel.uiState.value.validationError)
        assertNull(viewModel.uiState.value.authError)
    }

    @Test
    fun `empty email triggers EmptyEmail validation error without repository call`() = runTest {
        viewModel.onEvent(ForgotPasswordUiEvent.EmailChanged(""))
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeRepository.forgotPasswordCalls.size)
        assertEquals(AuthValidationError.EmptyEmail, viewModel.uiState.value.validationError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `invalid email format triggers InvalidEmail validation error without repository call`() = runTest {
        viewModel.onEvent(ForgotPasswordUiEvent.EmailChanged("not-an-email"))
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeRepository.forgotPasswordCalls.size)
        assertEquals(AuthValidationError.InvalidEmail, viewModel.uiState.value.validationError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `valid email submits request, normalizes email, and marks success`() = runTest {
        viewModel.onEvent(ForgotPasswordUiEvent.EmailChanged(" Alice@example.com "))
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("alice@example.com"), fakeRepository.forgotPasswordCalls)
        val state = viewModel.uiState.value
        assertTrue(state.isSuccess)
        assertEquals("alice@example.com", state.submittedEmail)
        assertFalse(state.isLoading)
        assertNull(state.validationError)
        assertNull(state.authError)
    }

    @Test
    fun `when onForgotPasswordSuccessHandled called, isSuccess is reset to false`() = runTest {
        viewModel.onEvent(ForgotPasswordUiEvent.EmailChanged("alice@example.com"))
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSuccess)

        viewModel.onForgotPasswordSuccessHandled()
        assertFalse(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `when repository returns error, authError is updated`() = runTest {
        fakeRepository.forgotPasswordHandler = { Either.Left(DataError.Remote.Unavailable) }
        viewModel.onEvent(ForgotPasswordUiEvent.EmailChanged("alice@example.com"))
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.forgotPasswordCalls.size)
        val state = viewModel.uiState.value
        assertFalse(state.isSuccess)
        assertFalse(state.isLoading)
        assertEquals(AuthError.Data(DataError.Remote.Unavailable), state.authError)
    }

    @Test
    fun `two immediate submissions start only one request`() = runTest {
        val completable = CompletableDeferred<Either<DataError, Unit>>()
        fakeRepository.forgotPasswordHandler = { completable.await() }

        viewModel.onEvent(ForgotPasswordUiEvent.EmailChanged("alice@example.com"))
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)

        testDispatcher.scheduler.runCurrent()
        assertEquals(1, fakeRepository.forgotPasswordCalls.size)
        assertTrue(viewModel.uiState.value.isLoading)

        completable.complete(Either.Right(Unit))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isSuccess)
    }
}
