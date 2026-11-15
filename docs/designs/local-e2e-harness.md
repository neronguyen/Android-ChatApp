# Local E2E Harness & Service CLI (A4)

* **Author:** Nero Nguyen
* **Status:** Implemented
* **Date:** 2026-11-15

--------------------------------------------------------------------------------

## Overview

This design specifies **A4: Local E2E Harness & Service CLI**, a deterministic, zero-token Tier 3
cross-boundary verification system spanning `KMP-ChatBackend` and `Android-ChatApp`. It executes
modular Critical User Journeys (both Direct Chat and Stateful Negative/Recovery flows) on a real
Android Virtual Device (`Medium_Phone_API_37.0`) connected over `adb reverse` to a real Spring Boot
instance backed by PostgreSQL, Redis, RabbitMQ, and Mailpit.

The system separates unattended regression execution, demo video recording, and interactive
debugging into `scripts/e2e/`:

* **Modular E2E Journey Architecture (`BaseE2EJourneyTest` + `DirectChatE2ETest` +
  `OtpRecoveryE2ETest` in `:app:src/androidTest`)**: Shares a common harness base class that
  enforces `-Pandroid.testInstrumentationRunnerArguments.e2e=true`, derives `journeyId` in
  `kebab-case` from `<Journey>E2ETest`, manages per-journey persistent artifact mirroring
  (`/sdcard/Download/e2e/<journey>/`), records `steps.csv` rows (plus `failure.png` on unhandled
  exceptions, or step PNGs when `--capture-steps` is passed), and restores device connectivity
  (`svc wifi/data enable`).
* **`scripts/e2e/verify.sh`**: Supports running the full suite or selective journeys via
  `--journey <all|direct-chat|otp-recovery>`, overwrites per-journey `steps.csv`, `logcat.txt`, and
  `failure.png` (on failure) into gitignored `build/reports/e2e/<journey>/`, generates fixed-width
  `summary.md`, and publishes CI step summaries + failure annotations when `$GITHUB_STEP_SUMMARY` is
  present.
* **`scripts/e2e/record.sh`**: Dedicated local video recorder
  (`--journey <direct-chat|otp-recovery>`)
  that uses `ffmpeg` to synthesize `demo.mp4` and `demo.gif` into `build/reports/e2e/<journey>/`,
  combining the live AVD screen recording with a real-time dark terminal stream of `E2E_PEER` (Bob)
  or `E2E_TEST` FSM events via `ffmpeg`'s subtitle filter without external Python image libraries.
