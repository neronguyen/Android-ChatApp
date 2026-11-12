package io.github.neronguyen.chat.feature.auth.presentation.verify

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
class VerifyEmailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: VerifyEmailViewModel
    private val testEmail = "user@example.com"
    private var baseEpoch = 1_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        savedStateHandle = SavedStateHandle()
        baseEpoch = 1_000_000L
        viewModel = VerifyEmailViewModel(testEmail, fakeRepository, savedStateHandle).apply {
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
        assertFalse(state.isLoading)
        assertFalse(state.isSuccess)
        assertFalse(state.isResending)
        assertEquals(0, state.resendCooldownSeconds)
        assertNull(state.validationError)
        assertNull(state.authError)
        assertNull(state.infoMessage)
    }

    @Test
    fun `when code entered, uiState and savedStateHandle are updated`() {
        viewModel.onEvent(VerifyEmailUiEvent.CodeChanged("1234"))
        assertEquals("1234", viewModel.uiState.value.code)
        assertEquals("1234", savedStateHandle.get<String>("otp_code"))
    }

    @Test
    fun `entering 6th digit automatically triggers verify`() = runTest {
        viewModel.onEvent(VerifyEmailUiEvent.CodeChanged("123456"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.verifyCallCount)
        assertEquals(testEmail, fakeRepository.lastVerifyEmail)
        assertEquals("123456", fakeRepository.lastVerifyCode)
        assertTrue(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `empty code sets EmptyOtpCode validation error and makes no repository call`() = runTest {
        viewModel.onEvent(VerifyEmailUiEvent.CodeChanged(""))
        viewModel.onEvent(VerifyEmailUiEvent.Verify)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeRepository.verifyCallCount)
        assertEquals(AuthValidationError.EmptyOtpCode, viewModel.uiState.value.validationError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `non-6-digit code sets InvalidOtpLength validation error and makes no repository call`() = runTest {
        viewModel.onEvent(VerifyEmailUiEvent.CodeChanged("12345"))
        viewModel.onEvent(VerifyEmailUiEvent.Verify)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, fakeRepository.verifyCallCount)
        assertEquals(AuthValidationError.InvalidOtpLength, viewModel.uiState.value.validationError)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `when verify fails with InvalidCode error, authError is updated`() = runTest {
        fakeRepository.verifyResult = Either.Left(AuthError.InvalidCode)
        viewModel.onEvent(VerifyEmailUiEvent.CodeChanged("123456"))

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.verifyCallCount)
        val state = viewModel.uiState.value
        assertFalse(state.isSuccess)
        assertFalse(state.isLoading)
        assertEquals(AuthError.InvalidCode, state.authError)
    }

    @Test
    fun `when verify fails with CodeExpired error, authError is updated`() = runTest {
        fakeRepository.verifyResult = Either.Left(AuthError.CodeExpired)
        viewModel.onEvent(VerifyEmailUiEvent.CodeChanged("123456"))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSuccess)
        assertEquals(AuthError.CodeExpired, state.authError)
    }

    @Test
    fun `when verify fails with MaxAttemptsExceeded error, authError is updated with retryAfterSeconds`() = runTest {
        fakeRepository.verifyResult = Either.Left(AuthError.MaxAttemptsExceeded(retryAfterSeconds = 300L))
        viewModel.onEvent(VerifyEmailUiEvent.CodeChanged("123456"))

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSuccess)
        assertEquals(AuthError.MaxAttemptsExceeded(retryAfterSeconds = 300L), state.authError)
    }

    @Test
    fun `two immediate verify submissions start only one request`() = runTest {
        val completable = CompletableDeferred<Either<AuthError.VerifyEmail, Unit>>()
        fakeRepository.verifyCompletable = completable

        viewModel.onEvent(VerifyEmailUiEvent.CodeChanged("12345"))
        viewModel.onEvent(VerifyEmailUiEvent.Verify)
        viewModel.onEvent(VerifyEmailUiEvent.Verify)

        testDispatcher.scheduler.runCurrent()
        assertEquals(0, fakeRepository.verifyCallCount)

        viewModel.onEvent(VerifyEmailUiEvent.CodeChanged("123456"))
        viewModel.onEvent(VerifyEmailUiEvent.Verify)

        testDispatcher.scheduler.runCurrent()
        assertEquals(1, fakeRepository.verifyCallCount)
        assertTrue(viewModel.uiState.value.isLoading)

        completable.complete(Either.Right(Unit))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `resend code triggers repository and starts cooldown countdown`() = runTest {
        viewModel.onEvent(VerifyEmailUiEvent.ResendCode)

        testDispatcher.scheduler.runCurrent()
        assertEquals(1, fakeRepository.resendCallCount)
        assertEquals(testEmail, fakeRepository.lastResendEmail)

        val state = viewModel.uiState.value
        assertFalse(state.isResending)
        assertEquals("Verification code resent", state.infoMessage)
        assertEquals(60, state.resendCooldownSeconds)

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.resendCooldownSeconds)
    }

    @Test
    fun `resend cooldown prevents subsequent resend attempts until expired`() = runTest {
        viewModel.startCooldown(30)
        assertEquals(30, viewModel.uiState.value.resendCooldownSeconds)

        viewModel.onEvent(VerifyEmailUiEvent.ResendCode)
        testDispatcher.scheduler.runCurrent()

        assertEquals(0, fakeRepository.resendCallCount)

        testDispatcher.scheduler.advanceTimeBy(15_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(15, viewModel.uiState.value.resendCooldownSeconds)

        testDispatcher.scheduler.advanceTimeBy(16_000L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.resendCooldownSeconds)

        viewModel.onEvent(VerifyEmailUiEvent.ResendCode)
        testDispatcher.scheduler.runCurrent()
        assertEquals(1, fakeRepository.resendCallCount)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `cooldown timer uses SavedStateHandle target epoch to survive ViewModel recreation`() = runTest {
        viewModel.startCooldown(60)
        assertEquals(60, viewModel.uiState.value.resendCooldownSeconds)

        // Advance virtual time by 20 seconds
        testDispatcher.scheduler.advanceTimeBy(20_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(40, viewModel.uiState.value.resendCooldownSeconds)

        // Recreate ViewModel with the exact same SavedStateHandle (simulating process recreation)
        val recreatedViewModel = VerifyEmailViewModel(testEmail, fakeRepository, savedStateHandle).apply {
            timeProvider = { baseEpoch + testDispatcher.scheduler.currentTime }
        }

        // Remaining cooldown should reflect elapsed wall-clock time (60 - 20 = 40s) without resetting to 60s
        assertEquals(40, recreatedViewModel.uiState.value.resendCooldownSeconds)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `SavedStateHandle preserves entered code across ViewModel recreation`() {
        viewModel.onEvent(VerifyEmailUiEvent.CodeChanged("1234"))
        assertEquals("1234", savedStateHandle.get<String>("otp_code"))

        val recreatedViewModel = VerifyEmailViewModel(testEmail, fakeRepository, savedStateHandle)
        assertEquals("1234", recreatedViewModel.uiState.value.code)
    }

    @Test
    fun `when resend fails, authError is populated`() = runTest {
        fakeRepository.resendResult = Either.Left(DataError.Remote.Unavailable)
        viewModel.onEvent(VerifyEmailUiEvent.ResendCode)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.resendCallCount)
        val state = viewModel.uiState.value
        assertFalse(state.isResending)
        assertEquals(AuthError.Data(DataError.Remote.Unavailable), state.authError)
    }

    @Test
    fun `dismissInfo clears infoMessage`() = runTest {
        viewModel.onEvent(VerifyEmailUiEvent.ResendCode)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Verification code resent", viewModel.uiState.value.infoMessage)

        viewModel.onEvent(VerifyEmailUiEvent.DismissInfo)
        assertNull(viewModel.uiState.value.infoMessage)
    }
}

private class FakeAuthRepository : AuthRepository {
    var verifyCallCount = 0
    var lastVerifyEmail: String? = null
    var lastVerifyCode: String? = null
    var verifyResult: Either<AuthError.VerifyEmail, Unit> = Either.Right(Unit)
    var verifyCompletable: CompletableDeferred<Either<AuthError.VerifyEmail, Unit>>? = null

    var resendCallCount = 0
    var lastResendEmail: String? = null
    var resendResult: Either<DataError, Unit> = Either.Right(Unit)

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
    ): Either<AuthError.VerifyEmail, Unit> {
        verifyCallCount++
        lastVerifyEmail = email
        lastVerifyCode = code
        val completable = verifyCompletable
        return completable?.await() ?: verifyResult
    }

    override suspend fun resendVerification(email: String): Either<DataError, Unit> {
        resendCallCount++
        lastResendEmail = email
        return resendResult
    }

    override suspend fun forgotPassword(email: String): Either<DataError, Unit> = Either.Right(Unit)

    override suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Either<AuthError.ResetPassword, Unit> = Either.Right(Unit)

    override suspend fun logout(): Either<DataError, Unit> = Either.Right(Unit)
}
