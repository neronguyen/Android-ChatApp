package io.github.neronguyen.chat.feature.chat.presentation.util

import io.github.neronguyen.chat.core.model.ChatSyncState

internal enum class SyncCategory { QUIET, PROGRESS, OFFLINE, ERROR }

internal fun ChatSyncState.toCategory(): SyncCategory = when (this) {
    is ChatSyncState.Idle, is ChatSyncState.Synced -> SyncCategory.QUIET
    is ChatSyncState.Connecting, is ChatSyncState.Syncing -> SyncCategory.PROGRESS
    is ChatSyncState.Offline -> SyncCategory.OFFLINE
    is ChatSyncState.Failed -> SyncCategory.ERROR
}
