# Local E2E Smoke Harness & Agent Inspection CLI (A4)

* **Author:** Nero Nguyen
* **Status:** Approved
* **Date:** 2026-11-15

--------------------------------------------------------------------------------

## Overview

This design specifies **A4: Local E2E Smoke Harness & Agent Inspection CLI**, a deterministic,
zero-token Tier 3 cross-boundary verification system spanning `KMP-ChatBackend` and
`Android-ChatApp`. It executes the core user journey on a real Android Virtual Device
(`Medium_Phone_API_37.0`) connected over `adb reverse` to a real Spring Boot instance backed by
PostgreSQL, Redis, RabbitMQ, and Mailpit.

The system separates unattended regression execution from interactive debugging:

* **`scripts/smoke-e2e.sh` + `FullStackSmokeE2ETest` (`:app:src/androidTest`)** runs an 8-state
  Finite State Machine (`S0`–`S7`) via `ComposeTestRule` and `UiDevice`, pulling per-state
  screenshots, UI hierarchy XML dumps, and a generated `summary.md` proof-of-work report into
  gitignored `build/reports/e2e-smoke/<run-id>/`.
* **`scripts/e2e-ctl.sh`** exposes atomic host subcommands (`up`, `down`, `status`, `get-otp`,
  `ui-dump`, `screenshot`) so developers and AI agents can boot infrastructure or diagnose a broken
  run on demand without spending LLM tokens during normal test loops.

## The Problem

Tier 1 and Tier 2 verification suites in `KMP-ChatBackend` (`BackendGoldenWireTest`, Testcontainers
HTTP/WebSocket tests) and `Android-ChatApp` (Robolectric `*ScreenTest`, AGP Compose screenshot
suites, MockWebServer wire tests, `ArchitectureKonsistTest`) run headless in the JVM in 45–90
seconds, but leave three cross-process integration boundaries unverified until manual APK testing:

1. **Android Runtime Networking & Build Wiring**: `app/src/main/AndroidManifest.xml` relies on
   Android's API 37 default (`cleartextTrafficPermitted="false"`), which blocks
   `http://localhost:8080/` and `ws://localhost:8080/ws/chat` over `adb reverse`. Simultaneously,
   `core/network/build.gradle.kts` reads `BASE_URL` strictly from `local.properties`, forcing manual
   file edits that risk dirtying `git status` and tripping the clean-tree check in
   `./scripts/verify.sh clean`.
2. **Async Multi-Service Infrastructure Chains**: Neither `KMP-ChatBackend/docker-compose.yml` nor
   an unencrypted local SMTP/AMQP Spring profile exists yet (`application.yaml` enforces RabbitMQ
   SSL and SMTP STARTTLS). As a result, full-stack flows involving Redis-backed 6-digit OTP
   generation, Mailpit email delivery, and asynchronous RabbitMQ `UserEvent.Verified` projection
   from `:user` to `:chat` cannot be tested together end-to-end against a compiled APK.
3. **Host-Out-of-Band Orchestration Without Token Waste**: Evaluating UI tests with an AI agent at
   runtime (`android` CLI XML journeys) consumes LLM tokens per step, introduces non-determinism,
   and cannot run unattended on CI runners. Conversely, pure manual `adb` scripts lack structured
   post-run visual evidence and diagnostic entry points.

## Goals

* **G1 — Zero-Token Deterministic Full-Stack Verification**: Execute the 8-state CUJ and
  fault-recovery FSM (`S0_REGISTER_FORM` through `S7_RECONNECT_CATCHUP_SYNC`) deterministically with
  zero LLM calls via `:app:connectedDebugAndroidTest` gated by
  `-Pandroid.testInstrumentationRunnerArguments.e2eSmoke=true`.
* **G2 — Real Cross-Boundary & Async Coverage**: Verify real HTTP + Mailpit SMTP OTP delivery,
  RabbitMQ `UserEvent.Verified` peer visibility (`bob@example.com`), bidirectional WebSocket
  delivery (`SENDING` -> `SENT` and peer inbound arrival), and `ConnectivityManagerNetworkMonitor`
  offline-to-online catch-up sync.
* **G3 — Clean Build & Security Isolation**: Keep `cleartextTrafficPermitted="false"` on release
  builds by isolating `network_security_config.xml` to `app/src/debug/`, and pass
  `-PBASE_URL=http://localhost:8080/` via Gradle `providers.gradleProperty` without modifying
  `local.properties` or dirtying `git status`.
* **G4 — Fast Repeatable Runs Without Volume Wipes**: Support repeatable consecutive executions
  against warm Docker containers by clearing Android app data (`pm clear`), using run-scoped
  `alice.<epoch>@example.com` accounts, and idempotently ensuring `bob@example.com` is registered
  and verified via Mailpit.
* **G5 — Actionable Proof-of-Work & Diagnostic CLI**: Produce a self-contained
  `build/reports/e2e-smoke/<run-id>/summary.md` (`<run-id>` formatted `YYYYMMDD-HHMMSS`) with 8
  checkpoint PNGs and `uiautomator` dumps even if an intermediate state fails, alongside
  `scripts/e2e-ctl.sh` (`up`, `down`, `status`, `get-otp`, `ui-dump`, `screenshot`) for rapid
  triage.
