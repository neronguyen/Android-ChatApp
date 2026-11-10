# core:common

## Overview
Application-wide coroutine dispatchers, application coroutine scope bindings, and baseline core utilities.

## Interface

- `[SingleFlight.kt:SingleFlight]`: Generic concurrency coalescing utility ensuring concurrent
  callers share a single in-flight `Deferred` and isolating caller cancellation.
- `[ChatDispatchers.kt:ChatDispatchers]`: Dispatcher enumeration (`Default`, `IO`) used with `@Dispatcher` qualifier.
- `[AppScope.kt:AppScope]`: Application-scoped `CoroutineScope` qualifier for long-running background tasks.
- `[DispatchersModule.kt:DispatchersModule]`: Hilt DI module providing qualified coroutine dispatchers.
- `[CoroutineScopesModule.kt:CoroutineScopesModule]`: Hilt DI module providing `@AppScope` application coroutine scope.

## Invariants
- Zero Android UI or feature dependencies.
- Replaces hardcoded `Dispatchers.IO`/`Dispatchers.Default` for deterministic unit testing.

## Side Effects
- Manages long-lived coroutine lifecycle bound to the application process.

## Verification
- Run: `./gradlew :core:common:test --rerun-tasks`