* **`scripts/e2e/service.sh`**: Exposes atomic host subcommands (`up`, `down`, `status`, `get-otp`,
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
2. **Async Multi-Service Infrastructure Chains (Happy & Unhappy Paths)**: Previously, neither
   `KMP-ChatBackend/docker-compose.yml` nor an unencrypted local SMTP/AMQP Spring profile existed
   (`application.yaml` enforces RabbitMQ SSL and SMTP STARTTLS). Full-stack flows involving
   Redis-backed 6-digit OTP generation and rejection (`400 INVALID_VERIFICATION_CODE`), Mailpit
   email delivery, and asynchronous RabbitMQ `UserEvent.Verified` projection from `:user` to `:chat`
   could not be tested together end-to-end against a compiled APK.
3. **Host-Out-of-Band Orchestration & Visual Proof Without Token Waste**: Evaluating UI tests with
   an AI agent at runtime (`android` CLI XML journeys) consumes LLM tokens per step, introduces
   non-determinism, and cannot run unattended on CI runners. Conversely, pure manual `adb` scripts
   lack reusable journey abstractions, structured post-run visual evidence, and synchronized
   client-plus-peer showcase recordings.

## Goals

* **G1 — Modular Zero-Token Deterministic E2E Journeys**: Execute both Direct Chat (`S0`–`S7`) and
  OTP Negative/Recovery (`N0`–`N2`) state machines deterministically with zero LLM calls via
  `:app:connectedDebugAndroidTest` gated by
  `-Pandroid.testInstrumentationRunnerArguments.e2e=true` and selectable via
  `./scripts/e2e/verify.sh --journey <all|direct-chat|otp-recovery>`.
* **G2 — Real Cross-Boundary & Async Coverage**: Verify real HTTP + Mailpit SMTP OTP delivery,
  Redis invalid-OTP rejection and client UI recovery, RabbitMQ `UserEvent.Verified` peer visibility
  (`bob@example.com`), bidirectional WebSocket delivery (`SENDING` -> `SENT` and peer inbound
  arrival), and `ConnectivityManagerNetworkMonitor` offline-to-online catch-up sync.
* **G3 — Clean Build & Security Isolation**: Keep `cleartextTrafficPermitted="false"` on release
  builds by isolating `network_security_config.xml` to `app/src/debug/`, and pass
  `-PBASE_URL=http://localhost:8080/` via Gradle `providers.gradleProperty` without modifying
  `local.properties` or dirtying `git status`.
* **G4 — Fast Repeatable Runs Without Volume Wipes**: Support repeatable consecutive executions
  against warm Docker containers by clearing Android app data (`pm clear`) before each journey,
  using run-scoped `alice.<epoch>@example.com` accounts, and idempotently ensuring `bob@example.com`
  is registered and verified via Mailpit.
* **G5 — Survivable Diagnostics, Pure-ffmpeg Recorder & Service CLI**: Preserve device
  artifacts across AGP test completion using
  `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` plus mirroring to
  `/sdcard/Download/e2e/<journey>/`, produce `build/reports/e2e/summary.md`,
  isolate video synthesis (`demo.mp4` / `demo.gif`) into `./scripts/e2e/record.sh`, and provide
  `scripts/e2e/service.sh` (`up`, `down`, `status`, `get-otp`, `ui-dump`, `screenshot`).
* **G6 — Preserved Tier 1/2 Speed**: Keep `./scripts/verify.sh` and `./scripts/verify.sh clean` 100%
  emulator-free and Docker-free; isolate E2E execution to Tier 3 (`./scripts/e2e/verify.sh`) for
  local milestone checks and manual/nightly KVM CI workflows (`.github/workflows/e2e.yml`).

## Non-Goals

* **NG1 — Combinatorial Input Validation on AVD**: Exhaustive form validation matrices remain in
  Tier 1/2 Robolectric and Spring Testcontainers suites; Tier 3 focuses on stateful cross-boundary
  happy and recovery journeys.
* **NG2 — LLM-Driven Runtime UI Navigation**: Using AI agent turns or `<journey>.xml` loops to
  locate widgets or tap buttons during automated E2E runs is explicitly excluded.
* **NG3 — Test-Only Backend Auth/OTP Backdoors**: No special bypass endpoints, hardcoded OTPs, or
  direct SQL inserts skipping RabbitMQ `UserEvent.Verified` are exposed in `KMP-ChatBackend`
  production code.
* **NG4 — Executing AVD E2E on Every PR**: `./scripts/e2e/verify.sh` does not run inside
  `./scripts/verify.sh clean` or standard pull-request CI gates (`.github/workflows/ci.yml`).

## Critical User Journeys (CUJs)

### CUJ 1 (`DirectChatE2ETest` /
`--journey direct-chat`): Zero-Token Full-Stack Account Lifecycle, Bidirectional WebSocket & Reconnect Catch-Up

* **Given**: Docker Compose (`postgres`, `redis`, `rabbitmq`, `mailpit`), `KMP-ChatBackend`
  (`--spring.profiles.active=dev,e2e`), and AVD `Medium_Phone_API_37.0` are running with
  `adb reverse tcp:8080 tcp:8080` and `adb reverse tcp:8025 tcp:8025`; app data is cleared
  (`pm clear`) and `:app` is built with `-PBASE_URL=http://localhost:8080/`.
