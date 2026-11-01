package io.github.neronguyen.chat.feature.chat.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.ui.asUiText
import io.github.neronguyen.chat.feature.chat.R

@Composable
fun ChatError.asUiText(): String = when (this) {
    ChatError.ParticipantNotFound -> stringResource(R.string.chat_error_participant_not_found)
    ChatError.RoomNotFound -> stringResource(R.string.chat_error_room_not_found)
    ChatError.InvalidRoomSize -> stringResource(R.string.chat_error_invalid_room_size)
    ChatError.AlreadyInRoom -> stringResource(R.string.chat_error_already_in_room)
    ChatError.ParticipantNotInRoom -> stringResource(R.string.chat_error_participant_not_in_room)
    ChatError.MessageNotFound -> stringResource(R.string.chat_error_message_not_found)
    ChatError.NotMessageSender -> stringResource(R.string.chat_error_not_message_sender)
    is ChatError.Data -> error.asUiText()
}
