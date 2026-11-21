package io.github.neronguyen.chat.feature.auth.presentation.reset

import androidx.lifecycle.SavedStateHandle
import arrow.core.Either
import io.github.neronguyen.chat.core.model.error.AuthError
import io.github.neronguyen.chat.core.testing.FakeAuthRepository
import io.github.neronguyenvn.nerochat.contract.validation.auth.AuthValidationError
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

        assertEquals(0, fakeRepository.resetPasswordCalls.size)
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

        assertEquals(0, fakeRepository.resetPasswordCalls.size)
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

        assertEquals(
            listOf(Triple(testEmail, "123456", "Secret1234!")),
            fakeRepository.resetPasswordCalls,
        )
        assertTrue(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `when reset fails with SamePassword error, authError is updated`() = runTest {
        fakeRepository.resetPasswordHandler = { _, _, _ -> Either.Left(AuthError.SamePassword) }
        viewModel.onEvent(ResetPasswordUiEvent.CodeChanged("123456"))
        viewModel.onEvent(ResetPasswordUiEvent.NewPasswordChanged("Secret1234!"))
        viewModel.onEvent(ResetPasswordUiEvent.ConfirmPasswordChanged("Secret1234!"))
        viewModel.onEvent(ResetPasswordUiEvent.Reset)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.resetPasswordCalls.size)
        val state = viewModel.uiState.value
        assertFalse(state.isSuccess)
        assertEquals(AuthError.SamePassword, state.authError)
    }

    @Test
    fun `when reset fails with InvalidCode error, authError is updated`() = runTest {
        fakeRepository.resetPasswordHandler = { _, _, _ -> Either.Left(AuthError.InvalidCode) }
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