* **When**: `./scripts/e2e/verify.sh --journey direct-chat` (or `--journey all`) launches
  `DirectChatE2ETest`, driving `alice.<epoch>@example.com` through the 8-state FSM (`S0`–`S7`)
  while `E2EPeerClient` acts as verified peer `bob@example.com`.
* **Then**: Every FSM transition satisfies its `ComposeTestRule` assertion within bounded timeouts,
  `steps.csv` and `logcat.txt` are stored under `build/reports/e2e/direct-chat/`
  (`failure.png` on failure, or per-step PNGs if `--capture-steps` is passed), and `summary.md`
  reports `PASS` with zero LLM tokens consumed. When `./scripts/e2e/record.sh --journey direct-chat`
  is run locally, `demo.mp4` and `demo.gif` are synthesized in the same folder.

### CUJ 2 (`OtpRecoveryE2ETest` /
`--journey otp-recovery`): Cross-Boundary Invalid OTP Rejection & Stateful Recovery

* **Given**: The E2E stack is active and `pm clear` has reset Android app state.
* **When**: `./scripts/e2e/verify.sh --journey otp-recovery` (or `--journey all`) launches
  `OtpRecoveryE2ETest`, executing the 3-state negative-and-recovery FSM (`N0`–`N2`):
    1. `N0_REGISTER_AND_CHALLENGE`: Alice registers `alice.otp.<epoch>@example.com` and lands on
       `verify_email_screen`.
    2. `N1_INVALID_OTP_REJECTED`: Alice enters an invalid 6-digit OTP (`000000`, verified distinct
       from the real Mailpit OTP); Backend Redis rejects the code (`400 INVALID_VERIFICATION_CODE`),
       and `verify_email_screen` renders `verify_email_error_text` and `retry_button` without
       crashing or hanging in a loading state.
    3. `N2_VALID_OTP_RECOVERY_TO_LOGIN`: Alice clears/replaces the input with the real 6-digit OTP
       fetched from Mailpit (`fetchLatestOtp`), succeeds verification, and transitions to
       `login_screen` with `confirmation_banner` visible.
* **Then**: `steps.csv` and `logcat.txt` are stored under
  `build/reports/e2e/otp-recovery/` and recorded as `PASS` in `summary.md`.

### CUJ 3: Host Infrastructure Bring-Up & Live Agent Inspection (`./scripts/e2e/service.sh`)

* **Given**: A developer or AI agent needs to boot services once for iterative debugging or inspect
  why a state failed.
* **When**: The caller invokes `./scripts/e2e/service.sh up`, `status`, `get-otp <email>`,
  `ui-dump`,
  `screenshot <path>`, or `down`.
* **Then**: Services come up deterministically with health checks (`pg_isready`, `redis-cli ping`,
  `rabbitmq-diagnostics`, Mailpit HTTP, Backend `GET /v3/api-docs`, and `adb shell pm path android`
  readiness after `sys.boot_completed=1`), OTP codes are parsed from Mailpit's REST API via
  `r"\b(\d{6})\b"`, and current AVD semantics/PNGs are exported to the specified artifact path.

## Technical Plan (Architecture)

### 1. System Topology & Process Boundary Wiring

`Android-ChatApp` acts as the leaf orchestrator pointing to `../KMP-ChatBackend` (overridable via
`E2E_BACKEND_DIR`). Rather than duplicating Docker runtime logic inside `Android-ChatApp`,
`KMP-ChatBackend` owns `docker-compose.yml` and the unencrypted local/E2E Spring profile
(`application-e2e.yaml`), while `Android-ChatApp` owns the debug client build configuration,
modular instrumentation journey suite, and host CLI scripts.

