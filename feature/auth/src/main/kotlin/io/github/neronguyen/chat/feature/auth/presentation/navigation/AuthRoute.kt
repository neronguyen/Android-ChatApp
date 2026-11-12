package io.github.neronguyen.chat.feature.auth.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AuthRoute : NavKey {

    @Serializable
    data class Login(
        val email: String? = null,
        val bannerMessage: String? = null,
    ) : AuthRoute

    @Serializable
    data object Register : AuthRoute

    @Serializable
    data class VerifyEmail(
        val email: String,
    ) : AuthRoute

    @Serializable
    data object ForgotPassword : AuthRoute

    @Serializable
    data class ResetPassword(
        val email: String,
    ) : AuthRoute
}
