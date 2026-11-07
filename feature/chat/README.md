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
- Depends only on `:core:ui`, `:core:model`, `:core:data` repository interfaces, and `:validation:chat`.
- Never imports wire DTOs (`:contract:*`) or database entities.
- Validates room creation and participant searches synchronously via `ChatValidator`.
- Exposes deterministic `testTag` identifiers for headless Robolectric testing.

## Side Effects
- Mutates UI state flows; triggers message transmissions and room queries via `ChatRoomRepository` and `ChatMessageRepository`.

## Verification
- Run: `./gradlew :feature:chat:testDebugUnitTest --rerun-tasks`