```text
+------------------------------------------+       +-----------------------------------------------+
| AVD: Medium_Phone_API_37.0               |       | Host Machine                                  |
|                                          |       |                                               |
|  :app (Debug APK)                        |       |  KMP-ChatBackend (Spring Boot :8080)          |
|   - debug network_security_config        |       |   - Profile: dev,e2e                          |
|   - BASE_URL=http://localhost:8080/      |       |   - /v3/api-docs (readiness probe)            |
|                                          |       |   - /api/auth/*, /api/chat/*, /ws/chat        |
|  BaseE2EJourneyTest (:app/androidTest)   |       |       |         |          |          |       |
|   ├── DirectChatE2ETest (S0..S7)         |==adb==|   Postgres   Redis   RabbitMQ   Mailpit (:8025)|
|   └── OtpRecoveryE2ETest (N0..N2)        |reverse|                                               |
|   - E2EPeerClient (Mailpit + Bob + Logs) |8080   |  Host Scripts (Android-ChatApp/scripts/e2e/)  |
|   - /sdcard/Download/e2e/<journey>       |8025   |   - service.sh (up/down/status/otp/dump/shot) |
+------------------------------------------+       |   - verify.sh (--journey, --capture-steps)    |
                                                   |   - record.sh (--journey)                     |
                                                   +-----------------------------------------------+
```

### 2. Backend E2E Infrastructure (`KMP-ChatBackend`)

* **`docker-compose.yml`**: Defines 4 pinned service containers with explicit healthchecks and host
  port bindings synchronized with `application-e2e.yaml`:
    * `postgres` (`postgres:17-alpine`, port `5432`, DB `nerochat`, user `nerochat`, password
      `nerochat_e2e`, healthcheck `pg_isready -U nerochat -d nerochat`)
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
      `spring.jpa.hibernate.ddl-auto=update`, `hibernate.hbm2ddl.create_namespaces=true`, and IP
      rate limiting disabled.

### 3. Android Debug Networking & Parameterized `BASE_URL`

* **Debug-Only Cleartext Exemption**: `app/src/debug/AndroidManifest.xml` references
  `app/src/debug/res/xml/network_security_config.xml` permitting `cleartextTrafficPermitted="true"`
  strictly for domains `localhost`, `127.0.0.1`, and `10.0.2.2`. Release APKs inherit zero manifest
  overrides and keep `cleartextTrafficPermitted="false"`.
* **Configuration-Cache-Safe URL Injection**: `core/network/build.gradle.kts` resolves
  `BASE_URL` in priority order:
  `providers.gradleProperty("BASE_URL").orNull ?: properties.getProperty("BASE_URL") ?: "http://localhost/"`,
  preserving existing non-E2E build behavior while letting `scripts/e2e/verify.sh` pass
  `-PBASE_URL=http://localhost:8080/` cleanly. `ChatWebSocketClient.kt` derives
  `ws://.../ws/chat` directly from `BuildConfig.BASE_URL`.
* **Minimal Determinism `testTag`s**: `.testTag("chat_list_new_chat_fab")` in
  `ChatListScreen.kt` and `.testTag("conversation_message_input")` in `ConversationScreen.kt` allow
  `ComposeTestRule` to locate every interactive widget across `feature:auth` and `feature:chat`
  without modifying visual rendering or invalidating screenshot goldens.

### 4. Modular E2E Journey Architecture (`BaseE2EJourneyTest`, `DirectChatE2ETest`,

`OtpRecoveryE2ETest`)

Rather than coupling infrastructure plumbing and state assertions inside a monolithic test class,
`:app:src/androidTest/kotlin/io/github/neronguyen/chat/e2e/` is structured around three components:

#### 4.1 Shared Harness Base Class (`BaseE2EJourneyTest.kt`)

* **Opt-In Instrumentation Guard**: Checks
  `Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("e2e") == "true")` in
  `@Before` so normal `./gradlew connectedDebugAndroidTest` runs skip E2E classes cleanly.
