# Design System — Quiet Messaging

| Metadata           | Value                                                |
|--------------------|------------------------------------------------------|
| **Author**         | Nero Nguyen                                          |
| **Status**         | Implemented                                          |
| **Date**           | 2026-11-11                                           |
| **Role**           | Repository Living Visual SSOT                        |
| **Target Modules** | `:core:ui`, `:feature:chat`, `:feature:auth`, `:app` |

---

## Overview

This technical design document is the living Single Source of Truth (SSOT) for the NeroChat **Quiet
Messaging** design system in `Android-ChatApp`. It establishes a compact, distraction-free Android
chat interface centered around flat canvas aesthetics, high-contrast typography, and strict semantic
color tokens.

The design system is centralized within `:core:ui` as the single visual foundation for the entire
client codebase, ensuring presentation consistency across authentication and conversation flows
without per-module style fragmentation.

---

## The Problem

1. **Token Fragmentation & Visual Drift**:
   Prior feature implementations relied on hardcoded color constants, ad-hoc hex values, and
   component-local styles in feature modules. This created visual inconsistencies between
   `:feature:auth`, `:feature:chat`, and root entry points.
2. **Interface Distraction & Noise**:
   Displaying persistent presence dots, permanent status banners, and blocking loading spinners
   degrades messaging readability. The product requirements mandate rendering cached conversation
   state immediately while omitting non-actionable presence and connection indicators.
3. **Specification Coupling**:
   Previous design documentation combined foundational visual tokens with dynamic, feature-specific
   screen component layouts (top app bars, composer bars, and sync status strips). This bloated the
   design system specification and coupled token governance with feature slice lifecycles.

---

## Goals

1. **Standardize Semantic Color Tokens**:
   Define an immutable 11-token semantic palette (`ChatBackground`, `ChatSurface`, `ChatText`,
   `ChatTextMuted`, `ChatBorder`, `ChatPrimary`, `ChatOnPrimary`, `ChatError`, `ChatOnError`,
   `ChatErrorContainer`, `ChatOnErrorContainer`) in `:core:ui`.
2. **Standardize Geometry & Scale Tokens**:
   Standardize shape radii (`radius.control` 12dp, `radius.bubble` 16dp, `radius.avatar`
   CircleShape), stroke weights (1dp), and minimum accessible touch targets (48×48dp).
3. **Platform Typography Mapping**:
   Establish a clear Roboto/Sans-serif typography hierarchy from `title` (22sp) down to `caption`
   (12sp) mapped to Material 3 `Typography`, including `headlineMedium` for screen headers.
4. **Centralize Foundations in `:core:ui`**:
   Provide `ChatTheme` in `:core:ui` so that `:feature:chat`, `:feature:auth`, and `:app` consume
   theme tokens consistently through the Compose `MaterialTheme` hierarchy without circular
   dependencies.
5. **Enforce Quiet Messaging Invariants**:
   Strictly forbid persistent green presence dots, ornamental gradients, glassmorphism, or non-zero
   surface elevation.

---

## Non-Goals

1. **Component Layout Specification**:
   Detailed component layouts (e.g., `SyncStatusBar`, `ChatMessageItem`, `ChatRoomRow`,
   `ComposerBar`) belong to their respective functional feature slices
   (`docs/designs/presentation-sync-ui-integration.md`, etc.) and are excluded from this design
   system specification.
2. **Dark Theme Implementation**:
   Initial implementation is light theme only. Dark theme palette tokens will be defined in a
   separate future iteration.
3. **Dynamic / Wallpaper Customization**:
   User-customizable chat bubble colors, custom fonts, or chat wallpapers are out of scope.
4. **Multiplatform Theming**:
   Jetpack Compose multiplatform bindings (Desktop/iOS) are deferred to future milestones.

---

## Critical User Journeys (CUJs)

### CUJ 1: Consistent Themed Screen Rendering

* **Given**: A user navigates across screens (login, chat list, conversation, account settings).
* **When**: Each screen is rendered by Jetpack Compose wrapped in `ChatTheme`.
* **Then**: Canvas background resolves to flat `#FFFFFF`, message surfaces resolve to `#F3F4F6`
  (incoming) and `#0F766E` (outgoing) via `MaterialTheme.colorScheme`, and all interactive controls
  provide at least 48×48dp touch targets.

### CUJ 2: Quiet Connected State Experience

* **Given**: The user has active WebSocket connectivity and synced data.
* **When**: The chat list or conversation screen is displayed.
* **Then**: The UI renders without presence indicators, permanent status badges, or blocking loading
  screens.

---

## Technical Plan (Architecture)

### 1. Architectural Principles