* **G6 — Preserved Tier 1/2 Speed**: Keep `./scripts/verify.sh` and `./scripts/verify.sh clean` 100%
  emulator-free and Docker-free; isolate E2E execution to Tier 3 (`./scripts/smoke-e2e.sh`) for
  local milestone checks and manual/nightly KVM CI workflows.

## Non-Goals

* **NG1 — Combinatorial Input Validation on AVD**: Exhaustive form validation errors, invalid email
  formatting, and server `4xx` error-code matrices remain in Tier 1/2 Robolectric and Spring
  Testcontainers suites.
* **NG2 — LLM-Driven Runtime UI Navigation**: Using AI agent turns or `<journey>.xml` loops to
  locate widgets or tap buttons during automated smoke runs is explicitly excluded.
* **NG3 — Test-Only Backend Auth/OTP Backdoors**: No special bypass endpoints, hardcoded OTPs, or
  direct SQL inserts skipping RabbitMQ `UserEvent.Verified` will be added to `KMP-ChatBackend`
  production code.
* **NG4 — Executing AVD Smoke on Every PR**: `./scripts/smoke-e2e.sh` will not run inside
  `./scripts/verify.sh clean` or standard pull-request CI gates.

## Critical User Journeys (CUJs)

### CUJ 1: Zero-Token Full-Stack Account Lifecycle, Bidirectional WebSocket & Reconnect Catch-Up (
`./scripts/smoke-e2e.sh`)

* **Given**: Docker Compose (`postgres`, `redis`, `rabbitmq`, `mailpit`), `KMP-ChatBackend`
  (`--spring.profiles.active=dev,e2e`), and AVD `Medium_Phone_API_37.0` are running with
  `adb reverse tcp:8080 tcp:8080` and `adb reverse tcp:8025 tcp:8025`; app data is cleared
  (`pm clear`) and `:app` is built with `-PBASE_URL=http://localhost:8080/`.
* **When**: `./scripts/smoke-e2e.sh` launches `FullStackSmokeE2ETest`
  (`-Pandroid.testInstrumentationRunnerArguments.e2eSmoke=true`), driving
  `alice.<epoch>@example.com` through the 8-state FSM (`S0`–`S7`) while an in-test `OkHttpClient`
  acts as verified peer `bob@example.com`.
* **Then**: Every FSM transition satisfies its `ComposeTestRule` assertion within bounded timeouts,
  8 checkpoint PNGs and `uiautomator` dumps are pulled to `build/reports/e2e-smoke/<run-id>/`, and
  `summary.md` reports `PASS` with zero LLM tokens consumed.

### CUJ 2: Host Infrastructure Bring-Up & Live Agent Inspection (`./scripts/e2e-ctl.sh`)

* **Given**: A developer or AI agent needs to boot services once for iterative debugging or inspect
  why a smoke state failed.
* **When**: The caller invokes `./scripts/e2e-ctl.sh up`, `status`, `get-otp <email>`, `ui-dump`,
  `screenshot <path>`, or `down`.
* **Then**: Services come up deterministically with health checks (`pg_isready`, `redis-cli ping`,
  `rabbitmq-diagnostics`, Mailpit HTTP, Backend `GET /v3/api-docs`), OTP codes are parsed from
  Mailpit's REST API, and current AVD semantics/PNGs are exported to the specified artifact path
  cleanly.

## Technical Plan (Architecture)

### 1. System Topology & Process Boundary Wiring

`Android-ChatApp` acts as the leaf orchestrator pointing to `../KMP-ChatBackend` (overridable via
`BACKEND_DIR`). Rather than duplicating Docker runtime logic inside `Android-ChatApp`,
`KMP-ChatBackend` owns `docker-compose.yml` and the unencrypted local/E2E Spring profile
(`application-e2e.yaml`), while `Android-ChatApp` owns the debug client build configuration,
instrumentation test driver, and host CLI scripts.

```text
+---------------------------------------+       +-----------------------------------------------+
| AVD: Medium_Phone_API_37.0            |       | Host Machine                                  |
|                                       |       |                                               |
|  :app (Debug APK)                     |       |  KMP-ChatBackend (Spring Boot :8080)          |
|   - debug network_security_config     |       |   - Profile: dev,e2e                          |
|   - BASE_URL=http://localhost:8080/   |       |   - /v3/api-docs (readiness probe)            |
|                                       |       |   - /api/auth/*, /api/chat/*, /ws/chat        |
|  FullStackSmokeE2ETest                |       |       |         |          |          |       |
|   - ComposeTestRule (Alice UI)        |==adb==|   Postgres   Redis   RabbitMQ   Mailpit (:8025)|
|   - OkHttp Driver (Mailpit + Bob)     |reverse|                                               |
|   - UiDevice.takeScreenshot           |8080   |  Host Scripts (Android-ChatApp/scripts/)      |
|                                       |8025   |   - e2e-ctl.sh (up/down/status/otp/dump/shot) |
+---------------------------------------+       |   - smoke-e2e.sh (orchestrate + report pull)  |
                                                +-----------------------------------------------+
```

### 2. Backend E2E Infrastructure (`KMP-ChatBackend`)