* **Survivable Per-Journey Artifact Mirroring**: AGP's default `connectedDebugAndroidTest` lifecycle
  uninstalls the tested APK on completion, which deletes app-scoped storage under
  `/sdcard/Android/data/io.github.neronguyen.chat/files/`. To guarantee zero artifact loss,
  `scripts/e2e/verify.sh` passes `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`
  AND `BaseE2EJourneyTest` mirrors `steps.csv` (and `failure.png` on error, or step PNGs when
  `e2eCaptureSteps=true`) immediately into `/sdcard/Download/e2e/<journey>/`.
* **Structured FSM Execution (`runJourney` & `step`)**: Wraps journey execution with structured
  `E2E_TEST` logcat events (`[FSM] State transition -> <state> (<checkpoint>)`,
  `[FSM] State <state> PASS (<elapsedMs>ms)`), `steps.csv` tracking, automatic `failure.png` capture
  when any assertion throws, and unconditional `restoreDeviceConnectivity()` (`svc wifi enable` +
  `svc data enable`) in `@After`.

#### 4.2 Peer & Mailpit Driver with Live Telemetry (`E2EPeerClient.kt`)

* **Bob Headless Client `E2E_PEER` Logcat Stream**: Emits concise client-perspective events under
  tag `E2E_PEER` (`[BOB] Online as bob@example.com`, `[BOB] Joined direct room ... with <peer>`,
  `[BOB] Latest msg from peer: "..."`, `[BOB-WS] Connected /ws/chat`,
  `[BOB-WS] Sending: "..."`, `[BOB-WS] Server ACK: "..."`, `[BOB-WS] Received from peer: "..."`,
  `[BOB-WS] Disconnected /ws/chat`). Alice's Mailpit OTP polling and FSM state logs remain scoped
  to `E2E_TEST` so `scripts/e2e/record.sh` renders **Bob's headless client exclusively** in the
  Terminal pane for peer-enabled journeys (`DirectChatE2ETest`), or `E2E_TEST` FSM events for
  single-client journeys (`OtpRecoveryE2ETest`).
* **Mailpit Disambiguation (`fetchLatestOtp(email)`)**: Polls
  `GET http://localhost:8025/api/v1/messages`, filters summaries whose `To` array contains
  `<email>`, selects the newest message ID by `Created` timestamp, fetches
  `GET http://localhost:8025/api/v1/message/{ID}`, and extracts the 6-digit OTP via `r"\b(\d{6})\b"`
  from `Text` (or `HTML` after stripping `<style>` blocks and tags so CSS hex colors like `#112233`
  never false-match).
* **Idempotent Peer Seeding (`ensureVerifiedPeerBob()`) & Persistent WebSocket (
  `connectWebSocket()`)**: Attempts `POST /api/auth/login` for
  `bob@example.com`. If login fails, calls `POST /api/auth/register` (or
  `POST /api/auth/resend-verification` on `409 Conflict`), fetches Bob's OTP from Mailpit, calls
  `POST /api/auth/verify-email`, logs Bob in, and polls
  `GET /api/chat/participants?email=bob@example.com`
  until RabbitMQ's `UserEvent.Verified` projection completes. In `DirectChatE2ETest`,
  `connectWebSocket()` runs immediately afterward (using an OkHttp WebSocket client configured with
  infinite `callTimeout`/`readTimeout` and `30s` `pingInterval`) so Bob remains online across
  `S0`–`S7` and logs real-time receipt of Alice's `S5` outbound messages.

#### 4.3 Journey 1 — Direct Chat 8-State FSM (`DirectChatE2ETest.kt`, `--journey direct-chat`)

