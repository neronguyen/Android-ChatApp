package io.github.neronguyen.chat.core.data.mapper

import io.github.neronguyen.chat.core.model.User
import io.github.neronguyenvn.nerochat.contract.auth.AuthenticatedUserDto
import io.github.neronguyenvn.nerochat.contract.auth.UserDto

internal fun UserDto.toDomain(): User {
    return User(
        id = id,
        email = email,
        displayName = displayName,
        isEmailVerified = isEmailVerified
    )
}

internal fun AuthenticatedUserDto.toDomainUser(): User {
    return user.toDomain()
}