* **`docker-compose.yml`**: Defines 4 pinned service containers with explicit healthchecks and host
  port bindings synchronized with `application-e2e.yaml`:
    * `postgres` (`postgres:17-alpine`, port `5432`, DB `nerochat`, user `nerochat`, password
      `nerochat_e2e`, healthcheck `pg_isready -U nerochat`)
    * `redis` (`redis:7-alpine`, port `6379`, password `nerochat_e2e`, healthcheck
      `redis-cli -a nerochat_e2e ping`)
    * `rabbitmq` (`rabbitmq:4-management-alpine`, ports `5672`/`15672`, user `nerochat`, password
      `nerochat_e2e`, vhost `/`, healthcheck `rabbitmq-diagnostics -q ping`)
    * `mailpit` (`axllent/mailpit`, SMTP `1025`, Web/REST API `8025`, healthcheck
      `wget --spider -q http://localhost:8025/api/v1/info`)
* **`app/src/main/resources/application-e2e.yaml`**: Sets concrete Spring Boot property keys
  directly (overriding `application.yaml` placeholders and any developer `local.properties` imported
  via `spring.config.import: optional:file:local.properties` when `e2e` is the last active profile):
    * Database & Redis: `spring.datasource.url=jdbc:postgresql://localhost:5432/nerochat`,
      `spring.datasource.username=nerochat`, `spring.datasource.password=nerochat_e2e`, and explicit
      `spring.redis.redisson.config` targeting `redis://localhost:6379` with
      `password: "nerochat_e2e"`.
    * RabbitMQ (unencrypted local): `spring.rabbitmq.host=localhost`,
      `spring.rabbitmq.username=nerochat`, `spring.rabbitmq.password=nerochat_e2e`,
      `spring.rabbitmq.virtual-host=/`, and `spring.rabbitmq.ssl.enabled=false`.
    * Mailpit SMTP (unencrypted local): `spring.mail.host=localhost`, `spring.mail.port=1025`,
      `spring.mail.username=""`, `spring.mail.password=""`, `email.from=noreply@nerochat.local`,
      plus `spring.mail.properties.mail.smtp.auth=false`, `starttls.enable=false`, and
      `starttls.required=false`.
    * Auth & Security (non-production E2E defaults):
      `jwt.secret=dGhpcy1pcy1hLWRldmVsb3BtZW50LW9ubHktMjU2LWJpdC1qd3Qtc2VjcmV0LWtleQ==` (valid
      HMAC-SHA256 key length), `otp.pepper=e2e-only-otp-pepper-value`,
      `spring.jpa.hibernate.ddl-auto=update`, and IP rate limiting disabled.

### 3. Android Debug Networking & Parameterized `BASE_URL`

* **Debug-Only Cleartext Exemption**: Create `app/src/debug/AndroidManifest.xml` referencing
  `app/src/debug/res/xml/network_security_config.xml` permitting `cleartextTrafficPermitted="true"`
  strictly for domains `localhost`, `127.0.0.1`, and `10.0.2.2`. Release APKs inherit zero manifest
  overrides and keep `cleartextTrafficPermitted="false"`.
* **Configuration-Cache-Safe URL Injection**: Update `core/network/build.gradle.kts` to resolve
  `BASE_URL` in priority order:
  `providers.gradleProperty("BASE_URL").orNull ?: properties.getProperty("BASE_URL") ?: "http://localhost/"`,
  preserving existing non-E2E build behavior while letting `smoke-e2e.sh` pass
  `-PBASE_URL=http://localhost:8080/` cleanly. `ChatWebSocketClient.kt` already derives
  `ws://.../ws/chat` directly from `BuildConfig.BASE_URL`.
* **Minimal Determinism `testTag`s**: Add `.testTag("chat_list_new_chat_fab")` in
  `ChatListScreen.kt` and `.testTag("conversation_message_input")` in `ConversationScreen.kt` so
  `ComposeTestRule` can locate every interactive widget across `feature:auth` and `feature:chat`
  without modifying visual rendering or invalidating screenshot goldens.

### 4. Zero-Token 8-State FSM (`FullStackSmokeE2ETest` in `:app:src/androidTest`)

`FullStackSmokeE2ETest` runs under `AndroidJUnitRunner`, gated by
`Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("e2eSmoke") == "true")` so
normal `./gradlew connectedDebugAndroidTest` invocations skip it cleanly. Because `adb reverse`
exposes both ports `8080` and `8025` as `localhost` inside the AVD, the test drives Alice's UI via
`createAndroidComposeRule<MainActivity>()` and orchestrates Mailpit + Bob synchronously via an
in-test `OkHttpClient` using `KMP-ChatContract` serialization models (exempt from
`ArchitectureKonsistTest`, which scopes to `/src/main/`).

* **Mailpit Disambiguation (`E2EPeerClient.fetchLatestOtp(email)`)**: Polls
  `GET http://localhost:8025/api/v1/messages`, filters summaries whose `To` array contains
  `<email>`, selects the newest message ID by `Created` timestamp, fetches
  `GET http://localhost:8025/api/v1/message/{ID}`, and extracts the 6-digit OTP via `\b\d{6}\b` from
  its `Text` / `HTML` body.