| State                             | Screen / Phase       | Triggering Action                                                                                                                                                                                                               | `ComposeTestRule` Post-Condition                                                                                                          | Checkpoint ID (`steps.csv`)   |
|:----------------------------------|:---------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------|:------------------------------|
| **`S0_REGISTER_FORM`**            | Register             | Navigate from `login_screen` (`register_button`), fill `alice.<epoch>@example.com`, display name, and strong password                                                                                                           | Inputs populated on `register_screen` without client validation errors                                                                    | `00_register_form`            |
| **`S1_OTP_CHALLENGE`**            | Verify Email         | Tap `register_button`                                                                                                                                                                                                           | `verify_email_screen` & `otp_input_field` visible                                                                                         | `01_otp_challenge`            |
| **`S2_LOGIN_VERIFIED`**           | Login Verified       | Fetch Alice's 6-digit OTP via `fetchLatestOtp(aliceEmail)`, type into `otp_input_field` (auto-submitting via `VerifyEmailScreen`'s `onComplete`)                                                                                | Redirected to `login_screen` with `confirmation_banner` visible                                                                           | `02_login_verified`           |
| **`S3_CHAT_LIST_EMPTY`**          | Chat List            | Fill Alice credentials + tap `login_button`                                                                                                                                                                                     | `chat_list_screen` visible; sync error banner absent                                                                                      | `03_chat_list_empty`          |
| **`S4_NEW_CHAT_FOUND_PEER`**      | New Chat Search      | Tap `chat_list_new_chat_fab`, type `bob@example.com` in `new_chat_email_field`, tap `new_chat_search_button`                                                                                                                    | `new_chat_searched_participant_card` visible (proving RabbitMQ `UserEvent.Verified` projection)                                           | `04_new_chat_found_peer`      |
| **`S5_OUTBOUND_MESSAGE_SENT`**    | Conversation Send    | Enter `"Hello Bob from AVD"` in `new_chat_first_message_field` (required to enable submit), tap `new_chat_submit_direct_button`; then send `"Follow-up from AVD"` via `conversation_message_input` + `conversation_send_button` | Both outbound bubbles visible on `ConversationScreen` with status reconciled to `SENT`, and Bob's WebSocket logs receipt of both messages | `05_outbound_message_sent`    |
| **`S6_INBOUND_MESSAGE_RECEIVED`** | Conversation Receive | `E2EPeerClient` resolves `roomId` via `GET /api/chat/rooms` and sends `WsClientMessage.NewMessage` (`"Reply from Bob over WS"`) over Bob's active WebSocket                                                                     | Bubble `"Reply from Bob over WS"` renders automatically on Alice's `ConversationScreen`                                                   | `06_inbound_message_received` |
| **`S7_RECONNECT_CATCHUP_SYNC`**   | Offline & Catch-Up   | Run `UiDevice.executeShellCommand("svc wifi disable")` + `svc data disable`; confirm `sync_status_bar`; Bob sends `"Offline missed msg"` via WS; re-enable `svc wifi enable` + `svc data enable`                                | `sync_status_bar` clears back to quiet synced state and `"Offline missed msg"` renders in `ConversationScreen`                            | `07_reconnect_catchup_sync`   |

#### 4.4 Journey 2 — Negative & Recovery OTP FSM (`OtpRecoveryE2ETest.kt`, `--journey otp-recovery`)

| State                                | Screen / Phase        | Triggering Action                                                                                                                  | `ComposeTestRule` Post-Condition                                                                                       | Checkpoint ID (`steps.csv`)      |
|:-------------------------------------|:----------------------|:-----------------------------------------------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------|:---------------------------------|
| **`N0_REGISTER_AND_CHALLENGE`**      | Register -> Challenge | Navigate to `register_screen`, submit `alice.otp.<epoch>@example.com`, display name, and password                                  | `verify_email_screen` & `otp_input_field` visible                                                                      | `00_register_and_challenge`      |
| **`N1_INVALID_OTP_REJECTED`**        | Verify Email Error    | Enter wrong 6-digit code (`000000`, distinct from Mailpit OTP) into `otp_input_field`, triggering backend Redis verification check | Remains on `verify_email_screen`; `verify_email_error_text` and `retry_button` displayed (`Invalid verification code`) | `01_invalid_otp_rejected`        |
| **`N2_VALID_OTP_RECOVERY_TO_LOGIN`** | Recovery -> Login     | Clear `otp_input_field` and enter valid 6-digit OTP fetched via `fetchLatestOtp(aliceEmail)`                                       | Verification succeeds; navigates to `login_screen` with `confirmation_banner` visible                                  | `02_valid_otp_recovery_to_login` |

