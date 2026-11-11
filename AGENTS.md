# Agent Instructions — Android-ChatApp

## 1. Philosophy, Documentation & UI Governance Gate

Before proposing any plan or writing code, read and follow:

1. **Global Agent Rules**: `~/.gemini/config/AGENTS.md` (Git sign-off/attribution, unified EGM +
   Archify lifecycle, post-implementation scaffolding pruning, and Archify/Chrome CLI commands).
2. **Project Philosophy**: `../KMP-ChatContract/docs/PHILOSOPHY.md` (especially Section 3 and
   Section 5 Mandatory Agent Planning Protocol).
3. **Unified Repository Documentation Index**: [`docs/README.md`](docs/README.md) (Single index for
   C4 Level 3 `android-components.archify.json`, Android-internal Archify flows, and EGM design
   specs in `docs/designs/`).
4. **Module Contract Invariant**: Every Gradle module maintains a machine-verifiable contract in its
   `<module>/README.md` (`Interface`, `Invariants`, `Side Effects`, `Verification`). Inspect a
   module's `README.md` before modifying it, and update it (plus root `README.md` if topology
   changes) in the same changeset.

### 1.1 UI & Design System Gate (MCP Stitch SSOT)

1. **Visual & Token SSOT**: The NeroChat *Quiet Messaging* design system on MCP `stitch` (Project
   ID: `projects/10647833259558812227`, spec screen `design.md`, and screen instances `Chats`,
   `Direct Conversation`, `Group Conversation`, `New Chat`, `Account`) is the Single Source of Truth
   for semantic color tokens, typography, geometry, spacing, and visual feedback rules (e.g., quiet
   connected state; subtle contextual banners instead of blocking full-screen spinners when cached
   data exists).
2. **Authority Boundary (Visual vs. Functional)**: Stitch `design.md` and screen layouts govern
   visual presentation and component states, whereas `:core:model`, `:core:data`, and approved EGM
   specs in `docs/designs/` govern functional capabilities (do not regress domain capabilities such
   as optimistic offline message staging, message retry, soft deletion, or room membership actions
   due to older historical notes in `stitch-prompt.md`).
3. **Pre-Design & Post-Execution Stitch Audit**: Before designing or modifying UI in `:core:ui`,
   `:feature:auth`, or `:feature:chat`, inspect the Stitch design system and relevant screens via
   MCP `stitch` (`get_project`, `list_screens`, `get_screen`, `download_file`). Upon completing
   implementation, cross-check implemented tokens, component states, and copy against Stitch
   specifications alongside headless Robolectric `testTag` verification.

---

## 2. Module Map & Architectural Invariants

### 2.1 Module Hierarchy
```text
:app
 ├──> :feature:auth ──> :core:ui, :core:data (interfaces), :core:model, :validation:auth
 ├──> :feature:chat ──> :core:ui, :core:data (interfaces), :core:model, :validation:chat
 ├──> :sync:work ─────> :core:data (ChatSyncScheduler SPI, ChatSyncManager), :core:model
 └──> :core:data ─────> :core:network (──> :contract:{common,auth,chat})
                  ├───> :core:database ──> :core:model
                  ├───> :core:datastore ──> :core:security, :core:model, :core:common
                  └───> :core:model, :core:common
```

- `:app`: Application entrypoint, `MainActivity`, splash screen, Hilt `Configuration.Provider`, and
  Navigation 3 root switcher.
- `:feature:auth`: Login and registration UI flows (Jetpack Compose).
- `:feature:chat`: Chat list, conversation, new chat, and account settings UI flows (Jetpack Compose).
- `:sync:work`: Edge Android WorkManager consumer (`ChatSyncWorker`, `WorkManagerSyncSchedulerImpl`)
  implementing `:core:data`'s `ChatSyncScheduler` SPI.
- `:core:ui`: Shared design system, theme, and common Compose components.
- `:core:data`: Repositories, synchronization coordinators (`ChatSyncManagerImpl`,
  `ChatRealtimeSyncManager`, `RealtimeConnectionManagerImpl`), `ConnectivityManagerNetworkMonitor`,
  `ChatSyncScheduler` SPI, and internal data-to-domain mappers.
