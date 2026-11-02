# Agent Instructions — Android-ChatApp

## 1. Philosophy & Governance Gate
Before proposing any plan or writing code, read and strictly follow:
1. `../KMP-ChatContract/docs/PHILOSOPHY.md` (or `https://github.com/neronguyen/KMP-ChatContract/blob/main/docs/PHILOSOPHY.md` if sibling directory is unavailable), especially Section 3 and Section 5.
2. Architecture specifications:
   - Level 3 Android Components: `docs/architecture/specifications/android-components.archify.json` (Viewer: `docs/architecture/interactive/c4-android-components.html`)
3. Baseline Audit Snapshot: `docs/audit/2026-11-baseline-android.md`

Every implementation plan must satisfy the **Mandatory Agent Planning Protocol** defined in `PHILOSOPHY.md` before execution:
1. **Core Mechanic & Trade-off**: Which state lifecycle, failure mode, or communication invariant is being solved, and what was traded off?
2. **Contract & Boundary Impact**: How does this change affect the client–server schema and module boundaries?
3. **Deterministic Verification Step**: What exact automated test or CLI command proves this change works headlessly?

---

## 2. Module Map & Architectural Invariants

### 2.1 Module Hierarchy
```text
:app
 ├──> :feature:auth ──> :core:ui, :core:data (interfaces), :core:model, :validation:auth
 ├──> :feature:chat ──> :core:ui, :core:data (interfaces), :core:model, :validation:chat
 └──> :core:data ─────> :core:network (──> :contract:{common,auth,chat})
                  ├───> :core:database ──> :core:model
                  ├───> :core:datastore ──> :core:security, :core:model, :core:common
                  └───> :core:model, :core:common
```

- `:app`: Application entrypoint, `MainActivity`, splash screen, and Navigation 3 root switcher.
- `:feature:auth`: Login and registration UI flows (Jetpack Compose).
- `:feature:chat`: Chat list, conversation, new chat, and account settings UI flows (Jetpack Compose).
- `:core:ui`: Shared design system, theme, and common Compose components.
- `:core:data`: Repositories, synchronization managers (`ChatRealtimeSyncManager`, `RealtimeConnectionManagerImpl`), and data-to-domain mappers.
- `:core:network`: Retrofit HTTP clients, OkHttp `ChatWebSocketClient`, interceptors (`AuthInterceptor`, `TokenAuthenticator`).
- `:core:database`: Room database (`ChatDatabase`, `ChatDao`), SQLite entities and relations.
- `:core:datastore`: DataStore token and user preferences persistence.
- `:core:security`: Android KeyStore AES-256-GCM encryption manager.
- `:core:model`: Pure Kotlin domain models (`User`, `ChatRoom`, `ChatMessage`) and sealed error hierarchies (`AuthError`, `ChatError`, `DataError`).
- `:core:common`: Coroutine dispatchers and core utilities.

### 2.2 Boundary Invariants & Hard Rules
1. **Presentation Layer Isolation**: Presentation modules (`:feature:auth`, `:feature:chat`, `:core:ui`) must **never** import or depend on `:core:network`, `:core:database`, `:core:datastore`, or `:contract:*` wire DTOs. They interact exclusively with repository interfaces and pure domain models (`:core:model`), using shared validators (`:validation:*`) for form validation.
2. **Internal Data Layer Mappers**: All mapper functions and repository implementations in `:core:data` must be `internal` to prevent leaking database entities or network types into public APIs.
3. **Contract SSOT & Pure Serialization**: All network requests and WebSocket frames use `kotlinx.serialization.json.Json` with `KMP-ChatContract` models. Never introduce Gson, Moshi, or Jackson.
4. **WebSocket Lifecycle & Backoff**: Reconnection uses exponential backoff with jitter (`RealtimeConnectionManagerImpl`). `ChatWebSocketClient` enforces strict callback isolation on superseded sockets.
5. **Optimistic Sending & Idempotency**: Messages are inserted locally as `SENDING` with client-assigned `UUIDv7` IDs before transmission. Server acknowledgments or broadcasts reconcile status to `SENT`.
6. **UI Verification Standard**: Screens must expose deterministic `testTag` semantics for headless Robolectric testing.

---

## 3. Verified Development & Verification Commands

All agent verification must be performed using these verified-green commands:

| Task / Purpose | Verified Command |
|---|---|
| Run all unit tests across all modules | `./gradlew testDebugUnitTest` |
| Fast re-run of tested modules | `./gradlew :core:network:testDebugUnitTest :core:data:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:chat:testDebugUnitTest --rerun-tasks` |
| Run single module unit tests | `./gradlew :core:network:testDebugUnitTest --tests "*"` |
| Assemble debug APK | `./gradlew assembleDebug` |
| Run Android Lint on debug variant | `./gradlew lintDebug` |
| List runnable tasks | `./gradlew tasks` |

*Note: `./gradlew` commands require access to host Android SDK (`/home/nero/Android/Sdk`) and JDK 21+.*

---

## 4. Git Commit Guidelines
When creating git commits:
1. **Sign-off (`-s`)**: Always include the `-s` flag (`--signoff`) when running `git commit`.
2. **Model Attribution Trailer**: Always append an `Assisted-by:` trailer to the commit message with the active model name (e.g., `Assisted-by: Gemini 3.8 Flash`).
