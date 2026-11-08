# core:data

## Overview
Offline-first data repositories, real-time sync coordinator (`ChatRealtimeSyncManager`), WebSocket lifecycle manager (`RealtimeConnectionManagerImpl`), and domain mappers.

## Interface
- `[AuthRepository.kt:AuthRepository]`: Repository interface for registration, login, and logout returning `Either<AuthError, User>` and `getAuthState(): Flow<AuthState>`.
- `[ChatRoomRepository.kt:ChatRoomRepository]`: Repository interface for observing rooms, fetching room metadata, creating direct/group chats, syncing rooms, adding participants, and leaving rooms.
- `[ChatMessageRepository.kt:ChatMessageRepository]`: Repository interface for observing messages, optimistic message dispatch, cursor pagination, message retries, and soft deletion.
- `[RealtimeConnectionManager.kt:RealtimeConnectionManager]`: Real-time engine supervising WebSocket connections based on app lifecycle (`ProcessLifecycleOwner`) and `AuthState`.
- `[ChatRealtimeSyncManager.kt:ChatRealtimeSyncManager]`: Reactive consumer processing incoming `WsServerEvent` frames and applying updates to `ChatDao` (with foreign-key buffering guard).
- `[DataModule.kt:DataModule]`: Hilt DI module binding repository and sync manager implementations.

## Invariants
- Repository implementations and data mappers should be `internal` to encapsulate persistence and network models.
- Messages are optimistically inserted into SQLite with `SENDING` status before network dispatch.
- WebSocket reconnection implements exponential backoff with jitter (`RealtimeConnectionManagerImpl`).
- Out-of-order message frames for uncached rooms are buffered (up to 50 per room) until room metadata is fetched to prevent SQLite foreign key constraint crashes (`ChatRealtimeSyncManager`).

## Side Effects
- Reads and mutates Room database (`ChatDao`).
- Triggers network requests and WebSocket frames via `:core:network`.
- Observes Android process foreground/background lifecycle.

## Verification
- Run: `./gradlew :core:data:testDebugUnitTest --rerun-tasks`
