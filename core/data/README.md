# core:data

## Overview
Offline-first data repositories, real-time sync coordinator (`ChatRealtimeSyncManager`), WebSocket lifecycle manager (`RealtimeConnectionManagerImpl`), network reachability monitor (`ConnectivityManagerNetworkMonitor`), catch-up sync coordinator (`ChatSyncManagerImpl`), and domain mappers.

## Interface
- `[AuthRepository.kt:AuthRepository]`: Repository interface for registration, login, and logout returning `Either<AuthError, User>` and `getAuthState(): Flow<AuthState>`.
- `[ChatRoomRepository.kt:ChatRoomRepository]`: Repository interface for observing rooms, fetching room metadata, creating direct/group chats, syncing rooms, atomic single-flight room reconciliation (`syncChatsAndGetChangedRooms()`), adding participants, and leaving rooms.
- `[ChatMessageRepository.kt:ChatMessageRepository]`: Repository interface for observing messages, optimistic message dispatch (`sendMessage()`, `retryMessage()` returning `Either<ChatError.SendMessage, Unit>`), cursor pagination, and soft deletion.
- `[RealtimeConnectionManager.kt:RealtimeConnectionManager]`: Real-time engine supervising WebSocket connections based on app lifecycle (`ProcessLifecycleOwner`), network reachability (`NetworkMonitor`), and `AuthState`.
- `[ChatRealtimeSyncManager.kt:ChatRealtimeSyncManager]`: Reactive consumer processing incoming `WsServerEvent` frames and applying updates to `ChatDao` (with foreign-key buffering guard, disconnect sweep, and idempotent duplicate message reconciliation).
- `[NetworkMonitor.kt:NetworkMonitor]`: Network reachability interface with cold-start seeding and asymmetric 300ms offline debounce.
- `[ChatSyncManager.kt:ChatSyncManager]`: Coordinates catch-up sync with bounded parallel prefetching (Semaphore 3) and exposes reactive `syncState: StateFlow<ChatSyncState>`.
- `[ChatSyncScheduler.kt:ChatSyncScheduler]`: SPI abstraction for edge background scheduling implemented by `:sync:work`.
- `[DataModule.kt:DataModule]`: Hilt DI module binding repository, network monitor, and sync manager implementations.

## Invariants
- Repository implementations and data mappers must be `internal` to encapsulate persistence and network models.
- Zero background WorkManager dependencies in `:core:data`; background scheduling is delegated to `:sync:work` via `ChatSyncScheduler` SPI.
- Single-flight coroutine coalescing in `ChatRoomRepositoryImpl` uses `inFlightSync?.takeIf { it.isActive }` with decoupled completion cleanup to prevent stale reuse and isolation hazards.
- Messages are optimistically inserted into SQLite with `SENDING` status before network dispatch.
- WebSocket reconnection implements exponential backoff with jitter (`RealtimeConnectionManagerImpl`), triggering 0ms reconnect attempt on online restoration while preserving retry counter until `Connected`.
- Out-of-order message frames for uncached rooms are buffered (up to 50 per room) until room metadata is fetched to prevent SQLite foreign key constraint crashes (`ChatRealtimeSyncManager`).
- Socket disconnect sweeps pending `SENDING` messages to `FAILED`; server duplicate message events reconcile to `SENT`.
- Catch-up sync prefetching across dirty rooms is bounded by `Semaphore(3)` in `supervisorScope`.

## Side Effects
- Reads and mutates Room database (`ChatDao`).
- Triggers network requests and WebSocket frames via `:core:network`.
- Observes Android process foreground/background lifecycle and Android connectivity state.

## Verification
- Run: `./gradlew :core:data:testDebugUnitTest --rerun-tasks`