1. **Silent Operational State**:
   When active and synchronized, operational state is silent: no presence badges or full-screen
   loading spinners appear while cached data is rendered.
2. **Flat Surface Aesthetic**:
   The canvas uses `0.dp` elevation across all surfaces. To prevent Material 3 from introducing
   unwanted tonal elevation tints, `surfaceTint` is explicitly mapped to `Color.Transparent`.
   Gradients, glassmorphism effects, shadows, and ornamental illustrations are prohibited.
3. **Strict Contrast & Legibility**:
   Hierarchy is established through typography size and distinct semantic color tokens (`ChatText`
   vs. `ChatTextMuted`), never by lowering text opacity or alpha. Contrast ratios satisfy WCAG AA
   standards (minimum 4.5:1 for body text).
4. **System Bar Edge-to-Edge Contract**:
   On modern Android edge-to-edge canvases, `ChatTheme` configures
   `isAppearanceLightStatusBars = true` and `isAppearanceLightNavigationBars = true` so that
   platform system icons (clock, battery, navigation glyphs) render in high-contrast dark gray over
   the `#FFFFFF` canvas.

---

### 2. Semantic Color Tokens

All client UI components across `:core:ui`, `:feature:chat`, `:feature:auth`, and `:app` consume
styling through `MaterialTheme.colorScheme.*`. Top-level constants in
`io.github.neronguyen.chat.core.ui.theme.Color` define the underlying palette:

| Token                  | Hex       | Material 3 Role Mapping                                                              | Usage                                                                   |
|------------------------|-----------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| `ChatBackground`       | `#FFFFFF` | `background`, `surface`, `surfaceContainerLowest`                                    | Screen canvas, app bar container                                        |
| `ChatSurface`          | `#F3F4F6` | `surfaceVariant`, `secondaryContainer`, `surfaceContainerLow/Container/High/Highest` | Incoming message bubbles, input containers, avatar fallback background  |
| `ChatText`             | `#111827` | `onBackground`, `onSurface`, `onSecondaryContainer`                                  | Primary high-contrast text, headings, message body                      |
| `ChatTextMuted`        | `#4B5563` | `secondary`, `onSurfaceVariant`                                                      | Timestamps, metadata labels, search hints, secondary labels             |
| `ChatBorder`           | `#D1D5DB` | `outline`, `outlineVariant`                                                          | Text field outlines, dividers, subtle borders                           |
| `ChatPrimary`          | `#0F766E` | `primary`, `primaryContainer`                                                        | Outgoing message bubbles, primary action buttons, active focus outlines |
| `ChatOnPrimary`        | `#FFFFFF` | `onPrimary`, `onPrimaryContainer`, `onSecondary`                                     | Text and icons placed on top of `ChatPrimary` surfaces                  |
| `ChatError`            | `#B91C1C` | `error`                                                                              | Failed delivery indicators, destructive buttons, error status text      |
| `ChatOnError`          | `#FFFFFF` | `onError`                                                                            | Text and icons placed on top of `ChatError` surfaces                    |
| `ChatErrorContainer`   | `#FFDAD6` | `errorContainer`                                                                     | Background container for subtle error banners                           |
| `ChatOnErrorContainer` | `#93000A` | `onErrorContainer`                                                                   | High-contrast text on error containers                                  |

#### Neutralized Framework Roles

To eliminate default Google baseline purple (`#6750A4`) leaks and maintain flat styling:

- `surfaceTint = Color.Transparent` (strictly disables M3 tonal surface elevation tinting).
- `tertiary = ChatPrimary`, `onTertiary = ChatOnPrimary`.
- `tertiaryContainer = ChatSurface`, `onTertiaryContainer = ChatText`.
- `inverseSurface = ChatText`, `inverseOnSurface = ChatBackground`,
  `inversePrimary = ChatOnPrimary`.
- `scrim = Color(0x52000000)`.

#### Color Invariants

- Deep Teal (`#0F766E`) is strictly an interactive action and outbound bubble color; it must never
  be used to represent an online presence indicator.
- Feature composables read colors from `MaterialTheme.colorScheme` to ensure theme adaptability.
  Direct imports of static `Color.kt` constants are restricted to `:core:ui` theme wiring.

---

### 3. Geometry, Shapes & Dimensions

#### 3.1 Shape Tokens & Material 3 Decoupling

To avoid corrupting standard Material 3 large surface containers (modal sheets, cards, drawers),
avatar circular geometry is decoupled from `Shapes.large`:

- `radius.control` (`small = RoundedCornerShape(12.dp)`): Rounded corners for buttons, search bars,
  text inputs, and dialog actions.
- `radius.bubble` (`medium = RoundedCornerShape(16.dp)`): Rounded rectangular message bubbles with
  symmetric corners. **Speech bubble tails are prohibited.**
