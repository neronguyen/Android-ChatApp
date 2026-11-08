# core

## Overview
Intermediate blueprint grouping core foundation modules of Android-ChatApp (domain models, persistence, network, security, data synchronization, and shared UI).

## Subdirectories & Boundaries
- [core:common](./common/README.md) - Coroutine dispatchers and application-scoped task management.
- [core:model](./model/README.md) - Pure client domain models and error hierarchies.
- [core:security](./security/README.md) - Hardware-backed Android KeyStore AES-256-GCM encryption.
- [core:datastore](./datastore/README.md) - Encrypted Preferences DataStore for session tokens.
- [core:database](./database/README.md) - Room SQLite Single Source of Truth (`ChatDatabase`, `ChatDao`).
- [core:network](./network/README.md) - Retrofit HTTP clients, OkHttp authenticator, and WebSocket client.
- [core:data](./data/README.md) - Repositories, real-time sync manager, and WebSocket lifecycle coordinator.
- [core:ui](./ui/README.md) - Shared Compose design system, themes, and UI components.
