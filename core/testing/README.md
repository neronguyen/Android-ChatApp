# core:testing

## Overview
Test-only shared fakes and deterministic domain fixtures for unit, feature, navigation, worker, and `:app` Hilt integration tests.

## Interface
- `[FakeAuthRepository.kt:FakeAuthRepository]`: Configurable in-memory `AuthRepository` fake with call recording and reactive `authStateFlow`.
- `[FakeChatRoomRepository.kt:FakeChatRoomRepository]`: Configurable in-memory `ChatRoomRepository` fake with reactive `roomsFlow` and call recording.
- `[FakeChatMessageRepository.kt:FakeChatMessageRepository]`: Configurable in-memory `ChatMessageRepository` fake with reactive `messagesFlow` and call recording.
- `[FakeChatSyncManager.kt:FakeChatSyncManager]`: Configurable `ChatSyncManager` fake exposing mutable `syncStateFlow` and call counters.
- `[TestFixtures.kt:TestFixtures]`: Canonical domain builders (`user`, `participant`, `directRoom`, `groupRoom`, `message`) for test suites.

## Invariants
- Consumed strictly via `testImplementation(projects.core.testing)` — **never** imported or depended upon in any `src/main` source set (enforced by `ArchitectureKonsistTest`).
- Contains zero production Hilt `@Provides` / `@Binds` modules so tests opt in explicitly via `@TestInstallIn` or direct constructor injection.

## Side Effects
- None (pure in-memory `MutableStateFlow` state).

## Verification
- Run: `./gradlew :core:testing:assembleDebug`
