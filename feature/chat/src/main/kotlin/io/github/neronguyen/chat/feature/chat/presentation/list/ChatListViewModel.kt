package io.github.neronguyen.chat.feature.chat.presentation.list

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
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.error.DataError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRoomRepository: ChatRoomRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    internal var nowProvider: () -> Instant = { Clock.System.now() }
    internal var zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }

    val searchState = TextFieldState()

    private val searchQueryFlow = MutableStateFlow("")
    private val syncStatusFlow = MutableStateFlow(SyncStatus())

    private val _uiState = MutableStateFlow(ChatListUiState(isSyncing = true))
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        observeSearchField()
        observeChats()
        syncChats()
    }

    fun onEvent(event: ChatListUiEvent) {
        when (event) {
            ChatListUiEvent.RefreshChats -> syncChats()
            ChatListUiEvent.DismissSyncBanner -> dismissSyncBanner()
            ChatListUiEvent.ClearSearch -> clearSearch()
        }
    }

    fun onSearchQueryChanged(query: String) {
        if (searchState.text.toString() != query) {
            searchState.setTextAndPlaceCursorAtEnd(query)
        }
        searchQueryFlow.value = query
    }

    private fun observeSearchField() {
        viewModelScope.launch {
            snapshotFlow { searchState.text.toString() }
                .distinctUntilChanged()
                .collect { query ->
                    searchQueryFlow.value = query
                }
        }
    }

    private fun observeChats() {
        val currentUserIdFlow = authRepository.getAuthState()
            .map { authState ->
                (authState as? AuthState.Authenticated)?.user?.id
            }
            .distinctUntilChanged()

        viewModelScope.launch {
            combine(
                chatRoomRepository.getChats(),
                currentUserIdFlow,
                searchQueryFlow,
                syncStatusFlow,
            ) { rooms, currentUserId, searchQuery, syncStatus ->
                buildUiState(
                    rooms = rooms,
                    currentUserId = currentUserId,
                    searchQuery = searchQuery,
                    syncStatus = syncStatus,
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private fun buildUiState(
        rooms: List<ChatRoom>,
        currentUserId: String?,
        searchQuery: String,
        syncStatus: SyncStatus,
    ): ChatListUiState {
        val mappedRooms = rooms.toChatRoomUiModels(
            currentUserId = currentUserId,
            now = nowProvider(),
            zoneId = zoneIdProvider(),
        )
        val trimmedQuery = searchQuery.trim().lowercase(Locale.ROOT)
        val filteredRooms = if (trimmedQuery.isEmpty()) {
            mappedRooms
        } else {
            mappedRooms.filter { room ->
                room.searchableText.contains(trimmedQuery)
            }
        }

        return ChatListUiState(
            chats = filteredRooms,
            searchQuery = searchQuery,
            isSyncing = syncStatus.isSyncing,
            isInitialLoadComplete = syncStatus.isInitialLoadComplete,
            syncError = syncStatus.syncError,
            isBannerDismissed = syncStatus.isBannerDismissed,
        )
    }

    private fun syncChats() {
        if (syncStatusFlow.value.isSyncing && syncStatusFlow.value.isSyncJobActive) return

        syncStatusFlow.update {
            it.copy(
                isSyncing = true,
                isSyncJobActive = true,
                syncError = null,
                isBannerDismissed = false,
            )
        }

        viewModelScope.launch {
            chatRoomRepository.syncChats()
                .onLeft { networkError ->
                    syncStatusFlow.update {
                        it.copy(
                            isSyncing = false,
                            isSyncJobActive = false,
                            isInitialLoadComplete = true,
                            syncError = networkError,
                        )
                    }
                }
                .onRight {
                    syncStatusFlow.update {
                        it.copy(
                            isSyncing = false,
                            isSyncJobActive = false,
                            isInitialLoadComplete = true,
                            syncError = null,
                        )
                    }
                }
        }
    }

    private fun dismissSyncBanner() {
        syncStatusFlow.update {
            it.copy(isBannerDismissed = true)
        }
    }

    private fun clearSearch() {
        searchState.clearText()
        searchQueryFlow.value = ""
    }

    private data class SyncStatus(
        val isSyncing: Boolean = true,
        val isSyncJobActive: Boolean = false,
        val isInitialLoadComplete: Boolean = false,
        val syncError: DataError? = null,
        val isBannerDismissed: Boolean = false,
    )
}