* **Idempotent Peer Seeding (`E2EPeerClient.ensureVerifiedPeerBob()`)**: Attempts
  `POST /api/auth/login` for `bob@example.com`. If login fails, calls `POST /api/auth/register`; on
  `409 Conflict` (unverified prior account on warm containers), triggers
  `POST /api/auth/resend-verification` (`204 No Content`). Polls
  `fetchLatestOtp("bob@example.com")`, calls `POST /api/auth/verify-email`, and logs Bob in to
  obtain his Bearer JWT so RabbitMQ emits `UserEvent.Verified` to `:chat`.
* **Room Discovery**: Before sending inbound WebSocket messages in `S6`, Bob polls
  `GET /api/chat/rooms` with his Bearer JWT to resolve the newly created direct room's `roomId`.

| State                             | Screen / Phase       | Triggering Action                                                                                                                                                                                                                  | `ComposeTestRule` Post-Condition                                                                               | Saved Checkpoint Artifacts                 |
|:----------------------------------|:---------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------|:-------------------------------------------|
| **`S0_REGISTER_FORM`**            | Register             | Navigate from `login_screen` (`register_button`), fill `alice.<epoch>@example.com`, display name, and strong password                                                                                                              | Inputs populated on `register_screen` without client validation errors                                         | `00_register_form.png` + `.xml`            |
| **`S1_OTP_CHALLENGE`**            | Verify Email         | Tap `register_button`                                                                                                                                                                                                              | `verify_email_screen` & `otp_input_field` visible                                                              | `01_otp_challenge.png` + `.xml`            |
| **`S2_LOGIN_VERIFIED`**           | Login Verified       | Fetch Alice's 6-digit OTP via `fetchLatestOtp(aliceEmail)`, type into `otp_input_field` (auto-submitting via `VerifyEmailScreen`'s `onComplete`)                                                                                   | Redirected to `login_screen` with `confirmation_banner` visible                                                | `02_login_verified.png` + `.xml`           |
| **`S3_CHAT_LIST_EMPTY`**          | Chat List            | Fill Alice credentials + tap `login_button`                                                                                                                                                                                        | `chat_list_screen` visible; sync error banner absent                                                           | `03_chat_list_empty.png` + `.xml`          |
| **`S4_NEW_CHAT_FOUND_PEER`**      | New Chat Search      | Tap `chat_list_new_chat_fab`, type `bob@example.com` in `new_chat_email_field`, tap `new_chat_search_button`                                                                                                                       | `new_chat_searched_participant_card` visible (proving RabbitMQ `UserEvent.Verified` projection)                | `04_new_chat_found_peer.png` + `.xml`      |
| **`S5_OUTBOUND_MESSAGE_SENT`**    | Conversation Send    | Enter `"Hello Bob from AVD"` in `new_chat_first_message_field` (required to enable submit), tap `new_chat_submit_direct_button`; then send `"Follow-up from AVD"` via `conversation_message_input` + `conversation_send_button`    | Both outbound bubbles visible on `ConversationScreen` with status reconciled to `SENT`                         | `05_outbound_message_sent.png` + `.xml`    |
| **`S6_INBOUND_MESSAGE_RECEIVED`** | Conversation Receive | `E2EPeerClient` resolves `roomId` via `GET /api/chat/rooms`, connects WebSocket with Bob's JWT, and sends `WsClientMessage.NewMessage` (`"Reply from Bob over WS"`)                                                                | Bubble `"Reply from Bob over WS"` renders automatically on Alice's `ConversationScreen`                        | `06_inbound_message_received.png` + `.xml` |
| **`S7_RECONNECT_CATCHUP_SYNC`**   | Offline & Catch-Up   | Run `UiDevice.executeShellCommand("svc wifi disable")` + `svc data disable`; confirm `sync_status_bar`; Bob sends `"Offline missed msg"` via WS; re-enable `svc wifi enable` + `svc data enable` (re-verifying `adb` connectivity) | `sync_status_bar` clears back to quiet synced state and `"Offline missed msg"` renders in `ConversationScreen` | `07_reconnect_catchup_sync.png` + `.xml`   |

After every state checkpoint, a lightweight test helper captures `UiDevice.takeScreenshot(...)` and
`UiDevice.dumpWindowHierarchy(...)` to
`/sdcard/Android/data/io.github.neronguyen.chat/files/e2e-smoke/`.

### 5. Host Orchestration (`smoke-e2e.sh`) & Agent Inspection CLI (`e2e-ctl.sh`)

* **`scripts/e2e-ctl.sh <command>`** (deterministic, zero-token host primitives):
    * `up`: Starts `docker compose -f "${BACKEND_DIR}/docker-compose.yml" up -d --wait`; if `:8080`
      is not listening, launches
      `(cd "${BACKEND_DIR}" && setsid ./gradlew --no-daemon :app:bootRun --args='--spring.profiles.active=dev,e2e')`
      in its own process group with PID/PGID stored in `/tmp/nerochat-e2e-backend.pid` and polls
      `http://localhost:8080/v3/api-docs` (`200 OK`); boots `Medium_Phone_API_37.0` if no emulator
      is online and waits for `sys.boot_completed=1`; applies `adb reverse tcp:8080 tcp:8080` and
      `tcp:8025 tcp:8025`.
    * `down`: Terminates the host Spring Boot process group via its recorded PGID (validating
      process liveness and cleaning stale PID files), removes `adb reverse --remove-all`, and runs
      `docker compose stop` (preserving warm containers; `--clean` passes `down -v`).
    * `status`: Emits pass/fail status for Docker containers, Backend HTTP `:8080`, Mailpit `:8025`,
      AVD boot state, and `adb reverse --list`, exiting **non-zero** if any required layer is
      unhealthy.
    * `get-otp <email>`: Queries `http://localhost:8025/api/v1/messages` +
      `GET /api/v1/message/{ID}` filtered by recipient `<email>` and prints the latest 6-digit OTP.
    * `ui-dump [out.xml]` / `screenshot [out.png]`: Wraps `android layout` /
      `android screen capture` (falling back to `adb exec-out uiautomator dump` / `screencap`) into
      explicit paths for developer or agent triage.