- `:core:network`: Retrofit HTTP clients, OkHttp `ChatWebSocketClient`, interceptors (`AuthInterceptor`, `TokenAuthenticator`).
- `:core:database`: Room database (`ChatDatabase`, `ChatDao`), SQLite entities and relations.
- `:core:datastore`: DataStore token and user preferences persistence.
- `:core:security`: Android KeyStore AES-256-GCM encryption manager.
- `:core:model`: Pure Kotlin domain models (`User`, `ChatRoom`, `ChatMessage`, `ChatSyncState`,
  `RealtimeConnectionState`) and sealed error hierarchies (`AuthError`, `ChatError`, `DataError`).
- `:core:common`: Coroutine dispatchers and concurrency utilities (`SingleFlight`,
  `KeyedSingleFlight`, `ExponentialBackoff`, `mapConcurrent`).

### 2.2 Boundary Invariants & Hard Rules

1. **Presentation Layer Isolation**: Presentation modules (`:feature:auth`, `:feature:chat`,
   `:core:ui`) must **never** import or depend on `:core:network`, `:core:database`,
   `:core:datastore`, `:sync:work`, or `:contract:*` wire DTOs. They interact exclusively with
   repository/coordinator interfaces and pure domain models (`:core:model`), using shared validators
   (`:validation:*`) for form validation.
2. **Internal Data Layer Mappers**: All mapper functions and repository implementations in `:core:data` must be `internal` to prevent leaking database entities or network types into public APIs.
3. **Contract SSOT & Pure Serialization**: All network requests and WebSocket frames use `kotlinx.serialization.json.Json` with `KMP-ChatContract` models. Never introduce Gson, Moshi, or Jackson.
4. **WebSocket Lifecycle & Backoff**: Reconnection uses exponential backoff with jitter
   (`RealtimeConnectionManagerImpl` via `ExponentialBackoff`). `ChatWebSocketClient` enforces strict
   callback isolation on superseded sockets.
5. **Optimistic Sending & Idempotency**: Messages are inserted locally as `SENDING` with client-assigned `UUIDv7` IDs before transmission. Server acknowledgments or broadcasts reconcile status to `SENT`.
6. **UI Verification & Design Fidelity Standard**: Screens must conform to MCP Stitch
   (`projects/10647833259558812227`) tokens/layouts and expose deterministic `testTag` semantics for
   headless Robolectric testing.

---

## 3. Verified Development & Verification Commands

| Task / Purpose                     | Tier                       | Verified Command                                                                                                                                                                                                         |
|------------------------------------|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Fast Selective Feedback**        | Tier 1 (Iterative / Local) | `./scripts/verify.sh` *(Auto-detects modified modules via Git, reuses daemon & build cache)*                                                                                                                             |
| **Selective Diagram Sync**         | Tier 1 (Architecture Doc)  | `./scripts/sync-archify.sh` *(Auto-detects modified .archify.json, delivers HTML, checks layout, exports dual-theme PNGs)*                                                                                               |
| **Verify Diagram Freshness**       | CI / Pre-Push              | `./scripts/sync-archify.sh --check`                                                                                                                                                                                      |
| **Strict Clean Verification Gate** | CI / Pre-Merge Gate        | `./scripts/verify.sh clean` *(Full rerun: unit tests & lintDebug, CI enforces this)*                                                                                                                                     |
| Fast re-run of tested modules      | Direct Gradle              | `./gradlew :core:common:test :core:database:testDebugUnitTest :core:network:testDebugUnitTest :core:data:testDebugUnitTest :sync:work:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:chat:testDebugUnitTest` |
| Run database module unit tests     | Direct Gradle              | `./gradlew :core:database:testDebugUnitTest`                                                                                                                                                                             |
| Assemble debug APK                 | Direct Gradle              | `./gradlew assembleDebug`                                                                                                                                                                                                |
| Run Android Lint on debug variant  | Direct Gradle              | `./gradlew lintDebug`                                                                                                                                                                                                    |
| List runnable tasks                | Utility                    | `./gradlew tasks`                                                                                                                                                                                                        |

> [!IMPORTANT]
> **Verification Tier Policy**:
> - Use **Tier 1 (`./scripts/verify.sh` & `./scripts/sync-archify.sh`)** for rapid iterative
    feedback during development loops. Test execution is automatically scoped to affected modules (~
    3-10s).
> - **CI Gate (`.github/workflows/ci.yml`)** strictly enforces `./scripts/sync-archify.sh --check`
    and `./scripts/verify.sh clean` on every PR.
