package io.github.neronguyen.chat.core.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neronguyen.chat.core.ui.theme.ChatBorder
import io.github.neronguyen.chat.core.ui.theme.ChatError
import io.github.neronguyen.chat.core.ui.theme.ChatPrimary
import io.github.neronguyen.chat.core.ui.theme.ChatSurface
import io.github.neronguyen.chat.core.ui.theme.ChatText
import io.github.neronguyen.chat.core.ui.theme.ChatTheme

private const val DEFAULT_CELL_COUNT = 6
private val CellWidth = 48.dp
private val CellHeight = 56.dp
private val CellCornerRadius = 12.dp

/**
 * Segmented 6-digit numeric OTP input field conforming to the NeroChat "Quiet Messaging" design system.
 *
 * Renders 6 discrete cells (`48.dp` x `56.dp`) backed by a single hidden [BasicTextField].
 * Disables autocomplete, password manager prompts, and non-numeric inputs.
 *
 * @param value Current 0..6 numeric string.
 * @param onValueChange Callback invoked when the user types or pastes characters, filtered to digits.
 * @param modifier Outer layout modifier.
 * @param cellCount Number of segmented OTP cells (defaults to 6).
 * @param isError Whether to render error border tokens.
 * @param enabled Whether input interactions are enabled.
 * @param onComplete Optional callback automatically invoked when all [cellCount] digits are entered.
 */
@Composable
fun OtpInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    cellCount: Int = DEFAULT_CELL_COUNT,
    isError: Boolean = false,
    enabled: Boolean = true,
    onComplete: ((String) -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    val cursorAlpha = if (LocalInspectionMode.current) {
        1f
    } else {
        val cursorTransition = rememberInfiniteTransition(label = "OtpCursorTransition")
        val animatedAlpha by cursorTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "OtpCursorAlpha",
        )
        animatedAlpha
    }

    val sanitizedValue = value.filter { it.isDigit() }.take(cellCount)

    LaunchedEffect(sanitizedValue) {
        if (sanitizedValue.length == cellCount) {
            onComplete?.invoke(sanitizedValue)
        }
    }

    Box(
        modifier = modifier
            .testTag("otp_input_field")
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "Verification code, ${sanitizedValue.length} of $cellCount digits entered"
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) {
                focusRequester.requestFocus()
            },
        contentAlignment = Alignment.Center,
    ) {
        // Hidden single BasicTextField managing keyboard state and focus
        BasicTextField(
            value = sanitizedValue,
            onValueChange = { input ->
                val digitsOnly = input.filter { it.isDigit() }.take(cellCount)
                onValueChange(digitsOnly)
            },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (sanitizedValue.length == cellCount) {
                        onComplete?.invoke(sanitizedValue)
                    }
                },
            ),
            cursorBrush = SolidColor(ChatPrimary),
            modifier = Modifier
                .size(1.dp)
                .alpha(0.01f)
                .focusRequester(focusRequester),
        )

        // Visual segmented cells row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (index in 0 until cellCount) {
                val digit = sanitizedValue.getOrNull(index)?.toString() ?: ""
                val isCurrent = enabled && (index == sanitizedValue.length)

                OtpCell(
                    digit = digit,
                    isCurrent = isCurrent,
                    isError = isError,
                    cursorAlpha = if (isCurrent) cursorAlpha else 0f,
                    index = index,
                )
            }
        }
    }
}

@Composable
private fun OtpCell(
    digit: String,
    isCurrent: Boolean,
    isError: Boolean,
    cursorAlpha: Float,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        isError -> ChatError
        isCurrent -> ChatPrimary
        digit.isNotEmpty() -> ChatPrimary.copy(alpha = 0.6f)
        else -> ChatBorder
    }
    val borderWidth = if (isCurrent || isError) 2.dp else 1.dp

    Box(
        modifier = modifier
            .width(CellWidth)
            .height(CellHeight)
            .testTag("otp_cell_$index")
            .background(ChatSurface, RoundedCornerShape(CellCornerRadius))
            .border(borderWidth, borderColor, RoundedCornerShape(CellCornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        if (digit.isNotEmpty()) {
            Text(
                text = digit,
                color = if (isError) ChatError else ChatText,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        } else if (isCurrent) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .alpha(cursorAlpha)
                    .background(ChatPrimary),
            )
        }
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
fun OtpInputFieldEmptyPreview() = ChatTheme {
    OtpInputField(
        value = "",
        onValueChange = {},
    )
}

@Preview(name = "Partial", showBackground = true)
@Composable
fun OtpInputFieldPartialPreview() = ChatTheme {
    OtpInputField(
        value = "123",
        onValueChange = {},
    )
}

@Preview(name = "Full", showBackground = true)
@Composable
fun OtpInputFieldFullPreview() = ChatTheme {
    OtpInputField(
        value = "123456",
        onValueChange = {},
    )
}

@Preview(name = "Error", showBackground = true)
@Composable
fun OtpInputFieldErrorPreview() = ChatTheme {
    OtpInputField(
        value = "123456",
        onValueChange = {},
        isError = true,
    )
}
