# Agent Instructions — Android-ChatApp

## 1. Philosophy & Governance Gate
Before proposing any plan or writing code, read and strictly follow:
1. `../KMP-ChatContract/docs/PHILOSOPHY.md` (or `https://github.com/neronguyen/KMP-ChatContract/blob/main/docs/PHILOSOPHY.md` if sibling directory is unavailable), especially Section 3 and Section 5.
2. Architecture specifications:
   - Level 3 Android Components: `docs/architecture/specifications/android-components.archify.json` (Viewer: `docs/architecture/interactive/c4-android-components.html`)
3. Baseline Audit Snapshot: `docs/audit/2026-11-baseline-android.md`

Every implementation plan must satisfy the **Mandatory Agent Planning Protocol** defined in `PHILOSOPHY.md` Section 5 before execution (read it; it is not restated here).

### 1.1 Design-First Flow Gate (Tooling Binding for Protocol Step 3)
To satisfy Step 3 (`To-Be Flow & Lifecycle Spec`) of the Planning Protocol:
1. Cross-repo wire flows are defined in `:contract` (`docs/architecture/specifications/flows/`). Consult `docs/architecture/flows/README.md`. Android-internal flows (e.g. Room lifecycle, offline-first sync) are stored in `docs/architecture/specifications/flows/<name>.<type>.archify.json` (`<type>` = `sequence` | `lifecycle`) and registered in `docs/architecture/flows/README.md`.
2. Mark `meta.title` and the registry row `[PLANNED]` before writing code.
3. **Completion Invariant (No Dangling `[PLANNED]`)**: After Step 4 verification passes, flip `[PLANNED]` → `[AS-IS]` in both the `.archify.json` spec and `docs/architecture/flows/README.md`, re-render interactive HTML and image assets, and verify that no in-scope flow remains `[PLANNED]`.
4. Render and verify the interactive HTML diagram via:
   `node ~/.gemini/config/skills/archify/bin/archify.mjs deliver <type> <input.json> <output.html> --quality showcase --json && node ~/.gemini/config/skills/archify/bin/archify.mjs check <output.html> --json`
5. **Static Image Assets**: For diagrams embedded in `README.md` or markdown documentation, export and update dark and light static image assets in `docs/architecture/assets/<name>.png` and `docs/architecture/assets/<name>.light.png`.

### 1.2 Module Contracts & Design Registry (EGM)
1. **Design Registry**: Approved technical design documents live in `docs/designs/` and are indexed in `docs/designs/SITEMAP.md`. In an execution session, follow the approved design's implementation plan and verify any impacted flows against Section 1.1.
2. **Module Contract Invariant**: Every Gradle module maintains a machine-verifiable contract in its `README.md` (`Interface`, `Invariants`, `Side Effects`, `Verification`). Before modifying a module, inspect its `README.md`. Any change to public symbols, invariants, side effects, or dependencies must update that module's `README.md` (and parent blueprint if boundaries change) in the same changeset.

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

| Task / Purpose | Tier | Verified Command |
|---|---|---|
| **Fast Inner-Loop Feedback** | Tier 1 (Iterative) | `./scripts/verify.sh fast` |
| **Strict Clean Verification Gate** | Tier 2 (Pre-Commit / Gate) | `./scripts/verify.sh clean` |
| Fast re-run of tested modules | Direct Gradle | `./gradlew :core:database:testDebugUnitTest :core:network:testDebugUnitTest :core:data:testDebugUnitTest :feature:auth:testDebugUnitTest :feature:chat:testDebugUnitTest` |
| Run database module unit tests | Direct Gradle | `./gradlew :core:database:testDebugUnitTest` |
| Assemble debug APK | Direct Gradle | `./gradlew assembleDebug` |
| Run Android Lint on debug variant | Direct Gradle | `./gradlew lintDebug` |
| List runnable tasks | Utility | `./gradlew tasks` |

> [!IMPORTANT]
> **Verification Tier Policy**:
> - Agents may use **Tier 1 (`fast`)** for rapid iterative feedback during development loops.
> - Agents **MUST** run and pass **Tier 2 (`clean`)** before committing any changes or flipping any flow spec from `[PLANNED]` to `[AS-IS]`.

*Note: `./scripts/verify.sh` automatically configures host Android SDK (`${HOME}/Android/Sdk`), JDK 21+, and sandbox-safe `ANDROID_USER_HOME=/tmp/android-home`.*


