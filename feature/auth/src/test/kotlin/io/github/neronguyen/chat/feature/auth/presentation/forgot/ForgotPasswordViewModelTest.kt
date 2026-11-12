package io.github.neronguyen.chat.feature.auth.presentation.forgot

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

        assertEquals(0, fakeRepository.forgotPasswordCallCount)
        assertEquals(AuthValidationError.EmptyEmail, viewModel.uiState.value.validationError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `invalid email format triggers InvalidEmail validation error without repository call`() = runTest {
        viewModel.onEvent(ForgotPasswordUiEvent.EmailChanged("not-an-email"))
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeRepository.forgotPasswordCallCount)
        assertEquals(AuthValidationError.InvalidEmail, viewModel.uiState.value.validationError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `valid email submits request, normalizes email, and marks success`() = runTest {
        viewModel.onEvent(ForgotPasswordUiEvent.EmailChanged(" Alice@example.com "))
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.forgotPasswordCallCount)
        assertEquals("alice@example.com", fakeRepository.lastForgotPasswordEmail)
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
        fakeRepository.forgotPasswordResult = Either.Left(DataError.Remote.Unavailable)
        viewModel.onEvent(ForgotPasswordUiEvent.EmailChanged("alice@example.com"))
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.forgotPasswordCallCount)
        val state = viewModel.uiState.value
        assertFalse(state.isSuccess)
        assertFalse(state.isLoading)
        assertEquals(AuthError.Data(DataError.Remote.Unavailable), state.authError)
    }

    @Test
    fun `two immediate submissions start only one request`() = runTest {
        val completable = CompletableDeferred<Either<DataError, Unit>>()
        fakeRepository.forgotPasswordCompletable = completable

        viewModel.onEvent(ForgotPasswordUiEvent.EmailChanged("alice@example.com"))
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)
        viewModel.onEvent(ForgotPasswordUiEvent.Submit)

        testDispatcher.scheduler.runCurrent()
        assertEquals(1, fakeRepository.forgotPasswordCallCount)
        assertTrue(viewModel.uiState.value.isLoading)

        completable.complete(Either.Right(Unit))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isSuccess)
    }
}

private class FakeAuthRepository : AuthRepository {
    var forgotPasswordCallCount = 0
    var lastForgotPasswordEmail: String? = null
    var forgotPasswordResult: Either<DataError, Unit> = Either.Right(Unit)
    var forgotPasswordCompletable: CompletableDeferred<Either<DataError, Unit>>? = null

    override fun getAuthState(): Flow<AuthState> = flowOf(AuthState.Unauthenticated)

    override suspend fun login(email: String, password: String): Either<AuthError.Login, User> =
        Either.Right(User("1", email, "Test User", true))

    override suspend fun register(
        email: String,
        displayName: String,
        password: String,
    ): Either<AuthError.Register, User> =
        Either.Right(User("1", email, displayName, false))

    override suspend fun verifyEmail(
        email: String,
        code: String,
    ): Either<AuthError.VerifyEmail, Unit> = Either.Right(Unit)

    override suspend fun resendVerification(email: String): Either<DataError, Unit> = Either.Right(Unit)

    override suspend fun forgotPassword(email: String): Either<DataError, Unit> {
        forgotPasswordCallCount++
        lastForgotPasswordEmail = email
        val completable = forgotPasswordCompletable
        return completable?.await() ?: forgotPasswordResult
    }

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Either<AuthError.ResetPassword, Unit> = Either.Right(Unit)

    override suspend fun logout(): Either<DataError, Unit> = Either.Right(Unit)
}