* **`scripts/smoke-e2e.sh`**:
    1. Invokes `e2e-ctl.sh up`.
    2. Clears package data (`adb shell pm clear io.github.neronguyen.chat` ignoring not-installed
       exit).
    3. Runs
       `./gradlew :app:connectedDebugAndroidTest -PBASE_URL=http://localhost:8080/ -Pandroid.testInstrumentationRunnerArguments.e2eSmoke=true`
       while capturing `TEST_EXIT=$?` (`set +e` / `trap` around the Gradle invocation).
    4. **Unconditionally** pulls all PNGs and XML dumps from device into
       `build/reports/e2e-smoke/<run-id>/` (`<run-id>` = `YYYYMMDD-HHMMSS`) and generates
       `summary.md` linking every checkpoint image alongside step pass/fail status, even when
       `$TEST_EXIT != 0`.
    5. Leaves Docker containers warm by default (supports `--teardown` to stop stack on exit) and
       exits with `$TEST_EXIT`.

### 6. Verification Tier Boundaries

* **Tier 1 (`./scripts/verify.sh`)**: Affected-module unit tests (JVM, seconds).
* **Tier 2 (`./scripts/verify.sh clean`)**: Full JVM unit tests, Robolectric UI tests, AGP Compose
  screenshot tests, `ArchitectureKonsistTest`, and `lintDebug`. Enforced on every commit and PR CI.
* **Tier 3 (`./scripts/smoke-e2e.sh`)**: Full-stack AVD + Spring Boot + Docker Compose E2E smoke.
  Run on local milestone/release gates or manual/nightly KVM-enabled CI workflows (`e2e-smoke.yml`).

## Alternatives Considered

### Alternative A: Agent-Evaluated `android` CLI `<journey>.xml` Execution

* **Pros**: Reads live `android layout` JSON trees at runtime and produces agent-authored Markdown
  reports without writing Kotlin test code.
* **Cons**: Consumes LLM tokens on every execution step, exhibits non-deterministic timing when
  software keyboards obscure inputs, cannot fetch Mailpit OTPs or drive Bob's WebSocket without
  external tools, and cannot run unattended in token-free CI pipelines.
* **Reason for Rejection**: E2E regression runs must consume zero LLM tokens, run deterministically
  on both local machines and CI runners, and reuse the existing Compose `testTag` contracts already
  guarded by Tier 2 Robolectric tests.

### Alternative B: Host-Side Shell Driver (`android layout` +
`adb shell input`) with Separate Host Bob Script

* **Pros**: Controls the compiled APK completely externally without adding
  `androidTestImplementation` dependencies to `:app`.
* **Cons**: Requires custom polling loops around `android layout` JSON parsing, brittle
  software-keyboard focus management, and cross-process file/pipe synchronization between a host
  shell UI driver and a separate host WebSocket client for Bob.
* **Reason for Rejection**: `ComposeTestRule` eliminates polling flakiness and coordinate
  assumptions via direct semantics matching, while exposing Mailpit (`8025`) and Backend (`8080`)
  over `adb reverse` allows an in-test `OkHttpClient` to coordinate Bob's actions synchronously
  between UI assertions.

### Alternative C: Full Docker Compose Including Containerized Spring Boot

* **Pros**: Keeps the JVM runtime inside the same container network as Postgres, Redis, RabbitMQ,
  and Mailpit.
* **Cons**: Forces a full container rebuild/restart whenever backend Kotlin files change during
  local cross-repo debugging.
* **Reason for Rejection**: Running the 4 stateful dependencies in Docker Compose while starting
  Spring Boot natively on the host preserves fast local edit-compile feedback while keeping
  container networking simple over `localhost`.

### Alternative D: Direct SQL User Seeding & Test-Only OTP Bypass Endpoints

* **Pros**: Avoids running Mailpit or querying OTP codes during `setupPeerBob` and
  `S1_OTP_CHALLENGE`.
* **Cons**: Raw SQL `INSERT` into `chat_participants` skips the real RabbitMQ `UserEvent.Verified`
  delivery path, and OTP bypass hooks risk leaking insecure code paths into production builds.
* **Reason for Rejection**: Seeding `bob@example.com` through the real HTTP registration and Mailpit
  OTP verification endpoints keeps backend production code untouched and proves RabbitMQ event
  propagation works end-to-end.

--------------------------------------------------------------------------------

## Non-Functional Requirements

