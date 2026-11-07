# Android Architecture Flows

This directory tracks Android-specific state and persistence flows (e.g., Room SQLite transactions, local UI state machines, and offline caching).

## Cross-Repository Flows
Shared client-server communication flows (WebSocket lifecycle, token refresh, optimistic message transmission) are specified in the Contract repository as the Single Source of Truth (SSOT):
- **Master Flow Index**: [Contract Flow Registry](../../../../KMP-ChatContract/docs/architecture/flows/README.md)
- **WebSocket Connection & Backoff**: `ws-lifecycle.sequence.archify.json`
- **Single-Flight Token Refresh**: `token-refresh.sequence.archify.json`
- **Optimistic Send & Idempotency**: `optimistic-send.sequence.archify.json`

## Android-Internal Flows (Track B+)

| Flow | Type | Status | Specification & Artifacts | Asserting Test Suite |
|---|---|---|---|---|
| Room Message State Lifecycle (`MessageDeliveryStatus`) | `lifecycle` | `[AS-IS]` | [Spec](../specifications/flows/message-delivery-status.lifecycle.archify.json) · [Viewer](../interactive/flows/message-delivery-status.html) · [Dark Preview](../assets/message-delivery-status.png) · [Light Preview](../assets/message-delivery-status.light.png) | `:core:database:testDebugUnitTest` |
| Unknown Room FK Guard (`ChatRealtimeSyncManager`) | `sequence` | `[AS-IS]` | [Spec](../specifications/flows/unknown-room-fk-guard.sequence.archify.json) · [Viewer](../interactive/flows/unknown-room-fk-guard.html) · [Dark Preview](../assets/unknown-room-fk-guard.png) · [Light Preview](../assets/unknown-room-fk-guard.light.png) | `:core:data:testDebugUnitTest` |
| Offline Catch-Up & Background Sync (`ChatSyncManager`) | `sequence` | `[AS-IS]` | [Spec](../specifications/flows/offline-catchup-sync.sequence.archify.json) · [Viewer](../interactive/flows/offline-catchup-sync.html) · [Dark Preview](../assets/offline-catchup-sync.png) · [Light Preview](../assets/offline-catchup-sync.light.png) | `:core:data:testDebugUnitTest` |
| *Room to Compose UI Data Flow* | `dataflow` | `[PLANNED]` | `docs/architecture/specifications/flows/room-to-ui.dataflow.archify.json` (Deferred) | `:feature:chat:testDebugUnitTest` |
