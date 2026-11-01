package io.github.neronguyen.chat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val QuietMessagingLightColorScheme = lightColorScheme(
    primary = ChatPrimary,
    onPrimary = ChatOnPrimary,
    primaryContainer = ChatPrimary,
    onPrimaryContainer = ChatOnPrimary,
    secondary = ChatTextMuted,
    onSecondary = ChatOnPrimary,
    secondaryContainer = ChatSurface,
    onSecondaryContainer = ChatText,
    background = ChatBackground,
    onBackground = ChatText,
    surface = ChatBackground,
    onSurface = ChatText,
    surfaceVariant = ChatSurface,
    onSurfaceVariant = ChatTextMuted,
    surfaceContainerLowest = ChatBackground,
    surfaceContainerLow = ChatSurface,
    surfaceContainer = ChatSurface,
    surfaceContainerHigh = ChatSurface,
    surfaceContainerHighest = ChatSurface,
    outline = ChatBorder,
    outlineVariant = ChatBorder,
    error = ChatError,
    onError = ChatOnError,
    errorContainer = ChatErrorContainer,
    onErrorContainer = ChatOnErrorContainer,
)

@Composable
fun ChatTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = QuietMessagingLightColorScheme,
        typography = Typography,
        content = content,
    )
}
