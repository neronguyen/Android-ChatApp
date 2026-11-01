package io.github.neronguyen.chat.feature.chat.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.neronguyen.chat.feature.chat.R
import io.github.neronguyenvn.nerochat.contract.validation.chat.ChatValidationError

@Composable
fun ChatValidationError.asUiText(): String = when (this) {
    ChatValidationError.EmptySearchEmail -> stringResource(R.string.new_chat_error_empty_email)
    ChatValidationError.InvalidSearchEmail -> stringResource(R.string.new_chat_error_invalid_email)
    ChatValidationError.SelfParticipantNotAllowed -> stringResource(R.string.new_chat_error_self_participant)
    ChatValidationError.MissingDirectRecipient,
    ChatValidationError.InvalidTargetUserId -> stringResource(R.string.new_chat_error_missing_direct_recipient)
    ChatValidationError.EmptyFirstMessage -> stringResource(R.string.new_chat_error_empty_first_message)
    ChatValidationError.TooFewGroupParticipants,
    ChatValidationError.InvalidGroupParticipantId -> stringResource(R.string.new_chat_error_no_group_participants)
    ChatValidationError.EmptyParticipantIds -> stringResource(R.string.new_chat_error_empty_participant_ids)
    ChatValidationError.InvalidParticipantId -> stringResource(R.string.new_chat_error_invalid_participant_id)
}