* **Security & Privacy**:
    * Cleartext HTTP/WebSocket traffic (`cleartextTrafficPermitted="true"`) is scoped strictly to
      `app/src/debug/res/xml/network_security_config.xml` and only for loopback/emulator hosts
      (`localhost`, `127.0.0.1`, `10.0.2.2`). Release builds enforce default HTTPS/WSS
      (`cleartextTrafficPermitted="false"`).
    * `application-e2e.yaml` binds unencrypted AMQP/SMTP settings and fixed non-production secrets
      solely when the `e2e` Spring profile is explicitly activated; no real user PII or production
      secrets are stored.
* **Performance & Token Economics**:
    * **Zero LLM tokens** consumed per `./scripts/smoke-e2e.sh` run.
    * Target warm-container execution time is **60–120 seconds** (reusing warm
      Postgres/Redis/RabbitMQ/Mailpit containers and `alice.<epoch>@example.com` isolation rather
      than wiping volumes on every iteration).
* **Repo Hygiene & Determinism**:
    * `./scripts/smoke-e2e.sh` never edits `local.properties` or writes reports into tracked source
      folders; all PNGs, XML dumps, and `summary.md` outputs write strictly to gitignored
      `build/reports/e2e-smoke/<run-id>/`.
    * `FullStackSmokeE2ETest` skips automatically (`Assume.assumeTrue`) unless
      `-Pandroid.testInstrumentationRunnerArguments.e2eSmoke=true` is set, preventing unintended
      failures during standard `./gradlew connectedDebugAndroidTest` invocations.

--------------------------------------------------------------------------------

## Risks & Mitigations

* **API 37 `S7` Connectivity Toggle Behavior**: On `Medium_Phone_API_37.0`, `svc wifi disable` +
  `svc data disable` might not drop `ConnectivityManager`'s default network callback or could
  interfere with `adb reverse` socket recovery.
    * *Mitigation*: Keep `svc wifi/data` as the self-contained in-test toggle and verify on
      `Medium_Phone_API_37.0` during Phase C; if `ConnectivityManagerNetworkMonitor` stays online or
      `airplane-mode` resets `adb reverse`, fall back to host-coordinated disconnect/re-apply
      (`adb reverse --remove tcp:8080` / `adb reverse tcp:8080 tcp:8080` from `smoke-e2e.sh`).
* **Host Port Collisions & Stale Backend PIDs**: Ports `5432`, `6379`, `5672`, `8025`, or `8080` may
  already be bound by local dev services, or an unclean exit may leave
  `/tmp/nerochat-e2e-backend.pid` stale.
    * *Mitigation*: `e2e-ctl.sh up` verifies live PID ownership + `/v3/api-docs` health before
      spawning Spring Boot, fails fast with actionable port-conflict diagnostics if `:8080` is
      occupied by an unrelated process, and `e2e-ctl.sh down` cleans stale PID files cleanly.
* **Warm-Container Data & Mailpit Growth**: Repeated runs without volume wipes accumulate
  `alice.<epoch>@example.com` rows and Mailpit messages.
    * *Mitigation*: `fetchLatestOtp(email)` filters strictly by recipient `To` address and newest
      `Created` timestamp; `e2e-ctl.sh down --clean` runs `docker compose down -v` whenever a full
      reset is desired.

--------------------------------------------------------------------------------

## Detailed Implementation Plan

### Phase A: Backend E2E Infrastructure (`KMP-ChatBackend`)

*Checkpoint*: `docker compose -f ../KMP-ChatBackend/docker-compose.yml config --quiet` succeeds and
`./gradlew :app:bootRun --args='--spring.profiles.active=dev,e2e'` serves
`GET http://localhost:8080/v3/api-docs` (`200 OK`).

#### 1. `../KMP-ChatBackend/docker-compose.yml` (Create)

* **Type / Scope**: Local & E2E Infrastructure Compose Definition (`KMP-ChatBackend`)
* **Proposed Changes**: Define four pinned services (`postgres:17-alpine` on `5432`,
  `redis:7-alpine` on `6379`, `rabbitmq:4-management-alpine` on `5672`/`15672`, and
  `axllent/mailpit` on SMTP `1025` + HTTP/UI `8025`) with persistent named volumes, synchronized
  local credentials (`nerochat` / `nerochat_e2e`), and service healthchecks (`pg_isready`,
  `redis-cli ping`, `rabbitmq-diagnostics -q ping`, and
  `wget --spider -q http://localhost:8025/api/v1/info`).

#### 2. `../KMP-ChatBackend/app/src/main/resources/application-e2e.yaml` (Create)

* **Type / Scope**: Spring Boot E2E Profile Configuration (`KMP-ChatBackend/:app`)
* **Proposed Changes**: Override cloud-TLS defaults from `application.yaml` for loopback container
  connections when launched with `--spring.profiles.active=dev,e2e` by setting Spring Boot property
  keys directly (`spring.datasource.url`, `spring.datasource.username`,
  `spring.datasource.password`, `spring.redis.redisson.config`, `spring.rabbitmq.host`,
  `spring.rabbitmq.username`, `spring.rabbitmq.password`, `spring.rabbitmq.virtual-host`,
  `spring.rabbitmq.ssl.enabled=false`, `spring.mail.host`, `spring.mail.port`,
  `spring.mail.username`, `spring.mail.password`, `email.from`,
  `spring.mail.properties.mail.smtp.auth=false`, `starttls.enable=false`, `starttls.required=false`,
  `jwt.secret`, and `otp.pepper`).

