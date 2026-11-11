# core:ui

## 1. Overview

Shared Jetpack Compose design system foundations, application semantic color tokens, shapes,
typography, and common UI helpers conforming to the *Quiet Messaging* design system specification
in [`../../docs/designs/design-system.md`](../../docs/designs/design-system.md) (MCP Stitch
`projects/10647833259558812227`).

---

## 2. Design System Specification (Quiet Messaging SSOT)

### 2.1 Visual Direction

Android-first text messaging. Quiet, compact, readable. White canvas, soft gray incoming messages,
deep teal outgoing messages. Flat surfaces; no gradients, glass, decorative illustrations, or
persistent bottom navigation. Chats is the home screen. Light theme only for the initial design.

### 2.2 Semantic Color Tokens

All UI components use these semantic color tokens; never introduce component-specific ad-hoc colors:

| Token                           | Hex       | Role & Usage                                                               |
|---------------------------------|-----------|----------------------------------------------------------------------------|
| `background` (`ChatBackground`) | `#FFFFFF` | Screen background and app bar container                                    |
| `surface` (`ChatSurface`)       | `#F3F4F6` | Incoming message bubbles, composer input bar, avatar fallback background   |
| `text` (`ChatText`)             | `#111827` | Primary high-contrast text and headings                                    |
| `text-muted` (`ChatTextMuted`)  | `#4B5563` | Timestamps, metadata, hints, secondary labels                              |
| `border` (`ChatBorder`)         | `#D1D5DB` | Inputs, text field outlines, essential dividers                            |
| `primary` (`ChatPrimary`)       | `#0F766E` | Outgoing message bubbles, primary action buttons, active focus outlines    |
| `on-primary` (`ChatOnPrimary`)  | `#FFFFFF` | Text and icons placed on primary-colored containers                        |
| `error` (`ChatError`)           | `#B91C1C` | Failed message delivery indicators, error status text, destructive actions |

- **Quiet Connected State**: Connected state stays quiet (no green presence dots or persistent
  connection banners).
- **Surface Elevation**: Flat elevation (`0.dp`) across all custom components.

### 2.3 Geometry, Shapes & Typography

| Dimension / Token          | Value                    | Target & Usage                                                      |
|----------------------------|--------------------------|---------------------------------------------------------------------|
| `radius.control` (`small`) | `12.dp`                  | Buttons, text inputs, search bar, card containers                   |
| `radius.bubble` (`medium`) | `16.dp`                  | Message bubbles (rounded rectangular, no tails)                     |
| `radius.avatar` (`large`)  | `CircleShape`            | Circular avatars (40dp in list, 32dp in conversation headers/items) |
| `touch target`             | `48 × 48 dp`             | Minimum interactive touch target for all buttons and icon buttons   |
| `stroke`                   | `1.dp`                   | Outlines and essential borders                                      |
| `typeface`                 | `Roboto` / Platform Sans | Medium for headers/titles, Regular for body/captions                |
| `title`                    | `22sp / 28sp`            | Screen titles (`Chats`, `Conversation`, `Account`)                  |
| `heading`                  | `18sp / 24sp`            | Section headers and dialog titles                                   |
| `body`                     | `16sp / 24sp`            | Message text and primary form inputs                                |
| `label`                    | `14sp / 20sp`            | Action button labels                                                |
| `caption`                  | `12sp / 16sp`            | Timestamps, status bar banners, sublabels                           |

---

## 3. Module Interface

### 3.1 Theme & Design Tokens

- `[Theme.kt:ChatTheme]`: Root Compose theme wrapper mapping `QuietMessagingLightColorScheme`,
  `QuietMessagingShapes`, and `Typography` into Material 3.
- `[Color.kt]`: Canonical definitions for `ChatBackground`, `ChatSurface`, `ChatText`,
  `ChatTextMuted`, `ChatBorder`, `ChatPrimary`, `ChatOnPrimary`, `ChatError`.
- `[Shape.kt:QuietMessagingShapes]`: Material 3 `Shapes` specification defining `small` (12dp),
  `medium` (16dp), and `large` (CircleShape).
- `[Type.kt:Typography]`: Material 3 typography scale matching platform sans-serif.

### 3.2 Common UI Components & Helpers

- `[DataErrorToString.kt:asUiText]`: Extension function mapping domain `DataError` models to
  localized UI text.
- `[rememberMinLoading.kt:rememberMinLoading]`: Compose state utility ensuring minimum loading indicator duration to prevent UI flickering.

---

## 4. Invariants
- Depends exclusively on `:core:model`.
- Must never depend on `:core:network`, `:core:database`, `:core:datastore`, `:sync:work`, or
  `:core:data` repository implementations.
- All presentation feature modules (`:feature:chat`, `:feature:auth`) and `:app` consume design
  tokens and components exclusively through `ChatTheme` and public `:core:ui` symbols.

---

## 5. Side Effects

- None (pure Compose rendering components and design tokens).

---

## 6. Verification

- Fast inner-loop unit tests: `./gradlew :core:ui:testDebugUnitTest --rerun-tasks`
