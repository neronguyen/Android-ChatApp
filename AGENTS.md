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

### 1.1 Codebase-First UI & Design System Gate

1. **Visual & Token SSOT**: [`docs/designs/design-system.md`](docs/designs/design-system.md) and
   `:core:ui` (`Theme.kt`, `Color.kt`, `Shape.kt`, `Type.kt`) are the Single Source of Truth for
   semantic color tokens, typography, geometry, spacing, and *Quiet Messaging* visual rules (flat
   `0.dp` elevation, quiet connected state, subtle contextual banners instead of blocking
   full-screen spinners when cached data exists).
2. **Component & Token Consumption Policy**: Feature modules (`:feature:auth`, `:feature:chat`)
   build screens using standard Material 3 primitives (`Scaffold`, `TopAppBar`, `OutlinedTextField`,
   `Button`) consuming tokens exclusively through `MaterialTheme.colorScheme`,
   `MaterialTheme.typography`, and `MaterialTheme.shapes`. Shared multi-module or stateful widgets
   (`SyncStatusBar`, `OtpInputField`) live in `:core:ui`. Never hardcode `Color(...)` literals,
   import raw `Chat*` color constants, or call `MaterialTheme { ... }` as a theme wrapper inside
   `:feature:*` (enforced by `ArchitectureKonsistTest`).
3. **Preview & Self-Inspection Loop**: Every `*Screen.kt` in `:feature:*` must expose `internal`
   `@Preview(showBackground = true, widthDp = 390, heightDp = 844)` functions wrapped in `ChatTheme`
   covering key visual states (reused by `*ScreenScreenshotTest` and enforced by
   `ArchitectureKonsistTest`), and reusable UI components in `:core:ui` must expose `@Preview`
   functions wrapped in `ChatTheme`. Agents should use `android preview render` (when the `android`
   CLI is available) to inspect layout, spacing, and contrast locally, alongside deterministic
   `testTag` Robolectric semantics tests.
4. **Screenshot Regression Policy & Verification Gate**: Preview screenshot tests in `:core:ui`,
   `:feature:auth`, and `:feature:chat` run host-side on Linux via AGP 9.5.1 screenshot test suites
   (`screenshot-validation-junit-engine` `0.0.1-alpha17`). Reference images are committed to version
   control under `<module>/src/screenshotTest*/reference/`.
    - **Permitted Updates**: Only run `updateDebugScreenshotTestDefaultTestSuite` when the active
      task explicitly requests a visual/layout change (including re-baselining impacted `:feature:*`
      suites when shared `:core:ui` tokens or components change visually), and present updated
      screenshots for user review.
    - **Prohibited Updates**: Never update screenshot golden images to silence unexpected failures
      during non-UI bug fixes, logic refactors, or dependency upgrades.
    - Validation command:
      `./gradlew :core:ui:testDebugScreenshotTestDefaultTestSuite :feature:auth:testDebugScreenshotTestDefaultTestSuite :feature:chat:testDebugScreenshotTestDefaultTestSuite`
      (enforced in `./scripts/verify.sh clean`); re-baselining command:
      `./scripts/verify.sh :<module>:updateDebugScreenshotTestDefaultTestSuite`.

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
3. **Contract SSOT & Pure Serialization**: All network requests and WebSocket frames use
   `kotlinx.serialization.json.Json` with `KMP-ChatContract` models. Never introduce Gson, Moshi, or
   Jackson. Likewise, do not duplicate standalone DTO or `ErrorCode` golden wire serialization
   tests—`KMP-ChatContract` owns pure contract wire-format tests; Android wire tests
   (`RetrofitNetworkWireTest`, `ChatWebSocketClientTest`) must strictly verify
   Retrofit/OkHttp/WebSocket client wiring.
4. **WebSocket Lifecycle & Backoff**: Reconnection uses exponential backoff with jitter
   (`RealtimeConnectionManagerImpl` via `ExponentialBackoff`). `ChatWebSocketClient` enforces strict
   callback isolation on superseded sockets.
5. **Optimistic Sending & Idempotency**: Messages are inserted locally as `SENDING` with client-assigned `UUIDv7` IDs before transmission. Server acknowledgments or broadcasts reconcile status to `SENT`.
6. **UI Verification & Design Fidelity Standard**: Screens must conform to [
   `docs/designs/design-system.md`](docs/designs/design-system.md) / `ChatTheme` tokens via
   `MaterialTheme` and expose deterministic `testTag` semantics for headless Robolectric testing.

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
