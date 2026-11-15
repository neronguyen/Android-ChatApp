# app

## Overview
Android application entrypoint, composition root, splash screen, and top-level Navigation 3 routing host.

## Interface
- `[ChatApplication.kt:ChatApplication]`: Application class initialized by Android runtime, configuring Hilt dependency injection, background sync workers, and real-time managers.
- `[MainActivity.kt:MainActivity]`: Single activity hosting the Jetpack Compose content view and observing `AuthState` to switch between `AuthNavGraph` and `ChatNavGraph`.
- `[FullStackSmokeE2ETest.kt:FullStackSmokeE2ETest]` & `[E2EPeerClient.kt:E2EPeerClient]`: Opt-in
  `androidTest` 8-state FSM harness (`S0`–`S7`) driving `MainActivity` against a live backend +
  Mailpit over `adb reverse`.

## Invariants
- Application module depending on all feature, sync (`:sync:work`), and core modules.
- Supervised by `ProcessLifecycleOwner` to drive real-time WebSocket connection state.
- Debug-only `app/src/debug/AndroidManifest.xml` and `network_security_config.xml` permit cleartext
  traffic strictly for `localhost`, `127.0.0.1`, and `10.0.2.2`; release builds keep
  `cleartextTrafficPermitted="false"`.
- `FullStackSmokeE2ETest` skips via `Assume.assumeTrue` unless
  `-Pandroid.testInstrumentationRunnerArguments.e2eSmoke=true` is passed.

## Side Effects
- Initializes Android application process, Hilt graph, and foreground lifecycle observers.

## Verification

- Unit & Konsist tests: `./gradlew :app:testDebugUnitTest --rerun-tasks`
- Compile instrumentation tests: `./gradlew :app:compileDebugAndroidTestKotlin`
- Full-stack Tier 3 AVD smoke: `./scripts/smoke-e2e.sh`
