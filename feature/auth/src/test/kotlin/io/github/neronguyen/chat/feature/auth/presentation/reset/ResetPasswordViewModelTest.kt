package io.github.neronguyen.chat.feature.auth.presentation.reset

import androidx.lifecycle.SavedStateHandle
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
class ResetPasswordViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: ResetPasswordViewModel
    private val testEmail = "bob@example.com"
    private var baseEpoch = 1_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        savedStateHandle = SavedStateHandle()
        baseEpoch = 1_000_000L
        viewModel = ResetPasswordViewModel(testEmail, fakeRepository, savedStateHandle).apply {
            timeProvider = { baseEpoch + testDispatcher.scheduler.currentTime }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects email and default values`() {
        val state = viewModel.uiState.value
        assertEquals(testEmail, state.email)
        assertEquals("", state.code)
        assertEquals("", state.newPassword)
        assertEquals("", state.confirmPassword)
        assertFalse(state.isLoading)
        assertFalse(state.isSuccess)
        assertFalse(state.passwordsMismatch)
        assertNull(state.validationError)
        assertNull(state.authError)
    }

    @Test
    fun `when code and passwords changed, state and savedStateHandle update`() {
        viewModel.onEvent(ResetPasswordUiEvent.CodeChanged("123456"))
        viewModel.onEvent(ResetPasswordUiEvent.NewPasswordChanged("Secret1234!"))
        viewModel.onEvent(ResetPasswordUiEvent.ConfirmPasswordChanged("Secret1234!"))

        val state = viewModel.uiState.value
        assertEquals("123456", state.code)
        assertEquals("Secret1234!", state.newPassword)
        assertEquals("Secret1234!", state.confirmPassword)
        assertEquals("123456", savedStateHandle.get<String>("otp_code"))
    }

    @Test
    fun `passwords mismatch sets passwordsMismatch flag and stops submission`() = runTest {
        viewModel.onEvent(ResetPasswordUiEvent.CodeChanged("123456"))
        viewModel.onEvent(ResetPasswordUiEvent.NewPasswordChanged("Secret1234!"))
        viewModel.onEvent(ResetPasswordUiEvent.ConfirmPasswordChanged("Different1234!"))
        viewModel.onEvent(ResetPasswordUiEvent.Reset)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeRepository.resetPasswordCallCount)
        assertTrue(viewModel.uiState.value.passwordsMismatch)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `invalid new password sets InvalidNewPassword validation error`() = runTest {
        viewModel.onEvent(ResetPasswordUiEvent.CodeChanged("123456"))
        viewModel.onEvent(ResetPasswordUiEvent.NewPasswordChanged("short"))
        viewModel.onEvent(ResetPasswordUiEvent.ConfirmPasswordChanged("short"))
        viewModel.onEvent(ResetPasswordUiEvent.Reset)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeRepository.resetPasswordCallCount)
        assertEquals(AuthValidationError.InvalidNewPassword, viewModel.uiState.value.validationError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `valid inputs execute reset and mark isSuccess`() = runTest {
        viewModel.onEvent(ResetPasswordUiEvent.CodeChanged("123456"))
        viewModel.onEvent(ResetPasswordUiEvent.NewPasswordChanged("Secret1234!"))
        viewModel.onEvent(ResetPasswordUiEvent.ConfirmPasswordChanged("Secret1234!"))
        viewModel.onEvent(ResetPasswordUiEvent.Reset)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.resetPasswordCallCount)
        assertEquals(testEmail, fakeRepository.lastResetEmail)
        assertEquals("123456", fakeRepository.lastResetCode)
        assertEquals("Secret1234!", fakeRepository.lastResetPassword)
        assertTrue(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `when reset fails with SamePassword error, authError is updated`() = runTest {
        fakeRepository.resetPasswordResult = Either.Left(AuthError.SamePassword)
        viewModel.onEvent(ResetPasswordUiEvent.CodeChanged("123456"))
        viewModel.onEvent(ResetPasswordUiEvent.NewPasswordChanged("Secret1234!"))
        viewModel.onEvent(ResetPasswordUiEvent.ConfirmPasswordChanged("Secret1234!"))
        viewModel.onEvent(ResetPasswordUiEvent.Reset)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.resetPasswordCallCount)
        val state = viewModel.uiState.value
        assertFalse(state.isSuccess)
        assertEquals(AuthError.SamePassword, state.authError)
    }

    @Test
    fun `when reset fails with InvalidCode error, authError is updated`() = runTest {
        fakeRepository.resetPasswordResult = Either.Left(AuthError.InvalidCode)
        viewModel.onEvent(ResetPasswordUiEvent.CodeChanged("123456"))
        viewModel.onEvent(ResetPasswordUiEvent.NewPasswordChanged("Secret1234!"))
        viewModel.onEvent(ResetPasswordUiEvent.ConfirmPasswordChanged("Secret1234!"))
        viewModel.onEvent(ResetPasswordUiEvent.Reset)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AuthError.InvalidCode, viewModel.uiState.value.authError)
    }

    @Test
    fun `cooldown timer uses SavedStateHandle target epoch to survive recreation`() = runTest {
        viewModel.startCooldown(60)
        assertEquals(60, viewModel.uiState.value.resendCooldownSeconds)

        testDispatcher.scheduler.advanceTimeBy(25_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(35, viewModel.uiState.value.resendCooldownSeconds)

        val recreated = ResetPasswordViewModel(testEmail, fakeRepository, savedStateHandle).apply {
            timeProvider = { baseEpoch + testDispatcher.scheduler.currentTime }
        }

        assertEquals(35, recreated.uiState.value.resendCooldownSeconds)
        testDispatcher.scheduler.advanceUntilIdle()
    }
}

private class FakeAuthRepository : AuthRepository {
    var resetPasswordCallCount = 0
    var lastResetEmail: String? = null
    var lastResetCode: String? = null
    var lastResetPassword: String? = null
    var resetPasswordResult: Either<AuthError.ResetPassword, Unit> = Either.Right(Unit)
    var resetPasswordCompletable: CompletableDeferred<Either<AuthError.ResetPassword, Unit>>? = null

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

    override suspend fun forgotPassword(email: String): Either<DataError, Unit> = Either.Right(Unit)

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Either<AuthError.ResetPassword, Unit> {
        resetPasswordCallCount++
        lastResetEmail = email
        lastResetCode = code
        lastResetPassword = newPassword
        val completable = resetPasswordCompletable
        return completable?.await() ?: resetPasswordResult
    }

    override suspend fun logout(): Either<DataError, Unit> = Either.Right(Unit)
}