### 5. Host Orchestration (`scripts/e2e/{service,verify,record}.sh`)

* **`scripts/e2e/service.sh <command>`** (deterministic, zero-token host primitives):
    * `up`: Starts `docker compose -f "${E2E_BACKEND_DIR}/docker-compose.yml" up -d --wait`; if
      `:8080`
      is not listening, launches
      `(cd "${E2E_BACKEND_DIR}" && setsid ./gradlew --no-daemon :app:bootRun --args='--spring.profiles.active=dev,e2e')`
      in its own process group with PID/PGID stored in `/tmp/nerochat-e2e-backend.pid` and polls
      `http://localhost:8080/v3/api-docs` (`200 OK`); boots a phone AVD (auto-scoring phone form
      factors and highest API level when multiple AVDs exist, or `E2E_AVD_NAME` if set) if no
      emulator is online, waits for `sys.boot_completed=1` **and** `adb shell pm path android`
      Package Manager readiness; applies `adb reverse tcp:8080 tcp:8080` and `tcp:8025 tcp:8025`.
    * `down`: Terminates the host Spring Boot process group via its recorded PGID, removes
      `adb reverse --remove-all`, and runs `docker compose stop` (`--clean` passes `down -v`).
    * `status`: Emits pass/fail status for Docker containers, Backend HTTP `:8080`, Mailpit `:8025`,
      AVD boot state, and `adb reverse --list`, exiting **non-zero** if any required layer is
      unhealthy.
    * `get-otp <email>`: Queries `http://localhost:8025/api/v1/messages` +
      `GET /api/v1/message/{ID}` filtered by recipient `<email>` and extracts the latest 6-digit OTP
      via `r"\b(\d{6})\b"` (stripping `<style>` blocks and HTML tags on HTML fallback).
    * `ui-dump [out.xml]` / `screenshot [out.png]`: Wraps `android layout` /
      `android screen capture` (falling back to `adb shell uiautomator dump` / `screencap`) into
      explicit paths for developer or agent triage.
* **
  `scripts/e2e/verify.sh [--journey <all|direct-chat|otp-recovery>] [--capture-steps] [--teardown] [--clean]`**:
    1. Invokes `service.sh up`.
    2. For each selected journey (`direct-chat` -> `DirectChatE2ETest`, `otp-recovery` ->
       `OtpRecoveryE2ETest`):
        * Clears package data (`adb shell pm clear io.github.neronguyen.chat`) and cleans
          `build/reports/e2e/<journey>/`.
        * Runs `:app:connectedDebugAndroidTest` with `-PBASE_URL=http://localhost:8080/`,
          `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`,
          `-Pandroid.testInstrumentationRunnerArguments.e2e=true`,
          `-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>` (and `e2eCaptureSteps=true`
          only if `--capture-steps` is set).
    3. Pulls `steps.csv`, `logcat.txt`, and `failure.png` (if failed) into
       `build/reports/e2e/<journey>/` and writes a fixed-width aligned
       `build/reports/e2e/summary.md` (plus `$GITHUB_STEP_SUMMARY` and `::error::` annotations
       when running on GitHub Actions).
* **
  `scripts/e2e/record.sh [--journey <direct-chat|otp-recovery>] [--capture-steps] [--teardown] [--clean]`**:
    1. Verifies `ffmpeg` and `ffprobe`, invokes `service.sh up`, and pre-installs
       `:app:installDebug` + `:app:installDebugAndroidTest`.
    2. Starts `adb shell screenrecord` to `/data/local/tmp/e2e-avd-raw.mp4`, emits the logcat
       sync anchor `[RECORDER] Screen recording started`, and launches the selected journey via
       `adb shell am instrument`.
    3. Stops `screenrecord` cleanly via `SIGINT`, pulls `avd-raw.mp4`, `logcat.txt`, and
       `steps.csv`,
       and runs `ffmpeg` to synthesize `demo.mp4` and `demo.gif` directly into
       `build/reports/e2e/<journey>/`.

