# feature:chat

## Overview
Chat list, conversation messaging, new chat creation, and account settings user experiences built with Jetpack Compose.

## Interface
- `[ChatListScreen.kt:ChatListScreen]`: Compose screen displaying real-time list of direct and group chat rooms.
- `[ChatListViewModel.kt:ChatListViewModel]`: ViewModel observing reactive room flows and background sync status.
- `[ConversationScreen.kt:ConversationScreen]`: Real-time chat screen supporting optimistic message delivery, failure retries, and cursor pagination.
- `[ConversationViewModel.kt:ConversationViewModel]`: ViewModel managing message sending, socket state reconciliation, and history fetching.
- `[NewChatScreen.kt:NewChatScreen]`: Participant search and room creation screen.
- `[NewChatViewModel.kt:NewChatViewModel]`: ViewModel managing participant search with debounce and room creation requests.
- `[AccountScreen.kt:AccountScreen]`: User profile and session management screen.
- `[AccountViewModel.kt:AccountViewModel]`: ViewModel handling account display and logout dispatch.
- `[ChatNavigation.kt:ChatNavGraph]`: Navigation 3 entrypoint integrating chat routes into the app navigation host.

## Invariants
- Depends only on `:core:ui`, `:core:model`, `:core:data` interfaces (`ChatRoomRepository`, `ChatMessageRepository`, `ChatSyncManager`), and `:validation:chat`.
- Never imports wire DTOs (`:contract:*`) or database entities.
- Consumes visual tokens exclusively via `MaterialTheme.colorScheme`, `MaterialTheme.typography`,
  and `MaterialTheme.shapes` (never hardcoded `Color(...)` literals or raw `Chat*` color constants).
- Every `*Screen.kt` exposes `internal` `@Preview` functions wrapped in `ChatTheme` reused by
  `src/screenshotTest`.
- Validates room creation and participant searches synchronously via `ChatValidator`.
- Consumes unified `ChatSyncState` via `ChatSyncManager` across `ChatListViewModel`, `ConversationViewModel`, and `AccountViewModel`; embeds `SyncStatusBar` for reachability/sync feedback.
- Gates message transmission on `canSendMessage == (syncState is Synced || syncState is Syncing)` while preserving editable user input in `TextFieldState`.
- Preserves banner dismissal across positive recovery (`Offline` -> `Connecting` -> `Syncing` -> `Synced`) and resets strictly on negative regressions (`Failed` or unexpected `Offline`).
- Exposes deterministic `testTag` identifiers (`conversation_send_button`, `sync_status_bar`,
  `new_chat_*`, `account_*`) across all chat screens covered by Robolectric `*ScreenTest` suites
  (`ChatListScreenTest`, `ConversationScreenTest`, `NewChatScreenTest`, `AccountScreenTest`).

## Side Effects
- Mutates UI state flows; triggers message transmissions and room queries via `ChatRoomRepository` and `ChatMessageRepository`.

## Verification

- Unit & Robolectric UI tests: `./gradlew :feature:chat:testDebugUnitTest --rerun-tasks`
- Validate screenshots against references:
  `./gradlew :feature:chat:testDebugScreenshotTestDefaultTestSuite`
- Update screenshot reference images:
  `./gradlew :feature:chat:updateDebugScreenshotTestDefaultTestSuite`