- `Shapes.large` (`large = RoundedCornerShape(16.dp)`): Standard surface container shape for dialogs
  and sheets.
- `AvatarShape` (`CircleShape`): Dedicated domain shape token in `Shape.kt` for user and group
  avatars, applied directly at avatar call sites.
- `stroke` (`1.dp`): Subtle borders and dividers.

#### 3.2 Dimensions & Touch Targets

- **Minimum Interactive Touch Target**: `48 × 48 dp` for all interactive elements (buttons, icon
  buttons, clickable rows). Enforced using `Modifier.minimumInteractiveComponentSize()` or component
  internal padding.
- **Avatar Dimensions**: `40.dp` for conversation list rows; `32.dp` for conversation headers and
  group message bubbles.
- **Icon Sizing**: `24.dp` standard across app bars and actions, using outlined icon variants
  (`Icons.Outlined`).

---

### 4. Typography Scale

The typography scale maps directly to the platform sans-serif typeface (Roboto) in
`io.github.neronguyen.chat.core.ui.theme.Type`:

| Scale Name | Style / Weight | Size / Line Height | Semantic Usage                                     | Material 3 Mapping                      |
|------------|----------------|--------------------|----------------------------------------------------|-----------------------------------------|
| `title`    | Medium (500)   | 22sp / 28sp        | Screen titles (`Chats`, `Conversation`, `Account`) | `Typography.titleLarge`                 |
| `headline` | Medium (500)   | 22sp / 28sp        | Screen headers in auth flows (`Login`, `Register`) | `Typography.headlineMedium`             |
| `heading`  | Medium (500)   | 18sp / 24sp        | Section headers, dialog titles                     | `Typography.titleMedium`                |
| `body`     | Regular (400)  | 16sp / 24sp        | Message bubble body, primary text field input      | `Typography.bodyLarge` / `bodyMedium`   |
| `label`    | Medium (500)   | 14sp / 20sp        | Action buttons, chip labels                        | `Typography.labelLarge` / `labelMedium` |
| `caption`  | Regular (400)  | 12sp / 16sp        | Timestamps, metadata, status bar captions          | `Typography.bodySmall` / `labelSmall`   |

---

### 5. Architectural Topology & Theme Encapsulation

The design system foundations are centralized in `:core:ui`. Both feature modules and `:app` depend
directly on `:core:ui`:

```text
               +----------------------------------+
               |             :core:ui             |
               |  [Color.kt] [Shape.kt] [Type.kt] |
               |                |                 |
               |                v                 |
               |      [Theme.kt: ChatTheme]       |
               +----------------+-----------------+
                                |
        +-----------------------+-----------------------+
        |                       |                       |
        v                       v                       v
 [:feature:chat]         [:feature:auth]              [:app]
(ChatList, Conver.)     (Login, Register)         (MainActivity)
```

#### Theme Mapping Contract

```kotlin
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
```

---

## Alternatives Considered

### Alternative A: Material You / Dynamic Colors (System Wallpaper Theming)

* **Pros**: Automatic alignment with user system-level Android 12+ wallpaper palettes.
* **Cons**: Unpredictable contrast ratios across arbitrary user wallpapers; dilutes the calm,
  text-first *Quiet Messaging* brand identity; conflicts with the deterministic semantic role of
  deep teal (`#0F766E`) for actions and soft gray (`#F3F4F6`) for incoming messages.
* **Reason for Rejection**: Dynamic wallpaper theming introduces nondeterministic contrast ratios
  and breaks fixed semantic assignments for action and message bubble roles.

### Alternative B: Retaining Screen Component Layouts Inside the Design System Document

* **Pros**: Consolidates all UI-related specifications into a single large document.
* **Cons**: Violates separation of concerns. Causes documentation bloat; blurs boundaries between
  static design tokens and dynamic state lifecycles (e.g. `SyncStatusBar` connection state
  transitions, message delivery status reconciliation, and composer optimistic sending).
* **Reason for Rejection**: Foundational design system specifications must strictly govern visual
  tokens and theme contracts. Stateful component layouts belong to their respective functional slice
  designs (`docs/designs/presentation-sync-ui-integration.md`).

### Alternative C: Ad-Hoc Component-Level Colors in Feature Modules

* **Pros**: Quick local styling during feature prototyping without cross-module coordination.
* **Cons**: Leads to visual inconsistencies, untracked color drift, duplicated hex literals across
  `:feature:auth` and `:feature:chat`, and breaks global theme refactoring.
* **Reason for Rejection**: Direct top-level constant imports in feature modules prevent runtime
  theme switching and create immediate refactoring barriers when introducing dark mode or
  high-contrast themes.

