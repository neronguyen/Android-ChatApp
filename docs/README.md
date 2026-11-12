# Android-ChatApp — Architecture & Design Registry

This is the unified documentation index for `Android-ChatApp`, linking C4 component architecture,
Archify sequence/lifecycle/dataflow specifications, and Elephant-and-Goldfish Model (EGM) technical
design documents.

> **Governance & Authoring Rules**: Follow global agent lifecycle rules in
> `~/.gemini/config/AGENTS.md`, architectural philosophy in [
`../KMP-ChatContract/docs/PHILOSOPHY.md`](../../KMP-ChatContract/docs/PHILOSOPHY.md), and repository
> boundary invariants in [`../AGENTS.md`](../AGENTS.md).

---

## 1. C4 Component Architecture (Level 3) & Design System Specification

| Diagram / Specification           | Level / Role | Status    | Specification (JSON / MD)                                                                          | Interactive Viewer (HTML)                                                             | Static Assets (Dark / Light)                                                                                     |
|-----------------------------------|--------------|-----------|----------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| **Android App Components**        | C4 Level 3   | `[AS-IS]` | [`android-components.archify.json`](./architecture/specifications/android-components.archify.json) | [`c4-android-components.html`](./architecture/interactive/c4-android-components.html) | [Dark](./architecture/assets/AndroidComponents.png) · [Light](./architecture/assets/AndroidComponents.light.png) |
| **Quiet Messaging Design System** | Visual SSOT  | `[AS-IS]` | [`designs/design-system.md`](designs/design-system.md)                                             | N/A (Markdown Specification)                                                          | Semantic Tokens & Theme Contract                                                                                 |

---

## 2. Architecture Flows & Lifecycles

Shared client–server wire flows (`ws-lifecycle`, `token-refresh`, `optimistic-send`) are maintained
in the Contract SSOT registry: [
`KMP-ChatContract/docs/README.md`](../../KMP-ChatContract/docs/README.md).

| Flow                                                       | Type        | Status      | Specification & Artifacts                                                                                                                                                                                                                                                                           | Linked Design Spec                                                      | Asserting Test Suite                                           |
|------------------------------------------------------------|-------------|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------|
| **Room Message State Lifecycle (`MessageDeliveryStatus`)** | `lifecycle` | `[AS-IS]`   | [Spec](./architecture/specifications/flows/message-delivery-status.lifecycle.archify.json) · [Viewer](./architecture/interactive/flows/message-delivery-status.html) · [Dark](./architecture/assets/message-delivery-status.png) · [Light](./architecture/assets/message-delivery-status.light.png) | [Offline-First Sync](./designs/offline-first-message-sync-and-cache.md) | `:core:database:testDebugUnitTest`                             |
| **Offline Catch-Up & Background Sync (`ChatSyncManager`)** | `sequence`  | `[AS-IS]`   | [Spec](./architecture/specifications/flows/offline-catchup-sync.sequence.archify.json) · [Viewer](./architecture/interactive/flows/offline-catchup-sync.html) · [Dark](./architecture/assets/offline-catchup-sync.png) · [Light](./architecture/assets/offline-catchup-sync.light.png)              | [Offline-First Sync](./designs/offline-first-message-sync-and-cache.md) | `:core:data:testDebugUnitTest`, `:sync:work:testDebugUnitTest` |

---

## 3. Technical Design Documents (EGM)

| Design Document                                                                                | Date       | Status        | Target Modules                                                      | Linked Archify Specs                                                                                                                                                                                                                                                                                               | Summary                                                                                                                                                                             |
|------------------------------------------------------------------------------------------------|------------|---------------|---------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`offline-first-message-sync-and-cache.md`](./designs/offline-first-message-sync-and-cache.md) | 2026-11-10 | `Implemented` | `:sync:work`, `:core:data`, `:core:database`, `:core:model`, `:app` | [`android-components`](./architecture/specifications/android-components.archify.json) · [`offline-catchup-sync`](./architecture/specifications/flows/offline-catchup-sync.sequence.archify.json) · [`message-delivery-status`](./architecture/specifications/flows/message-delivery-status.lifecycle.archify.json) | WorkManager extraction to `:sync:work`, internal `NetworkMonitor`, smoothed backoff, unified `ChatSyncState`, and bounded parallel prefetching.                                     |
| [`design-system.md`](./designs/design-system.md)                                               | 2026-11-11 | `Implemented` | `:core:ui`, `:feature:chat`, `:feature:auth`, `:app`                | N/A (Visual SSOT)                                                                                                                                                                                                                                                                                                  | Pure Visual SSOT for Quiet Messaging Design System, semantic color tokens, geometry/shapes, and typography.                                                                         |
| [`presentation-sync-ui-integration.md`](./designs/presentation-sync-ui-integration.md)         | 2026-11-12 | `Implemented` | `:core:data`, `:core:ui`, `:feature:chat`                           | [`android-components`](./architecture/specifications/android-components.archify.json)                                                                                                                                                                                                                              | Unified ChatSyncState in the UI, `ChatSyncManager.retry()`, shared Quiet Messaging `SyncStatusBar`, composer send gate, and `:core:ui` Compose Preview screenshot regression tests. |
