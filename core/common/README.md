# core:common

## Overview
Application-wide coroutine dispatchers, application coroutine scope bindings, and baseline core utilities.

## Interface

- `[SingleFlight.kt:SingleFlight]`: Generic concurrency coalescing utility ensuring concurrent
  callers share a single in-flight `Deferred` and isolating caller cancellation.
- `[KeyedSingleFlight.kt:KeyedSingleFlight]`: Keyed concurrency coalescing utility ensuring
  concurrent callers for the same key share a single in-flight `Deferred`, with independent
  execution across distinct keys.
- `[ConcurrencyExtensions.kt:mapConcurrent]`: Bounded parallel transformation extension (`Semaphore`
  + `supervisorScope`) isolating child failure while preserving output order.
- `[ExponentialBackoff.kt:ExponentialBackoff]`: Parameterized exponential backoff and symmetric
  jitter calculator for resilient reconnection loops and transient failure retries.
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
