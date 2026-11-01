package io.github.neronguyen.chat.feature.chat.presentation.conversation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.neronguyen.chat.core.data.realtime.RealtimeConnectionManager
import io.github.neronguyen.chat.core.data.repository.AuthRepository
import io.github.neronguyen.chat.core.data.repository.ChatMessageRepository
import io.github.neronguyen.chat.core.data.repository.ChatRoomRepository
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.model.error.ChatError
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.feature.chat.presentation.newchat.toParticipantUiModel
import io.github.neronguyenvn.nerochat.contract.validation.EmailValidator
import io.github.neronguyenvn.nerochat.contract.validation.chat.ChatValidationError
import io.github.neronguyenvn.nerochat.contract.validation.chat.ChatValidator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Instant

private const val INITIAL_PAGE_SIZE = 20
private const val PAGE_SIZE = 20

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ConversationViewModel.Factory::class)
class ConversationViewModel @AssistedInject constructor(
    @Assisted val roomId: String,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val realtimeConnectionManager: RealtimeConnectionManager,
    private val authRepository: AuthRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(roomId: String): ConversationViewModel
    }

    internal var nowProvider: () -> Instant = { Clock.System.now() }
    internal var zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }

    val composerState = TextFieldState()
    val addParticipantEmailState = TextFieldState()

    private val limitFlow = MutableStateFlow(INITIAL_PAGE_SIZE)
    private val syncStatusFlow = MutableStateFlow(ConversationSyncStatus())
    private val addParticipantDialogFlow = MutableStateFlow<AddParticipantDialogState?>(null)
    private val actionErrorFlow = MutableStateFlow<ChatError?>(null)
    private val isLeavingFlow = MutableStateFlow(false)
    private val hasLeftFlow = MutableStateFlow(false)
    private val isBannerDismissedFlow = MutableStateFlow(false)

    private val currentUserFlow: StateFlow<User?> = authRepository.getAuthState()
        .map { (it as? AuthState.Authenticated)?.user }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    private val _uiState = MutableStateFlow(
        ConversationUiState(
            roomId = roomId,
            isInitialLoading = true,
            isSyncing = true,
        )
    )
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    init {
        observeConversation()
        syncRoomAndRecentMessages()
    }

    fun onEvent(event: ConversationUiEvent) {
        when (event) {
            ConversationUiEvent.SendMessage -> sendMessage()
            is ConversationUiEvent.RetryMessage -> retryMessage(event.messageId)
            is ConversationUiEvent.DeleteMessage -> deleteMessage(event.messageId)
            ConversationUiEvent.LoadOlderMessages -> loadOlderMessages()
            ConversationUiEvent.RetrySync -> syncRoomAndRecentMessages()
            ConversationUiEvent.DismissBanner -> dismissBanner()
            ConversationUiEvent.ClearActionError -> clearActionError()
            ConversationUiEvent.LeaveRoom -> leaveRoom()
            ConversationUiEvent.OpenAddParticipantDialog -> openAddParticipantDialog()
            ConversationUiEvent.DismissAddParticipantDialog -> dismissAddParticipantDialog()
            ConversationUiEvent.SearchParticipantByEmail -> searchParticipantByEmail()
            ConversationUiEvent.ClearAddParticipantSearch -> clearAddParticipantSearch()
            ConversationUiEvent.ConfirmAddParticipant -> confirmAddParticipant()
        }
    }

    private fun observeConversation() {
        val currentUserIdFlow = currentUserFlow
            .map { it?.id }
            .distinctUntilChanged()

        val messagesFlow = limitFlow.flatMapLatest { limit ->
            chatMessageRepository.getMessages(roomId, limit)
        }

        viewModelScope.launch {
            combine(
                chatRoomRepository.getChatRoom(roomId),
                messagesFlow,
                currentUserIdFlow,
                realtimeConnectionManager.connectionState,
                syncStatusFlow,
            ) { room, messages, currentUserId, connectionState, syncStatus ->
                val header = mapConversationHeader(room, currentUserId)
                val items = mapConversationItems(
                    messages = messages,
                    currentUserId = currentUserId,
                    room = room,
                    now = nowProvider(),
                    zoneId = zoneIdProvider(),
                )

                _uiState.update { current ->
                    current.copy(
                        roomId = roomId,
                        roomType = header.roomType,
                        title = header.title,
                        subtitle = header.subtitle,
                        initials = header.initials,
                        avatarUrl = header.avatarUrl,
                        items = items,
                        connectionState = connectionState,
                        isInitialLoading = syncStatus.isInitialLoading && items.isEmpty(),
                        isSyncing = syncStatus.isSyncing,
                        syncError = syncStatus.syncError,
                        isLoadingOlder = syncStatus.isLoadingOlder,
                        hasMoreOlderMessages = syncStatus.hasMoreOlderMessages,
                        loadOlderError = syncStatus.loadOlderError,
                    )
                }
            }.collect {}
        }

        viewModelScope.launch {
            combine(
                actionErrorFlow,
                isLeavingFlow,
                hasLeftFlow,
                isBannerDismissedFlow,
                addParticipantDialogFlow,
            ) { actionError, isLeaving, hasLeft, isBannerDismissed, addParticipantDialogState ->
                _uiState.update { current ->
                    current.copy(
                        actionError = actionError,
                        isLeavingRoom = isLeaving,
                        hasLeftRoom = hasLeft,
                        isBannerDismissed = isBannerDismissed,
                        addParticipantDialogState = addParticipantDialogState,
                    )
                }
            }.collect {}
        }
    }

    private fun syncRoomAndRecentMessages() {
        if (syncStatusFlow.value.isSyncing && syncStatusFlow.value.isSyncJobActive) return

        syncStatusFlow.update {
            it.copy(
                isSyncing = true,
                isSyncJobActive = true,
                syncError = null,
            )
        }
        isBannerDismissedFlow.value = false

        viewModelScope.launch {
            val roomResult = chatRoomRepository.syncChatRoom(roomId)
            val messagesResult = chatMessageRepository.syncRecentMessages(roomId)

            val error: ChatError.SyncRoom? = roomResult.leftOrNull()
                ?: messagesResult.leftOrNull()?.let { ChatError.Data(it) }
            val hasMore = messagesResult.getOrNull() ?: syncStatusFlow.value.hasMoreOlderMessages

            syncStatusFlow.update {
                it.copy(
                    isSyncing = false,
                    isSyncJobActive = false,
                    isInitialLoading = false,
                    syncError = error,
                    hasMoreOlderMessages = hasMore,
                )
            }
        }
    }

    private fun sendMessage() {
        val text = composerState.text.toString().trim()
        if (text.isEmpty()) return

        composerState.clearText()

        viewModelScope.launch {
            chatMessageRepository.sendMessage(roomId, text)
        }
    }

    private fun retryMessage(messageId: String) {
        viewModelScope.launch {
            chatMessageRepository.retryMessage(messageId)
        }
    }

    private fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatMessageRepository.deleteMessage(messageId)
                .onLeft { error ->
                    actionErrorFlow.value = error
                }
        }
    }

    private fun loadOlderMessages() {
        val status = syncStatusFlow.value
        if (status.isLoadingOlder || !status.hasMoreOlderMessages) return

        syncStatusFlow.update {
            it.copy(isLoadingOlder = true, loadOlderError = null)
        }

        viewModelScope.launch {
            limitFlow.update { it + PAGE_SIZE }
            chatMessageRepository.loadOlderMessages(roomId)
                .onLeft { error ->
                    syncStatusFlow.update {
                        it.copy(isLoadingOlder = false, loadOlderError = error)
                    }
                }
                .onRight { hasMore ->
                    syncStatusFlow.update {
                        it.copy(isLoadingOlder = false, hasMoreOlderMessages = hasMore)
                    }
                }
        }
    }

    private fun leaveRoom() {
        if (isLeavingFlow.value) return
        isLeavingFlow.value = true

        viewModelScope.launch {
            chatRoomRepository.leaveChatRoom(roomId)
                .onLeft { error ->
                    isLeavingFlow.value = false
                    actionErrorFlow.value = error
                }
                .onRight {
                    isLeavingFlow.value = false
                    hasLeftFlow.value = true
                }
        }
    }

    private fun openAddParticipantDialog() {
        addParticipantEmailState.clearText()
        addParticipantDialogFlow.value = AddParticipantDialogState()
    }

    private fun dismissAddParticipantDialog() {
        addParticipantEmailState.clearText()
        addParticipantDialogFlow.value = null
    }

    private fun searchParticipantByEmail() {
        val email = EmailValidator.normalize(addParticipantEmailState.text.toString())
        val current = currentUserFlow.value
        val validationError = ChatValidator.validateSearchEmail(
            email = email,
            currentUserEmail = current?.email,
        )
        if (validationError != null) {
            addParticipantDialogFlow.update {
                it?.copy(
                    validationError = validationError,
                    searchError = null,
                    searchedParticipant = null,
                )
            }
            return
        }

        addParticipantDialogFlow.update {
            it?.copy(
                isSearching = true,
                validationError = null,
                searchError = null,
            )
        }

        viewModelScope.launch {
            chatRoomRepository.findChatParticipantByEmail(email)
                .onLeft { error ->
                    addParticipantDialogFlow.update {
                        it?.copy(
                            isSearching = false,
                            searchError = error,
                            searchedParticipant = null,
                        )
                    }
                }
                .onRight { participant ->
                    val latestSelf = currentUserFlow.value
                    val isSelf = latestSelf != null && (
                        participant.userId == latestSelf.id ||
                            participant.email.equals(latestSelf.email, ignoreCase = true)
                    )
                    if (isSelf) {
                        addParticipantDialogFlow.update {
                            it?.copy(
                                isSearching = false,
                                validationError = ChatValidationError.SelfParticipantNotAllowed,
                                searchError = null,
                                searchedParticipant = null,
                            )
                        }
                    } else {
                        addParticipantDialogFlow.update {
                            it?.copy(
                                isSearching = false,
                                searchError = null,
                                validationError = null,
                                searchedParticipant = participant.toParticipantUiModel(),
                            )
                        }
                    }
                }
        }
    }

    private fun clearAddParticipantSearch() {
        addParticipantEmailState.clearText()
        addParticipantDialogFlow.update {
            it?.copy(
                searchedParticipant = null,
                searchError = null,
                validationError = null,
            )
        }
    }

    private fun confirmAddParticipant() {
        val dialog = addParticipantDialogFlow.value ?: return
        val participant = dialog.searchedParticipant ?: return

        addParticipantDialogFlow.update {
            it?.copy(isSubmitting = true, submitError = null)
        }

        viewModelScope.launch {
            chatRoomRepository.addParticipantsToGroup(roomId, listOf(participant.userId))
                .onLeft { error ->
                    addParticipantDialogFlow.update {
                        it?.copy(isSubmitting = false, submitError = error)
                    }
                }
                .onRight {
                    addParticipantDialogFlow.value = null
                }
        }
    }

    private fun dismissBanner() {
        isBannerDismissedFlow.value = true
    }

    private fun clearActionError() {
        actionErrorFlow.value = null
    }

    private data class ConversationSyncStatus(
        val isInitialLoading: Boolean = true,
        val isSyncing: Boolean = false,
        val isSyncJobActive: Boolean = false,
        val syncError: ChatError.SyncRoom? = null,
        val isLoadingOlder: Boolean = false,
        val hasMoreOlderMessages: Boolean = true,
        val loadOlderError: DataError? = null,
    )
}
