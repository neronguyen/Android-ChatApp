package io.github.neronguyen.chat.core.model

import io.github.neronguyen.chat.core.model.error.DataError

sealed interface ChatSyncState {
    /** Inactive, unauthenticated, or awaiting sync trigger. */
    data object Idle : ChatSyncState

    /** Device has no validated internet connectivity (NetworkMonitor.isOnline == false). */
    data object Offline : ChatSyncState

    /**
     * Realtime WebSocket is establishing a connection or in backoff
     * retry delay under active foreground supervision.
     */
    data object Connecting : ChatSyncState

    /** Catch-up synchronization is actively reconciling room/message gaps. */
    data object Syncing : ChatSyncState

    /** Catch-up synchronization completed successfully; local cache is up to date. */
    data object Synced : ChatSyncState

    /** Catch-up synchronization failed due to network or server error. */
    data class Failed(val error: DataError) : ChatSyncState
}
