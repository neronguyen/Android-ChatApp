package io.github.neronguyen.chat.feature.chat.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface ChatRoute : NavKey {

    @Serializable
    data object ChatList : ChatRoute

    @Serializable
    data class Conversation(val roomId: String) : ChatRoute

    @Serializable
    data object NewChat : ChatRoute

    @Serializable
    data object Account : ChatRoute
}