#### 3. `../KMP-ChatBackend/app/README.md`, `../KMP-ChatBackend/docs/README.md`,
`../KMP-ChatBackend/AGENTS.md` (Modify)

* **Type / Scope**: Backend Contracts & Documentation Index (`KMP-ChatBackend`)
* **Proposed Changes**: Document the `e2e` Spring profile and `docker-compose.yml` prerequisites in
  `app/README.md`, link this spec in `docs/README.md`, and note the Tier 3 local E2E startup entry
  point in `AGENTS.md`.

### Phase B: Android Debug Networking, Build Wiring & Minimal UI Semantics (`Android-ChatApp`)

*Checkpoint*: `./scripts/verify.sh clean` passes (including `ArchitectureKonsistTest`, `lintDebug`,
and all AGP Compose screenshot regression suites with zero golden updates).

#### 4. `app/src/debug/res/xml/network_security_config.xml` &
`app/src/debug/AndroidManifest.xml` (Create)

* **Type / Scope**: Debug-Only Android Network Security Configuration (`:app`)
* **Proposed Changes**: Configure `<network-security-config>` allowing
  `<domain-config cleartextTrafficPermitted="true">` strictly for `localhost`, `127.0.0.1`, and
  `10.0.2.2`, and attach `android:networkSecurityConfig="@xml/network_security_config"` via debug
  manifest overlay without modifying `app/src/main/AndroidManifest.xml`.

#### 5. `core/network/build.gradle.kts` (Modify)

* **Type / Scope**: Gradle BuildConfig Parameterization (`:core:network`)
* **Proposed Changes**: Update `defaultConfig` `BASE_URL` lookup to resolve
  `providers.gradleProperty("BASE_URL").orNull ?: properties.getProperty("BASE_URL") ?: "http://localhost/"`,
  keeping `local.properties` untouched and maintaining Gradle Configuration Cache compatibility.

#### 6. `gradle/libs.versions.toml` & `app/build.gradle.kts` (Modify)

* **Type / Scope**: Instrumentation Test Catalog & Dependencies (`:app`)
* **Proposed Changes**: Register pinned versions in `[versions]` / `[libraries]`:
  `androidx-test-ext-junit` (`"1.3.0"`), `androidx-test-runner` (`"1.7.0"`),
  `androidx-test-uiautomator` (`"2.3.0"`), and `okhttp` (reusing existing `version.ref = "okhttp"`,
  `"5.5.1"`). Set `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in
  `app/build.gradle.kts` `defaultConfig`. Add
  `androidTestImplementation(platform(libs.androidx.compose.bom))`, `libs.androidx.compose.ui.test`,
  `libs.androidx.test.ext.junit`, `libs.androidx.test.runner`, `libs.androidx.test.uiautomator`,
  `libs.okhttp`, `libs.kotlinx.serialization.json`, `libs.contract.common`, `libs.contract.auth`,
  and `libs.contract.chat`.

#### 7.
`feature/chat/src/main/kotlin/io/github/neronguyen/chat/feature/chat/presentation/list/ChatListScreen.kt` &
`feature/chat/src/main/kotlin/io/github/neronguyen/chat/feature/chat/presentation/conversation/ConversationScreen.kt` (Modify)

* **Type / Scope**: Minimal UI Testability Semantics (`:feature:chat`)
* **Proposed Changes**: Add `.testTag("chat_list_new_chat_fab")` on the primary
  `FloatingActionButton` in `ChatListScreen` and `.testTag("conversation_message_input")` on the
  composer `BasicTextField` in `ConversationScreen` with zero visual, token, or layout changes.

### Phase C: Zero-Token Instrumentation FSM Driver (`Android-ChatApp`)

*Checkpoint*: `./gradlew :app:compileDebugAndroidTestKotlin` succeeds cleanly and
`:app:connectedDebugAndroidTest` skips `FullStackSmokeE2ETest` unless
`-Pandroid.testInstrumentationRunnerArguments.e2eSmoke=true` is passed.

#### 8. `app/src/androidTest/kotlin/io/github/neronguyen/chat/e2e/FullStackSmokeE2ETest.kt` &
`app/src/androidTest/kotlin/io/github/neronguyen/chat/e2e/E2EPeerClient.kt` (Create)

* **Type / Scope**: Zero-Token Instrumentation FSM Test Driver (`:app:src/androidTest`)
* **Proposed Changes**:
    * Implement `E2EPeerClient` (`OkHttpClient` + `ContractJson` helper) providing:
      `fetchLatestOtp(email)` (queries `GET /api/v1/messages`, filters `To` by `<email>`, fetches
      newest `GET /api/v1/message/{ID}`, parses `\b\d{6}\b`), `ensureVerifiedPeerBob()`
      (`POST /api/auth/login` -> fallback `register` / `409 resend-verification` ->
      `fetchLatestOtp` -> `verify-email` -> `login`), `resolveDirectRoomId()`
      (`GET /api/chat/rooms`), `connectWebSocket(token)`, and
      `sendDirectTextMessage(roomId, content)`.
    * Implement `FullStackSmokeE2ETest` gated by
      `Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("e2eSmoke") == "true")`,
      launching `MainActivity` via `createAndroidComposeRule<MainActivity>()`, executing
      `S0_REGISTER_FORM` through `S7_RECONNECT_CATCHUP_SYNC` (including `otp_input_field`
      auto-submit in `S2`, `new_chat_first_message_field` in `S5`, and `svc wifi/data` toggle in
      `S7` wrapped with a `@After` / `try-finally` guard to re-enable
      `svc wifi enable && svc data enable`), capturing `UiDevice.takeScreenshot` +
      `UiDevice.dumpWindowHierarchy` at each checkpoint into
      `/sdcard/Android/data/io.github.neronguyen.chat/files/e2e-smoke/`.

### Phase D: Host Orchestration, Optional CI & Living Docs (`Android-ChatApp`)

*Checkpoint*: `./scripts/smoke-e2e.sh` executes end-to-end, populates
`build/reports/e2e-smoke/<run-id>/summary.md` with 8 PNGs, and leaves `git status --porcelain` clean
across both repositories.

#### 9. `scripts/e2e-ctl.sh` & `scripts/smoke-e2e.sh` (Create)

* **Type / Scope**: Host Orchestration & Agent Inspection CLI (`Android-ChatApp/scripts/`)
* **Proposed Changes**:
    * `scripts/e2e-ctl.sh`: Subcommands `up`, `down [--clean]`, `status` (non-zero exit when
      unhealthy), `get-otp <email>`, `ui-dump [path]`, and `screenshot [path]`. Launches Spring Boot
      via `setsid ./gradlew --no-daemon :app:bootRun --args='--spring.profiles.active=dev,e2e'` in
      `${BACKEND_DIR}`, manages `/tmp/nerochat-e2e-backend.pid` (process-group teardown on `down`),
      polls `http://localhost:8080/v3/api-docs`, boots `Medium_Phone_API_37.0` if absent, and sets
      `adb reverse tcp:8080 tcp:8080` + `tcp:8025 tcp:8025`.
    * `scripts/smoke-e2e.sh`: Calls `e2e-ctl.sh up`, runs
      `adb shell pm clear io.github.neronguyen.chat`, executes
      `./gradlew :app:connectedDebugAndroidTest -PBASE_URL=http://localhost:8080/ -Pandroid.testInstrumentationRunnerArguments.e2eSmoke=true`
      while trapping/capturing `TEST_EXIT=$?`, unconditionally pulls PNGs/XMLs into
      `build/reports/e2e-smoke/<run-id>/` (`YYYYMMDD-HHMMSS`), writes `summary.md`, and exits with
      `$TEST_EXIT` (supports `--teardown`).