### 6. Verification Tier Boundaries

* **Tier 1 (`./scripts/verify.sh`)**: Affected-module JVM tests (seconds).
* **Tier 2 (`./scripts/verify.sh clean`)**: Full JVM unit tests, Robolectric UI behavior tests, AGP
  Compose screenshot tests, `ArchitectureKonsistTest`, and `lintDebug`. Enforced on every commit and
  PR CI (`.github/workflows/ci.yml`).
* **Tier 3 (`./scripts/e2e/verify.sh`)**: Full-stack AVD + Spring Boot + Docker Compose E2E
  verification. Run on local milestone/release gates or manual/nightly KVM-enabled CI workflows
  (`.github/workflows/e2e.yml`).

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

### Alternative B: Monolithic Single-Class E2E Test Without Shared Harness Base

* **Pros**: Keeps all helper methods private inside `DirectChatE2ETest.kt`.
* **Cons**: Cannot add negative/recovery CUJs (such as `OtpRecoveryE2ETest`) without
  duplicating `UiDevice` screenshot capture, `/sdcard/Download/e2e` mirroring,
  `steps.csv` reporting, and `svc wifi/data` cleanup.
* **Reason for Rejection**: Extracting `BaseE2EJourneyTest` keeps each journey class focused purely
  on its state machine transitions and allows selective or full-suite execution via
  `--journey <all|direct-chat|otp-recovery>`.

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
    * **Zero LLM tokens** consumed per `./scripts/e2e/verify.sh` run.
    * Target warm-container execution time is **60–150 seconds** across journeys (reusing warm
      Postgres/Redis/RabbitMQ/Mailpit containers and `alice.<epoch>@example.com` isolation rather
      than wiping volumes on every iteration).
* **Repo Hygiene & Determinism**:
    * `./scripts/e2e/verify.sh` and `./scripts/e2e/record.sh` never edit `local.properties` or write
      reports into tracked source folders; all `steps.csv`, `logcat.txt`, `failure.png`,
      `summary.md`, and recorded videos write strictly to gitignored `build/reports/e2e/`.
    * `BaseE2EJourneyTest` subclasses skip automatically (`Assume.assumeTrue`) unless
      `-Pandroid.testInstrumentationRunnerArguments.e2e=true` is set, preventing unintended
      failures during standard `./gradlew connectedDebugAndroidTest` invocations.

--------------------------------------------------------------------------------

## Risks & Mitigations

* **AGP APK Uninstall Wiping App-Scoped Artifacts**: By default, `connectedDebugAndroidTest`
  uninstalls the app APK after the test run finishes, erasing
  `/sdcard/Android/data/io.github.neronguyen.chat/files/`.
    * *Mitigation*: Pass `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` and mirror
      every checkpoint artifact immediately to `/sdcard/Download/e2e/<journey>/`.
* **Package Manager Race After `sys.boot_completed=1`**: On cold AVD boots, `sys.boot_completed`
  flips to `1` several seconds before the Android `package` service accepts `pm clear` or APK
  installations.
    * *Mitigation*: `service.sh up` polls `adb shell pm path android` until it succeeds after
      `sys.boot_completed=1`.
* **Host Port Collisions & Stale Backend PIDs**: Ports `5432`, `6379`, `5672`, `8025`, or `8080` may
  already be bound by local dev services, or an unclean exit may leave
  `/tmp/nerochat-e2e-backend.pid` stale.
    * *Mitigation*: `service.sh up` verifies live PID ownership + `/v3/api-docs` health before
      spawning Spring Boot, fails fast with actionable port-conflict diagnostics if `:8080` is
      occupied by an unrelated process, and `service.sh down` cleans stale PID files cleanly.
