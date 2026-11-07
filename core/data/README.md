# core:data

## Overview
Offline-first data repositories, real-time sync coordinator (`ChatRealtimeSyncManager`), WebSocket lifecycle manager (`RealtimeConnectionManagerImpl`), WorkManager background sync, and domain mappers.

## Interface
- `[AuthRepository.kt:AuthRepository]`: Repository interface for registration, login, and logout returning `Either<DomainError, T>`.
- `[ChatRoomRepository.kt:ChatRoomRepository]`: Repository interface for observing rooms, fetching room metadata, creating direct/group chats, and syncing rooms.
- `[ChatMessageRepository.kt:ChatMessageRepository]`: Repository interface for observing messages, optimistic message dispatch, and cursor pagination.
- `[RealtimeConnectionManager.kt:RealtimeConnectionManager]`: Real-time engine supervising WebSocket connections based on app lifecycle (`ProcessLifecycleOwner`) and `AuthState`.
- `[ChatRealtimeSyncManager.kt:ChatRealtimeSyncManager]`: Reactive consumer processing incoming `WsServerEvent` frames and applying updates to `ChatDao` (with foreign-key buffering guard).
- `[ChatSyncManager.kt:ChatSyncManager]`: Reconnection and background synchronization coordinator driving catch-up sync.
- `[ChatSyncScheduler.kt:ChatSyncScheduler]`: WorkManager scheduler interface for periodic and one-time catch-up sync jobs.
- `[DataModule.kt:DataModule]`: Hilt DI module binding repository and sync manager implementations.

## Invariants
- Repository implementations and data mappers should be `internal` to encapsulate persistence and network models.
- Messages are optimistically inserted into SQLite with `SENDING` status before network dispatch.
- WebSocket reconnection implements exponential backoff with jitter.
- Out-of-order message frames for uncached rooms are buffered (up to 50 per room) until room metadata is fetched to prevent SQLite foreign key constraint crashes.
- `[BASELINE-DEBT]`: `ChatRoomRepository` currently exposes ad-hoc UI event streams and WorkManager constants (scheduled for refactoring in Track 1).

## Side Effects
- Reads and mutates Room database (`ChatDao`).
- Triggers network requests and WebSocket frames via `:core:network`.
- Enqueues WorkManager background jobs (`ChatSyncWorker`).
- Observes Android process foreground/background lifecycle.

## Verification
- Run: `./gradlew :core:data:testDebugUnitTest --rerun-tasks`
