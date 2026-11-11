package io.github.neronguyen.chat.core.model

import io.github.neronguyen.chat.core.model.error.DataError

sealed interface ChatSyncState {
    /** Inactive, unauthenticated, or awaiting sync trigger. */
    data object Idle : ChatSyncState

    /** Catch-up sync is actively reconciling room/message gaps. */
    data object Syncing : ChatSyncState

    /** Catch-up sync completed successfully; local cache is up to date. */
    data object Synced : ChatSyncState

    /** Catch-up sync failed due to network or server error. */
    data class Failed(val error: DataError) : ChatSyncState
}
