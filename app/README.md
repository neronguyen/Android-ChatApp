# app

## Overview
Android application entrypoint, composition root, splash screen, and top-level Navigation 3 routing host.

## Interface
- `[ChatApplication.kt:ChatApplication]`: Application class initialized by Android runtime, configuring Hilt dependency injection, background sync workers, and real-time managers.
- `[MainActivity.kt:MainActivity]`: Single activity hosting the Jetpack Compose content view and observing `AuthState` to switch between `AuthNavGraph` and `ChatNavGraph`.
- `[BaseE2EJourneyTest.kt:BaseE2EJourneyTest]`, `[FullStackSmokeE2ETest.kt:FullStackSmokeE2ETest]`,
  `[OtpRecoverySmokeE2ETest.kt:OtpRecoverySmokeE2ETest]`, & `[E2EPeerClient.kt:E2EPeerClient]`:
  Opt-in modular `androidTest` E2E journey suite (`S0`–`S7` happy-path and `N0`–`N2` invalid-OTP
  recovery) driving `MainActivity` against a live backend + Mailpit over `adb reverse`.

## Invariants
- Application module depending on all feature, sync (`:sync:work`), and core modules.
- Supervised by `ProcessLifecycleOwner` to drive real-time WebSocket connection state.
- Debug-only `app/src/debug/AndroidManifest.xml` and `network_security_config.xml` permit cleartext
  traffic strictly for `localhost`, `127.0.0.1`, and `10.0.2.2`; release builds keep
  `cleartextTrafficPermitted="false"`.
- `BaseE2EJourneyTest` subclasses skip via `Assume.assumeTrue` unless
  `-Pandroid.testInstrumentationRunnerArguments.e2eSmoke=true` is passed, and mirror per-journey
  checkpoints to `/sdcard/Download/e2e-smoke/<journey-id>/`.

## Side Effects
- Initializes Android application process, Hilt graph, and foreground lifecycle observers.

## Verification

- Unit & Konsist tests: `./gradlew :app:testDebugUnitTest --rerun-tasks`
- Compile instrumentation tests: `./gradlew :app:compileDebugAndroidTestKotlin`
- Full-stack Tier 3 AVD smoke:
  `./scripts/smoke-e2e.sh [--journey <all|happy-path|otp-recovery>] [--record-demo]`
