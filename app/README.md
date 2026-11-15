# app

## Overview
Android application entrypoint, composition root, splash screen, and top-level Navigation 3 routing host.

## Interface
- `[ChatApplication.kt:ChatApplication]`: Application class initialized by Android runtime, configuring Hilt dependency injection, background sync workers, and real-time managers.
- `[MainActivity.kt:MainActivity]`: Single activity hosting the Jetpack Compose content view and observing `AuthState` to switch between `AuthNavGraph` and `ChatNavGraph`.
- `[BaseE2EJourneyTest.kt:BaseE2EJourneyTest]`, `[DirectChatE2ETest.kt:DirectChatE2ETest]`,
  `[OtpRecoveryE2ETest.kt:OtpRecoveryE2ETest]`, & `[E2EPeerClient.kt:E2EPeerClient]`:
  Opt-in modular `androidTest` E2E journey suite (`S0`–`S7` `direct-chat` and `N0`–`N2`
  `otp-recovery`) driving `MainActivity` against a live backend + Mailpit over `adb reverse`.

## Invariants
- Application module depending on all feature, sync (`:sync:work`), and core modules.
- Supervised by `ProcessLifecycleOwner` to drive real-time WebSocket connection state.
- Debug-only `app/src/debug/AndroidManifest.xml` and `network_security_config.xml` permit cleartext
  traffic strictly for `localhost`, `127.0.0.1`, and `10.0.2.2`; release builds keep
  `cleartextTrafficPermitted="false"`.
- `BaseE2EJourneyTest` subclasses skip via `Assume.assumeTrue` unless
  `-Pandroid.testInstrumentationRunnerArguments.e2e=true` is passed, derive `journeyId` in
  `kebab-case` from `<Journey>E2ETest`, and mirror `steps.csv` (plus `failure.png` on failure, or
  step PNGs with `--capture-steps`) to `/sdcard/Download/e2e/<journey>/`.

## Side Effects
- Initializes Android application process, Hilt graph, and foreground lifecycle observers.

## Verification

- Unit & Konsist tests: `./gradlew :app:testDebugUnitTest --rerun-tasks`
- Compile instrumentation tests: `./gradlew :app:compileDebugAndroidTestKotlin`
- Full-stack Tier 3 AVD verification:
  `./scripts/e2e/verify.sh [--journey <all|direct-chat|otp-recovery>] [--capture-steps]`
- Full-stack Tier 3 AVD video recording:
  `./scripts/e2e/record.sh [--journey <direct-chat|otp-recovery>]`
