package io.github.neronguyen.chat.core.model.error

sealed interface AuthError {

    sealed interface Login : AuthError
    sealed interface Register : AuthError

    data object InvalidCredentials : Login
    data object EmailNotVerified : Login
    data object EmailAlreadyInUse : Register

    data class Data(val error: DataError) : Login, Register
}
