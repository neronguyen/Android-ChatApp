package io.github.neronguyen.chat.feature.auth.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.neronguyen.chat.feature.auth.R
import io.github.neronguyen.chat.feature.auth.presentation.forgot.ForgotPasswordRoute
import io.github.neronguyen.chat.feature.auth.presentation.forgot.ForgotPasswordViewModel
import io.github.neronguyen.chat.feature.auth.presentation.login.LoginRoute
import io.github.neronguyen.chat.feature.auth.presentation.login.LoginViewModel
import io.github.neronguyen.chat.feature.auth.presentation.register.RegisterRoute
import io.github.neronguyen.chat.feature.auth.presentation.register.RegisterViewModel
import io.github.neronguyen.chat.feature.auth.presentation.reset.ResetPasswordRoute
import io.github.neronguyen.chat.feature.auth.presentation.reset.ResetPasswordViewModel
import io.github.neronguyen.chat.feature.auth.presentation.verify.VerifyEmailRoute
import io.github.neronguyen.chat.feature.auth.presentation.verify.VerifyEmailViewModel

fun EntryProviderScope<NavKey>.authSection(
    onAuthSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToLogin: (email: String?, bannerMessage: String?) -> Unit,
    onNavigateToVerifyEmail: (email: String) -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onNavigateToResetPassword: (email: String) -> Unit,
) {
    entry<AuthRoute.Login> { route ->
        val viewModel: LoginViewModel = hiltViewModel()
        LoginRoute(
            viewModel = viewModel,
            onNavigateToRegister = onNavigateToRegister,
            onLoginSuccess = onAuthSuccess,
            onNavigateToVerifyEmail = onNavigateToVerifyEmail,
            onNavigateToForgotPassword = onNavigateToForgotPassword,
            prefilledEmail = route.email,
            bannerMessage = route.bannerMessage,
        )
    }
    entry<AuthRoute.Register> {
        val viewModel: RegisterViewModel = hiltViewModel()
        RegisterRoute(
            viewModel = viewModel,
            onNavigateToLogin = { onNavigateToLogin(null, null) },
            onRegisterSuccess = onNavigateToVerifyEmail,
        )
    }
    entry<AuthRoute.VerifyEmail> { route ->
        val viewModel = hiltViewModel<VerifyEmailViewModel, VerifyEmailViewModel.Factory> { factory ->
            factory.create(route.email)
        }
        val verifiedBanner = stringResource(R.string.email_verified_banner)
        VerifyEmailRoute(
            viewModel = viewModel,
            onVerifySuccess = { onNavigateToLogin(route.email, verifiedBanner) },
            onNavigateToLogin = { onNavigateToLogin(null, null) },
        )
    }
    entry<AuthRoute.ForgotPassword> {
        val viewModel: ForgotPasswordViewModel = hiltViewModel()
        ForgotPasswordRoute(
            viewModel = viewModel,
            onNavigateToResetPassword = onNavigateToResetPassword,
            onNavigateToLogin = { onNavigateToLogin(null, null) },
        )
    }
    entry<AuthRoute.ResetPassword> { route ->
        val viewModel = hiltViewModel<ResetPasswordViewModel, ResetPasswordViewModel.Factory> { factory ->
            factory.create(route.email)
        }
        val resetBanner = stringResource(R.string.password_reset_banner)
        ResetPasswordRoute(
            viewModel = viewModel,
            onResetSuccess = { onNavigateToLogin(route.email, resetBanner) },
            onNavigateToLogin = { onNavigateToLogin(null, null) },
        )
    }
}

@Composable
fun AuthNavGraph(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    startRoute: AuthRoute = AuthRoute.Login(),
) {
    val backStack = rememberNavBackStack(startRoute)

    val entryProvider = remember(onAuthSuccess) {
        entryProvider {
            authSection(
                onAuthSuccess = onAuthSuccess,
                onNavigateToRegister = {
                    backStack.add(AuthRoute.Register)
                },
                onNavigateToLogin = { email, bannerMessage ->
                    backStack.popOrNavigateTo(AuthRoute.Login(email, bannerMessage))
                },
                onNavigateToVerifyEmail = { email ->
                    backStack.add(AuthRoute.VerifyEmail(email))
                },
                onNavigateToForgotPassword = {
                    backStack.add(AuthRoute.ForgotPassword)
                },
                onNavigateToResetPassword = { email ->
                    backStack.add(AuthRoute.ResetPassword(email))
                },
            )
        }
    }

    val saveableStateHolderDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val decorators = remember(saveableStateHolderDecorator, viewModelStoreDecorator) {
        listOf(saveableStateHolderDecorator, viewModelStoreDecorator)
    }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = decorators,
        entryProvider = entryProvider,
    )

    NavDisplay(
        entries = entries,
        onBack = { backStack.pop() },
        modifier = modifier,
    )
}

private fun NavBackStack<NavKey>.pop() {
    if (size > 1) {
        removeAt(lastIndex)
    }
}

private fun NavBackStack<NavKey>.popOrNavigateTo(defaultRoute: NavKey) {
    if (size > 1) {
        removeAt(lastIndex)
    } else {
        add(defaultRoute)
    }
}
