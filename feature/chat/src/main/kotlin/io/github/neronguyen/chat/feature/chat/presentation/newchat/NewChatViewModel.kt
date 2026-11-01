package io.github.neronguyen.chat.feature.chat.presentation.newchat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyenvn.nerochat.contract.validation.EmailValidator
import io.github.neronguyenvn.nerochat.contract.validation.chat.ChatValidationError
import io.github.neronguyenvn.nerochat.contract.validation.chat.ChatValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val chatRoomRepository: ChatRoomRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val emailSearchState = TextFieldState()
    val firstMessageState = TextFieldState()
    val groupNameState = TextFieldState()

    private var currentUser: User? = null

    private val _uiState = MutableStateFlow(NewChatUiState())
    val uiState: StateFlow<NewChatUiState> = _uiState.asStateFlow()

    init {
        observeAuthUser()
        observeTextFields()
    }

    fun onEvent(event: NewChatUiEvent) {
        when (event) {
            is NewChatUiEvent.SelectMode -> selectMode(event.mode)
            NewChatUiEvent.SearchByEmail -> searchByEmail()
            NewChatUiEvent.ClearSearchInput -> clearSearchInput()
            NewChatUiEvent.ClearSearchResult -> clearSearchResult()
            NewChatUiEvent.ToggleSearchedParticipant -> toggleSearchedParticipant()
            NewChatUiEvent.ClearDirectParticipant -> clearDirectParticipant()
            is NewChatUiEvent.RemoveGroupParticipant -> removeGroupParticipant(event.userId)
            NewChatUiEvent.SubmitChat -> submitChat()
            NewChatUiEvent.DismissCreateError -> dismissCreateError()
        }
    }

    fun onChatCreatedHandled() {
        _uiState.update { it.copy(createdRoomId = null) }
    }

    fun onEmailSearchQueryChanged(query: String) {
        if (emailSearchState.text.toString() != query) {
            emailSearchState.setTextAndPlaceCursorAtEnd(query)
        }
    }

    fun onFirstMessageChanged(message: String) {
        if (firstMessageState.text.toString() != message) {
            firstMessageState.setTextAndPlaceCursorAtEnd(message)
        }
        _uiState.update { it.copy(firstMessage = message) }
    }

    fun onGroupNameChanged(name: String) {
        if (groupNameState.text.toString() != name) {
            groupNameState.setTextAndPlaceCursorAtEnd(name)
        }
        _uiState.update { it.copy(groupName = name) }
    }

    private fun observeAuthUser() {
        viewModelScope.launch {
            authRepository.getAuthState()
                .collect { state ->
                    currentUser = (state as? AuthState.Authenticated)?.user
                }
        }
    }

    private fun observeTextFields() {
        viewModelScope.launch {
            snapshotFlow { firstMessageState.text.toString() }
                .distinctUntilChanged()
                .collect { text ->
                    _uiState.update { state ->
                        val clearedValidation = if (
                            text.isNotBlank() &&
                            state.validationError == ChatValidationError.EmptyFirstMessage
                        ) {
                            null
                        } else {
                            state.validationError
                        }
                        state.copy(
                            firstMessage = text,
                            validationError = clearedValidation,
                        )
                    }
                }
        }

        viewModelScope.launch {
            snapshotFlow { groupNameState.text.toString() }
                .distinctUntilChanged()
                .collect { text ->
                    _uiState.update { it.copy(groupName = text) }
                }
        }
    }

    private fun selectMode(mode: NewChatMode) {
        if (_uiState.value.mode == mode) return
        _uiState.update {
            it.copy(
                mode = mode,
                validationError = null,
                createError = null,
            )
        }
    }

    private fun searchByEmail() {
        if (_uiState.value.isSearching) return

        val email = EmailValidator.normalize(emailSearchState.text.toString())
        val validationError = ChatValidator.validateSearchEmail(
            email = email,
            currentUserEmail = currentUser?.email,
        )
        if (validationError != null) {
            _uiState.update {
                it.copy(
                    validationError = validationError,
                    searchError = null,
                    searchedParticipant = if (validationError is ChatValidationError.SelfParticipantNotAllowed) null else it.searchedParticipant,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isSearching = true,
                validationError = null,
                searchError = null,
            )
        }

        viewModelScope.launch {
            chatRoomRepository.findChatParticipantByEmail(email)
                .onLeft { networkError ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchError = networkError,
                            searchedParticipant = null,
                        )
                    }
                }
                .onRight { participant ->
                    val latestSelf = currentUser
                    val isSelf = latestSelf != null && (
                            participant.userId == latestSelf.id ||
                                    participant.email.equals(latestSelf.email, ignoreCase = true)
                            )

                    if (isSelf) {
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                validationError = ChatValidationError.SelfParticipantNotAllowed,
                                searchedParticipant = null,
                            )
                        }
                    } else {
                        val uiModel = participant.toParticipantUiModel()
                        _uiState.update { state ->
                            state.copy(
                                isSearching = false,
                                searchedParticipant = uiModel,
                                searchError = null,
                                validationError = null,
                                selectedDirectParticipant = if (state.mode == NewChatMode.DIRECT) {
                                    uiModel
                                } else {
                                    state.selectedDirectParticipant
                                },
                            )
                        }
                    }
                }
        }
    }

    private fun clearSearchInput() {
        emailSearchState.clearText()
        _uiState.update { state ->
            val clearedValidation = if (state.validationError is ChatValidationError.SearchParticipant) {
                null
            } else {
                state.validationError
            }
            state.copy(
                validationError = clearedValidation,
                searchError = null,
            )
        }
    }

    private fun clearSearchResult() {
        _uiState.update {
            it.copy(
                searchedParticipant = null,
                searchError = null,
                validationError = null,
            )
        }
    }

    private fun toggleSearchedParticipant() {
        val searched = _uiState.value.searchedParticipant ?: return
        _uiState.update { state ->
            when (state.mode) {
                NewChatMode.DIRECT -> {
                    val nextSelection =
                        if (state.selectedDirectParticipant?.userId == searched.userId) {
                            null
                        } else {
                            searched
                        }
                    state.copy(
                        selectedDirectParticipant = nextSelection,
                        validationError = null,
                    )
                }

                NewChatMode.GROUP -> {
                    val alreadySelected = state.selectedGroupParticipants.any {
                        it.userId == searched.userId
                    }
                    val updatedList = if (alreadySelected) {
                        state.selectedGroupParticipants.filterNot { it.userId == searched.userId }
                    } else {
                        state.selectedGroupParticipants + searched
                    }
                    state.copy(
                        selectedGroupParticipants = updatedList,
                        validationError = null,
                    )
                }
            }
        }
    }

    private fun clearDirectParticipant() {
        _uiState.update { state ->
            val currentDirectId = state.selectedDirectParticipant?.userId
            state.copy(
                selectedDirectParticipant = null,
                searchedParticipant = if (state.searchedParticipant?.userId == currentDirectId) {
                    null
                } else {
                    state.searchedParticipant
                },
            )
        }
    }

    private fun removeGroupParticipant(userId: String) {
        _uiState.update { state ->
            state.copy(
                selectedGroupParticipants = state.selectedGroupParticipants.filterNot {
                    it.userId == userId
                },
            )
        }
    }

    private fun submitChat() {
        val snapshot = _uiState.value
        if (snapshot.isCreatingRoom) return

        when (snapshot.mode) {
            NewChatMode.DIRECT -> submitDirectChat(snapshot)
            NewChatMode.GROUP -> submitGroupChat(snapshot)
        }
    }

    private fun submitDirectChat(snapshot: NewChatUiState) {
        val recipient = snapshot.selectedDirectParticipant
        val messageText = firstMessageState.text.toString().trim()
            .ifEmpty { snapshot.firstMessage.trim() }

        val validationError = ChatValidator.validateDirectRoom(
            targetUserId = recipient?.userId,
            message = messageText,
        ).firstOrNull()

        if (validationError != null) {
            _uiState.update {
                it.copy(validationError = validationError)
            }
            return
        }

        _uiState.update {
            it.copy(
                isCreatingRoom = true,
                validationError = null,
                createError = null,
            )
        }

        viewModelScope.launch {
            chatRoomRepository.createDirectChatRoom(
                targetUserId = recipient!!.userId,
                message = messageText,
            )
                .onLeft { networkError ->
                    _uiState.update {
                        it.copy(
                            isCreatingRoom = false,
                            createError = networkError,
                        )
                    }
                }
                .onRight { room ->
                    _uiState.update {
                        it.copy(
                            isCreatingRoom = false,
                            createError = null,
                            createdRoomId = room.id,
                        )
                    }
                }
        }
    }

    private fun submitGroupChat(snapshot: NewChatUiState) {
        val participants = snapshot.selectedGroupParticipants
        val validationError = ChatValidator.validateGroupRoom(
            participantIds = participants.map { it.userId },
        ).firstOrNull()

        if (validationError != null) {
            _uiState.update {
                it.copy(validationError = validationError)
            }
            return
        }

        val groupNameText = groupNameState.text.toString().trim()
            .ifEmpty { snapshot.groupName.trim() }
            .ifEmpty { null }

        _uiState.update {
            it.copy(
                isCreatingRoom = true,
                validationError = null,
                createError = null,
            )
        }

        viewModelScope.launch {
            chatRoomRepository.createGroupChatRoom(
                participantIds = participants.map { it.userId },
                name = groupNameText,
            )
                .onLeft { networkError ->
                    _uiState.update {
                        it.copy(
                            isCreatingRoom = false,
                            createError = networkError,
                        )
                    }
                }
                .onRight { room ->
                    _uiState.update {
                        it.copy(
                            isCreatingRoom = false,
                            createError = null,
                            createdRoomId = room.id,
                        )
                    }
                }
        }
    }

    private fun dismissCreateError() {
        _uiState.update { it.copy(createError = null) }
    }
}