#### 10. `app/README.md`, `core/network/README.md`, `feature/chat/README.md`, `docs/README.md`,
`AGENTS.md` (Modify)

* **Type / Scope**: Living Contracts, Design Registry & Tier 3 Docs (`Android-ChatApp`)
* **Proposed Changes**: Update impacted module contracts (`app/README.md`, `core/network/README.md`,
  `feature/chat/README.md`), keep `docs/README.md` synced, and document Tier 3
  (`./scripts/smoke-e2e.sh`) and `scripts/e2e-ctl.sh` in `AGENTS.md`.

#### 11. `.github/workflows/e2e-smoke.yml` (Create — Optional CI Trigger)

* **Type / Scope**: Manual / Nightly KVM Full-Stack CI Workflow (`Android-ChatApp`)
* **Proposed Changes**: Define a `workflow_dispatch` / scheduled nightly job checking out
  `KMP-ChatBackend` + `Android-ChatApp`, invoking `./scripts/smoke-e2e.sh --teardown`, and uploading
  `build/reports/e2e-smoke/` as a workflow artifact (kept separate from `ci.yml` PR checks).

### Verification Plan

* **Backend Pre-Flight**: `docker compose -f ../KMP-ChatBackend/docker-compose.yml config --quiet`
  validates compose schema; `./scripts/e2e-ctl.sh up && ./scripts/e2e-ctl.sh status` verifies
  Postgres, Redis, RabbitMQ, Mailpit, and Spring Boot (`GET /v3/api-docs` HTTP `200`).
* **Tier 2 Clean Gate (`KMP-ChatBackend` & `Android-ChatApp`)**:
    * `KMP-ChatBackend`: `./scripts/verify.sh clean` passes with clean `git status`.
    * `Android-ChatApp`: `./scripts/verify.sh clean` (all JVM unit tests, Robolectric UI tests,
      `:core:ui` / `:feature:auth` / `:feature:chat` AGP screenshot regression suites,
      `ArchitectureKonsistTest`, `lintDebug`) passes without modifying any screenshot reference
      goldens.
* **Tier 3 E2E Smoke Execution (`Android-ChatApp`)**:
    * `./scripts/smoke-e2e.sh` completes `exit 0` with zero LLM tokens, executes all 8 FSM
      checkpoints (`S0`–`S7`), populates `build/reports/e2e-smoke/<run-id>/summary.md` with 8 PNG
      screenshots + UI hierarchy dumps, and leaves `git status --porcelain` clean across both
      repositories.
* **Diagnostic CLI Verification**: Verify `./scripts/e2e-ctl.sh get-otp <email>`,
  `./scripts/e2e-ctl.sh ui-dump`, and `./scripts/e2e-ctl.sh screenshot` individually against a
  running stack.

--------------------------------------------------------------------------------

*Drafted with EGM*
