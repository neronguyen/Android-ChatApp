package io.github.neronguyen.chat.core.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val QuietMessagingLightColorScheme = lightColorScheme(
    primary = ChatPrimary,
    onPrimary = ChatOnPrimary,
    primaryContainer = ChatPrimary,
    onPrimaryContainer = ChatOnPrimary,
    secondary = ChatTextMuted,
    onSecondary = ChatOnPrimary,
    secondaryContainer = ChatSurface,
    onSecondaryContainer = ChatText,
    tertiary = ChatPrimary,
    onTertiary = ChatOnPrimary,
    tertiaryContainer = ChatSurface,
    onTertiaryContainer = ChatText,
    background = ChatBackground,
    onBackground = ChatText,
    surface = ChatBackground,
    onSurface = ChatText,
    surfaceVariant = ChatSurface,
    onSurfaceVariant = ChatTextMuted,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = ChatBackground,
    surfaceContainerLow = ChatSurface,
    surfaceContainer = ChatSurface,
    surfaceContainerHigh = ChatSurface,
    surfaceContainerHighest = ChatSurface,
    inverseSurface = ChatText,
    inverseOnSurface = ChatBackground,
    inversePrimary = ChatOnPrimary,
    outline = ChatBorder,
    outlineVariant = ChatBorder,
    scrim = Color(0x52000000),
    error = ChatError,
    onError = ChatOnError,
    errorContainer = ChatErrorContainer,
    onErrorContainer = ChatOnErrorContainer,
)

@Composable
fun ChatTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = ChatBackground.toArgb()
                window.navigationBarColor = ChatBackground.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = QuietMessagingLightColorScheme,
        shapes = QuietMessagingShapes,
        typography = Typography,
        content = content,
    )
}
