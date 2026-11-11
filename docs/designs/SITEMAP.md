# Design Documents Sitemap — Android-ChatApp

This sitemap registers technical design documents authored and reviewed under the Elephant-and-Goldfish Model (EGM) for `Android-ChatApp`.

| Design Document | Date | Author | Status | Target Modules | Description |
|---|---|---|---|---|---|
| [offline-first-message-sync-and-cache.md](./offline-first-message-sync-and-cache.md) | 2026-11-10 | Nero Nguyen | Implemented | `:sync:work`, `:core:data`, `:core:model`, `:app` | Core data offline-first sync architecture: WorkManager extraction to :sync:work, NetworkMonitor, ChatSyncState data model, bounded catch-up prefetch, and repository coordination refactoring. |
