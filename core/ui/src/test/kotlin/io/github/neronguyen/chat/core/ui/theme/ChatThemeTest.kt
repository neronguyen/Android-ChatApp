package io.github.neronguyen.chat.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chatTheme_propagatesQuietMessagingColorScheme() {
        var colorScheme: ColorScheme? = null

        composeTestRule.setContent {
            ChatTheme {
                colorScheme = MaterialTheme.colorScheme
            }
        }

        assertNotNull(colorScheme)
        val scheme = checkNotNull(colorScheme)

        // Primary & Actions
        assertEquals(ChatPrimary, scheme.primary)
        assertEquals(ChatOnPrimary, scheme.onPrimary)
        assertEquals(ChatPrimary, scheme.primaryContainer)
        assertEquals(ChatOnPrimary, scheme.onPrimaryContainer)

        // Canvas & Surface
        assertEquals(ChatBackground, scheme.background)
        assertEquals(ChatText, scheme.onBackground)
        assertEquals(ChatBackground, scheme.surface)
        assertEquals(ChatText, scheme.onSurface)
        assertEquals(ChatSurface, scheme.surfaceVariant)
        assertEquals(ChatTextMuted, scheme.onSurfaceVariant)

        // Zero surface elevation tint neutralization
        assertEquals(Color.Transparent, scheme.surfaceTint)

        // Surface Containers
        assertEquals(ChatBackground, scheme.surfaceContainerLowest)
        assertEquals(ChatSurface, scheme.surfaceContainerLow)
        assertEquals(ChatSurface, scheme.surfaceContainer)
        assertEquals(ChatSurface, scheme.surfaceContainerHigh)
        assertEquals(ChatSurface, scheme.surfaceContainerHighest)

        // Borders & Outlines
        assertEquals(ChatBorder, scheme.outline)
        assertEquals(ChatBorder, scheme.outlineVariant)

        // Errors
        assertEquals(ChatError, scheme.error)
        assertEquals(ChatOnError, scheme.onError)
        assertEquals(ChatErrorContainer, scheme.errorContainer)
        assertEquals(ChatOnErrorContainer, scheme.onErrorContainer)
    }

    @Test
    fun chatTheme_propagatesQuietMessagingShapes() {
        var shapes: Shapes? = null

        composeTestRule.setContent {
            ChatTheme {
                shapes = MaterialTheme.shapes
            }
        }

        assertNotNull(shapes)
        val resolvedShapes = checkNotNull(shapes)

        assertEquals(RoundedCornerShape(12.dp), resolvedShapes.small)
        assertEquals(RoundedCornerShape(16.dp), resolvedShapes.medium)
        assertEquals(RoundedCornerShape(16.dp), resolvedShapes.large)
    }

    @Test
    fun chatTheme_avatarShapeIsDecoupledCircleShape() {
        assertEquals(CircleShape, AvatarShape)
    }

    @Test
    fun chatTheme_propagatesQuietMessagingTypography() {
        var typography: Typography? = null

        composeTestRule.setContent {
            ChatTheme {
                typography = MaterialTheme.typography
            }
        }

        assertNotNull(typography)
        val resolvedTypography = checkNotNull(typography)

        // Title Large (22sp / 28sp Medium)
        assertEquals(22.sp, resolvedTypography.titleLarge.fontSize)
        assertEquals(28.sp, resolvedTypography.titleLarge.lineHeight)
        assertEquals(FontWeight.Medium, resolvedTypography.titleLarge.fontWeight)
        assertEquals(FontFamily.SansSerif, resolvedTypography.titleLarge.fontFamily)

        // Headline Medium (22sp / 28sp Medium)
        assertEquals(22.sp, resolvedTypography.headlineMedium.fontSize)
        assertEquals(28.sp, resolvedTypography.headlineMedium.lineHeight)
        assertEquals(FontWeight.Medium, resolvedTypography.headlineMedium.fontWeight)
        assertEquals(FontFamily.SansSerif, resolvedTypography.headlineMedium.fontFamily)

        // Title Medium (18sp / 24sp Medium)
        assertEquals(18.sp, resolvedTypography.titleMedium.fontSize)
        assertEquals(24.sp, resolvedTypography.titleMedium.lineHeight)
        assertEquals(FontWeight.Medium, resolvedTypography.titleMedium.fontWeight)
        assertEquals(FontFamily.SansSerif, resolvedTypography.titleMedium.fontFamily)

        // Body Large & Body Medium (16sp / 24sp Regular)
        assertEquals(16.sp, resolvedTypography.bodyLarge.fontSize)
        assertEquals(24.sp, resolvedTypography.bodyLarge.lineHeight)
        assertEquals(FontWeight.Normal, resolvedTypography.bodyLarge.fontWeight)
        assertEquals(FontFamily.SansSerif, resolvedTypography.bodyLarge.fontFamily)

        assertEquals(16.sp, resolvedTypography.bodyMedium.fontSize)
        assertEquals(24.sp, resolvedTypography.bodyMedium.lineHeight)
        assertEquals(FontWeight.Normal, resolvedTypography.bodyMedium.fontWeight)
        assertEquals(FontFamily.SansSerif, resolvedTypography.bodyMedium.fontFamily)

        // Label Large & Label Medium (14sp / 20sp Medium)
        assertEquals(14.sp, resolvedTypography.labelLarge.fontSize)
        assertEquals(20.sp, resolvedTypography.labelLarge.lineHeight)
        assertEquals(FontWeight.Medium, resolvedTypography.labelLarge.fontWeight)
        assertEquals(FontFamily.SansSerif, resolvedTypography.labelLarge.fontFamily)

        assertEquals(14.sp, resolvedTypography.labelMedium.fontSize)
        assertEquals(20.sp, resolvedTypography.labelMedium.lineHeight)
        assertEquals(FontWeight.Medium, resolvedTypography.labelMedium.fontWeight)
        assertEquals(FontFamily.SansSerif, resolvedTypography.labelMedium.fontFamily)

        // Body Small & Label Small (12sp / 16sp Regular)
        assertEquals(12.sp, resolvedTypography.bodySmall.fontSize)
        assertEquals(16.sp, resolvedTypography.bodySmall.lineHeight)
        assertEquals(FontWeight.Normal, resolvedTypography.bodySmall.fontWeight)
        assertEquals(FontFamily.SansSerif, resolvedTypography.bodySmall.fontFamily)

        assertEquals(12.sp, resolvedTypography.labelSmall.fontSize)
        assertEquals(16.sp, resolvedTypography.labelSmall.lineHeight)
        assertEquals(FontWeight.Normal, resolvedTypography.labelSmall.fontWeight)
        assertEquals(FontFamily.SansSerif, resolvedTypography.labelSmall.fontFamily)
    }
}
