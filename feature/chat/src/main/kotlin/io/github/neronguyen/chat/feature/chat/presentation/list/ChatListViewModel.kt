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
import io.github.neronguyen.chat.core.data.sync.ChatSyncManager
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.ChatRoom
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.feature.chat.presentation.util.SyncCategory
import io.github.neronguyen.chat.feature.chat.presentation.util.toCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val chatSyncManager: ChatSyncManager,
) : ViewModel() {

    internal var nowProvider: () -> Instant = { Clock.System.now() }
    internal var zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }

    val searchState = TextFieldState()

    private val searchQueryFlow = MutableStateFlow("")
    private val isBannerDismissedFlow = MutableStateFlow(false)
    private val isInitialLoadCompleteFlow = MutableStateFlow(false)
    private var lastSyncCategory: SyncCategory? = null

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    init {
        observeSearchField()
        observeSyncState()
        observeChats()
    }

    fun onEvent(event: ChatListUiEvent) {
        when (event) {
            ChatListUiEvent.RefreshChats -> refreshChats()
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

    private fun observeSyncState() {
        viewModelScope.launch {
            chatSyncManager.syncState.collect { syncState ->
                val newCategory = syncState.toCategory()
                val prevCategory = lastSyncCategory
                if (prevCategory != null) {
                    if (newCategory == SyncCategory.ERROR ||
                        (newCategory == SyncCategory.OFFLINE && prevCategory != SyncCategory.OFFLINE)
                    ) {
                        isBannerDismissedFlow.value = false
                    }
                }
                lastSyncCategory = newCategory

                if (syncState is ChatSyncState.Synced ||
                    syncState is ChatSyncState.Failed ||
                    syncState is ChatSyncState.Offline ||
                    syncState is ChatSyncState.Idle
                ) {
                    isInitialLoadCompleteFlow.value = true
                }
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
            chatRoomRepository.getChats().collect { rooms ->
                if (rooms.isNotEmpty()) {
                    isInitialLoadCompleteFlow.value = true
                }
            }
        }

        val syncUiFlow = combine(
            chatSyncManager.syncState,
            isBannerDismissedFlow,
            isInitialLoadCompleteFlow,
        ) { syncState, isBannerDismissed, isInitialLoadComplete ->
            Triple(syncState, isBannerDismissed, isInitialLoadComplete)
        }

        viewModelScope.launch {
            combine(
                chatRoomRepository.getChats(),
                currentUserIdFlow,
                searchQueryFlow,
                syncUiFlow,
            ) { rooms, currentUserId, searchQuery, (syncState, isBannerDismissed, isInitialLoadComplete) ->
                buildUiState(
                    rooms = rooms,
                    currentUserId = currentUserId,
                    searchQuery = searchQuery,
                    syncState = syncState,
                    isBannerDismissed = isBannerDismissed,
                    isInitialLoadComplete = isInitialLoadComplete,
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
        syncState: ChatSyncState,
        isBannerDismissed: Boolean,
        isInitialLoadComplete: Boolean,
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
            syncState = syncState,
            isInitialLoadComplete = isInitialLoadComplete,
            isBannerDismissed = isBannerDismissed,
        )
    }

    private fun refreshChats() {
        viewModelScope.launch {
            chatSyncManager.retry()
        }
    }

    private fun dismissSyncBanner() {
        isBannerDismissedFlow.value = true
    }

    private fun clearSearch() {
        searchState.clearText()
        searchQueryFlow.value = ""
    }
}
