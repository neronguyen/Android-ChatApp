# Android-ChatApp — Architecture & Design Registry

This is the unified documentation index for `Android-ChatApp`, linking C4 component architecture,
Archify sequence/lifecycle/dataflow specifications, and Elephant-and-Goldfish Model (EGM) technical
design documents.

> **Governance & Authoring Rules**: Follow global agent lifecycle rules in
> `~/.gemini/config/AGENTS.md`, architectural philosophy in [
`../KMP-ChatContract/docs/PHILOSOPHY.md`](../../KMP-ChatContract/docs/PHILOSOPHY.md), and repository
> boundary invariants in [`../AGENTS.md`](../AGENTS.md).

---

## 1. C4 Component Architecture (Level 3)

| Diagram                    | Level      | Status    | Specification (JSON)                                                                               | Interactive Viewer (HTML)                                                             | Static Assets (Dark / Light)                                                                                     |
|----------------------------|------------|-----------|----------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| **Android App Components** | C4 Level 3 | `[AS-IS]` | [`android-components.archify.json`](./architecture/specifications/android-components.archify.json) | [`c4-android-components.html`](./architecture/interactive/c4-android-components.html) | [Dark](./architecture/assets/AndroidComponents.png) · [Light](./architecture/assets/AndroidComponents.light.png) |

---

## 2. Architecture Flows & Lifecycles

Shared client–server wire flows (`ws-lifecycle`, `token-refresh`, `optimistic-send`) are maintained
in the Contract SSOT registry: [
`KMP-ChatContract/docs/README.md`](../../KMP-ChatContract/docs/README.md).

| Flow                                                       | Type        | Status      | Specification & Artifacts                                                                                                                                                                                                                                                                           | Linked Design Spec                                                      | Asserting Test Suite                                           |
|------------------------------------------------------------|-------------|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------|
| **Room Message State Lifecycle (`MessageDeliveryStatus`)** | `lifecycle` | `[AS-IS]`   | [Spec](./architecture/specifications/flows/message-delivery-status.lifecycle.archify.json) · [Viewer](./architecture/interactive/flows/message-delivery-status.html) · [Dark](./architecture/assets/message-delivery-status.png) · [Light](./architecture/assets/message-delivery-status.light.png) | [Offline-First Sync](./designs/offline-first-message-sync-and-cache.md) | `:core:database:testDebugUnitTest`                             |
| **Unknown Room FK Guard (`ChatRealtimeSyncManager`)**      | `sequence`  | `[AS-IS]`   | [Spec](./architecture/specifications/flows/unknown-room-fk-guard.sequence.archify.json) · [Viewer](./architecture/interactive/flows/unknown-room-fk-guard.html) · [Dark](./architecture/assets/unknown-room-fk-guard.png) · [Light](./architecture/assets/unknown-room-fk-guard.light.png)          | [Offline-First Sync](./designs/offline-first-message-sync-and-cache.md) | `:core:data:testDebugUnitTest`                                 |
| **Offline Catch-Up & Background Sync (`ChatSyncManager`)** | `sequence`  | `[AS-IS]`   | [Spec](./architecture/specifications/flows/offline-catchup-sync.sequence.archify.json) · [Viewer](./architecture/interactive/flows/offline-catchup-sync.html) · [Dark](./architecture/assets/offline-catchup-sync.png) · [Light](./architecture/assets/offline-catchup-sync.light.png)              | [Offline-First Sync](./designs/offline-first-message-sync-and-cache.md) | `:core:data:testDebugUnitTest`, `:sync:work:testDebugUnitTest` |
| ***Room to Compose UI Data Flow***                         | `dataflow`  | `[PLANNED]` | `docs/architecture/specifications/flows/room-to-ui.dataflow.archify.json`                                                                                                                                                                                                                           | *(Queued: Presentation & Sync UI Slice)*                                | `:feature:chat:testDebugUnitTest`                              |

---

## 3. Technical Design Documents (EGM)

| Design Document                                                                                | Date       | Status        | Target Modules                                    | Linked Archify Specs                                                                                                                                                                             | Summary                                                                                                                                                   |
|------------------------------------------------------------------------------------------------|------------|---------------|---------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`offline-first-message-sync-and-cache.md`](./designs/offline-first-message-sync-and-cache.md) | 2026-11-10 | `Implemented` | `:sync:work`, `:core:data`, `:core:model`, `:app` | [`android-components`](./architecture/specifications/android-components.archify.json) · [`offline-catchup-sync`](./architecture/specifications/flows/offline-catchup-sync.sequence.archify.json) | WorkManager extraction to `:sync:work`, proactive `NetworkMonitor`, single-flight coalescing, bounded parallel catch-up prefetching, and `ChatSyncState`. |
