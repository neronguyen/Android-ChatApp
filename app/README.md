# app

## Overview
Android application entrypoint, composition root, splash screen, and top-level Navigation 3 routing host.

## Interface
- `[ChatApplication.kt:ChatApplication]`: Application class initialized by Android runtime, configuring Hilt dependency injection, background sync workers, and real-time managers.
- `[MainActivity.kt:MainActivity]`: Single activity hosting the Jetpack Compose content view and observing `AuthState` to switch between `AuthNavGraph` and `ChatNavGraph`.

## Invariants
- Application module depending on all feature, sync (`:sync:work`), and core modules.
- Supervised by `ProcessLifecycleOwner` to drive real-time WebSocket connection state.

## Side Effects
- Initializes Android application process, Hilt graph, and foreground lifecycle observers.

## Verification
- Run: `./gradlew :app:testDebugUnitTest --rerun-tasks`
