# Android-ChatApp

Native Android client for the end-to-end chat project — built with Jetpack Compose, offline-first Room persistence, lifecycle-supervised OkHttp WebSockets, hardware-backed AndroidKeyStore token encryption, and compile-time contract sharing via `KMP-ChatContract`.

> **Architecture & Philosophy**
> - Read the [Chat Project Philosophy](../KMP-ChatContract/docs/PHILOSOPHY.md) ([GitHub](https://github.com/neronguyen/KMP-ChatContract/blob/main/docs/PHILOSOPHY.md)) for the architectural principles and AI agent governance rules governing this repository.
> - **Medium Series**: [Part 1 — End-to-End Type Safety: Sharing Pure Kotlin Contracts Between Android and Spring Boot](https://medium.com/@neronguyen/end-to-end-type-safety-sharing-pure-kotlin-contracts-between-android-and-spring-boot-part-1-8c45e3ab6720)

## Related Repositories

| Repository | Role | C4 Architecture Level | Tech Stack |
| :--- | :--- | :--- | :--- |
| **[KMP-ChatContract](https://github.com/neronguyen/KMP-ChatContract)** | Compile-time wire schema, error contracts, and shared validators | **Level 1** (System Context) & **Level 2** (Containers) | Pure Kotlin 2.4, `kotlinx.serialization`, `kotlinx-datetime` |
| **[KMP-ChatBackend](https://github.com/neronguyen/KMP-NeroChatBackend)** | Modular monolith REST & WebSocket server | **Level 3** (Backend Components) | Kotlin, Spring Boot 4, PostgreSQL, Redis (Redisson), RabbitMQ |
| **[Android-ChatApp](https://github.com/neronguyen/Android-NeroChatApp)** *(this repo)* | Native Android client with offline-first persistence and real-time sync | **Level 3** (Android Components) | Jetpack Compose, Navigation 3, Retrofit, OkHttp, Room, DataStore, Hilt, Arrow |

---

## Android Component Architecture (C4 Level 3)

> **Interactive Architecture Viewers**:
> - **Level 3 — Android App Components (Local HTML)**: [`docs/architecture/interactive/c4-android-components.html`](docs/architecture/interactive/c4-android-components.html)
> - **Level 1 & Level 2 — System Context & Containers**: [`KMP-ChatContract/docs/architecture/`](https://github.com/neronguyen/KMP-ChatContract/tree/main/docs/architecture)

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/architecture/assets/AndroidComponents.png">
  <source media="(prefers-color-scheme: light)" srcset="docs/architecture/assets/AndroidComponents.light.png">
  <img alt="Android App Components (C4 Level 3)" src="docs/architecture/assets/AndroidComponents.png">
</picture>

### Key Architectural Mechanics
- **Offline-First SSOT & Optimistic Messaging**: `ChatDatabase` (`ChatDao`) serves as the Single Source of Truth. ViewModels observe reactive SQLite `Flow` streams while background synchronization runs via Retrofit. Outgoing messages insert immediately into Room with `SENDING` status before dispatching `WsClientMessage.NewMessage` over WebSocket, transitioning to `SENT` on server acknowledgement or `FAILED` on socket disconnect.
- **Lifecycle-Supervised Realtime Engine**: `RealtimeConnectionManagerImpl` combines `ProcessLifecycleOwner` foreground state (with a 30-second background grace period) and `AuthState` to supervise `ChatWebSocketClient` connections with exponential backoff and jitter. `ChatRealtimeSyncManager` consumes incoming `WsServerEvent` frames and applies transactional updates to `ChatDao`.
- **Single-Flight Token Refresh & Hardware Crypto**: `PreferencesTokenDataSource` encrypts access and refresh tokens via `AndroidKeyStoreCryptoManager` (`AES/GCM/NoPadding` 256-bit hardware-backed keys) with an in-memory `AtomicReference` fast path. `AuthInterceptor` and `TokenAuthenticator` share the authenticated OkHttp pipeline across both REST and WebSocket (`/ws/chat`) upgrades, invoking `AuthRepositoryImpl` (`TokenRefresher`) under a `Mutex` lock so concurrent `401 Unauthorized` responses trigger only a single refresh call.

---

## Module Structure

### App & Feature Modules
- `:app`: Application bootstrap (`ChatApplication` initializing `RealtimeConnectionManager` and `ChatRealtimeSyncManager`) and root navigation host (`MainActivity` observing `AuthState` to switch between `AuthNavGraph` and `ChatNavGraph`).
- `:feature:auth`: Login and registration screens and ViewModels (`LoginViewModel`, `RegisterViewModel`) running shared `AuthValidator` rules and mapping domain `AuthError` states to UI resources.
- `:feature:chat`: Chat room list (`ChatListViewModel`), conversation messaging (`ConversationViewModel`), participant search & room creation (`NewChatViewModel`), and account management (`AccountViewModel`).

### Core Modules
- `:core:data`: Offline-first repositories (`AuthRepositoryImpl`, `ChatRoomRepositoryImpl`, `ChatMessageRepositoryImpl`) and real-time coordinators (`RealtimeConnectionManagerImpl`, `ChatRealtimeSyncManager`).
- `:core:database`: Room SQLite database (`ChatDatabase`, `ChatDao`) storing rooms, messages, participants, and cross-references with `@Transaction` upserts.
- `:core:datastore`: Jetpack Preferences DataStore (`PreferencesTokenDataSource`) managing encrypted session tokens and `AuthState`.
- `:core:security`: Hardware-backed `AndroidKeyStore` AES-256 GCM encryption (`AndroidKeyStoreCryptoManager`).
- `:core:network`: Retrofit REST data sources (`RetrofitAuthNetwork`, `RetrofitChatNetwork`), OkHttp authentication pipeline (`AuthInterceptor`, `TokenAuthenticator`), and WebSocket client (`ChatWebSocketClient`).
- `:core:model`: Pure client domain models and typed error hierarchies (`AuthError`, `ChatError`, `DataError`).
- `:core:ui` & `:core:common`: Shared Jetpack Compose UI utilities, coroutine dispatchers, and application-scoped coroutines.

---

## Contract Integration

1. **Client-Side Pre-Validation**: Form inputs in `:feature:auth` and `:feature:chat` execute `AuthValidator`, `ChatValidator`, and `EmailValidator` from `KMP-ChatContract` synchronously before making network requests.
2. **Typed Error Classification**: `:core:network` and `:core:data` mappers decode error responses and classify wire codes against `AuthServerError`, `ChatServerError`, and `CommonServerError`, returning typed `Either<DomainError, T>` results via Arrow Core.
3. **Real-Time WebSocket Events**: `ChatWebSocketClient` encodes outgoing frames as `WsClientMessage` and decodes incoming frames as `WsServerEvent` using the shared polymorphic discriminator from `:contract:chat`.

---

## Local Development & Verification

### Composite Build with Local Contract

When `KMP-ChatContract` is cloned in the sibling directory (`../KMP-ChatContract`), Gradle automatically substitutes all `io.github.neronguyenvn.nerochat.contract:*` and `validation:*` coordinates with local project modules. To disable local substitution, pass `-PuseLocalContract=false`.

### Build & Run Tests

```bash
# Verify Gradle configuration and composite build wiring
./gradlew help

# Run unit tests across all modules
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```
