package io.github.neronguyen.chat.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.neronguyen.chat.core.model.ChatSyncState
import io.github.neronguyen.chat.core.model.error.DataError
import io.github.neronguyen.chat.core.ui.R
import io.github.neronguyen.chat.core.ui.asUiText
import io.github.neronguyen.chat.core.ui.theme.ChatTheme

@Composable
fun SyncStatusBar(
    syncState: ChatSyncState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    if (syncState is ChatSyncState.Idle || syncState is ChatSyncState.Synced) {
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("sync_status_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (syncState) {
                is ChatSyncState.Offline -> {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.sync_status_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is ChatSyncState.Connecting -> {
                    val rotation = if (animate) {
                        val infiniteTransition = rememberInfiniteTransition(label = "connecting_rotation")
                        val animRotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "connecting_icon_rotation",
                        )
                        animRotation
                    } else {
                        0f
                    }
                    Icon(
                        imageVector = Icons.Outlined.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = rotation },
                    )
                    Text(
                        text = stringResource(R.string.sync_status_connecting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is ChatSyncState.Syncing -> {
                    val rotation = if (animate) {
                        val infiniteTransition = rememberInfiniteTransition(label = "syncing_rotation")
                        val animRotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "syncing_icon_rotation",
                        )
                        animRotation
                    } else {
                        0f
                    }
                    Icon(
                        imageVector = Icons.Outlined.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = rotation },
                    )
                    Text(
                        text = stringResource(R.string.sync_status_syncing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                is ChatSyncState.Failed -> {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = syncState.error.asUiText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (syncState is ChatSyncState.Failed) {
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.testTag("sync_status_retry_button"),
                ) {
                    Text(
                        text = stringResource(R.string.sync_status_retry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("sync_status_dismiss_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.sync_status_dismiss_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Preview(name = "Offline")
@Composable
private fun SyncStatusBarOfflinePreview() {
    ChatTheme {
        SyncStatusBar(
            syncState = ChatSyncState.Offline,
            onRetry = {},
            onDismiss = {},
            animate = false,
        )
    }
}

@Preview(name = "Connecting")
@Composable
private fun SyncStatusBarConnectingPreview() {
    ChatTheme {
        SyncStatusBar(
            syncState = ChatSyncState.Connecting,
            onRetry = {},
            onDismiss = {},
            animate = false,
        )
    }
}

@Preview(name = "Syncing")
@Composable
private fun SyncStatusBarSyncingPreview() {
    ChatTheme {
        SyncStatusBar(
            syncState = ChatSyncState.Syncing,
            onRetry = {},
            onDismiss = {},
            animate = false,
        )
    }
}

@Preview(name = "Failed")
@Composable
private fun SyncStatusBarFailedPreview() {
    ChatTheme {
        SyncStatusBar(
            syncState = ChatSyncState.Failed(DataError.Remote.Unavailable),
            onRetry = {},
            onDismiss = {},
            animate = false,
        )
    }
}
