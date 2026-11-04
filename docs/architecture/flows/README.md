# Android Architecture Flows

This directory tracks Android-specific state and persistence flows (e.g., Room SQLite transactions, local UI state machines, and offline caching).

## Cross-Repository Flows
Shared client-server communication flows (WebSocket lifecycle, token refresh, optimistic message transmission) are specified in the Contract repository as the Single Source of Truth (SSOT):
- **Master Flow Index**: [Contract Flow Registry](../../../KMP-ChatContract/docs/architecture/flows/README.md)
- **WebSocket Connection & Backoff**: `ws-lifecycle.sequence.archify.json`
- **Single-Flight Token Refresh**: `token-refresh.sequence.archify.json`
- **Optimistic Send & Idempotency**: `optimistic-send.sequence.archify.json`

## Android-Internal Flows (Track B+)

| Flow | Type | Status | Specification | Asserting Test Suite |
|---|---|---|---|---|
| *Room Message State Lifecycle (`MessageDeliveryStatus`)* | `lifecycle` | `[PLANNED]` | `docs/architecture/specifications/flows/message-delivery-status.lifecycle.archify.json` (Track B) | `:core:database:testDebugUnitTest` |
| *Room to Compose UI Data Flow* | `dataflow` | `[PLANNED]` | `docs/architecture/specifications/flows/room-to-ui.dataflow.archify.json` (Track B) | `:feature:chat:testDebugUnitTest` |
