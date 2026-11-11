# sync:work

## Overview
Android WorkManager background execution and scheduling edge adapter. Hosts `ChatSyncWorker` (`@HiltWorker`) and `WorkManagerSyncSchedulerImpl`.

## Interface
- `[ChatSyncWorker.kt:ChatSyncWorker]`: `CoroutineWorker` delegating background sync execution to `ChatSyncManager.performCatchUpSync()`.
- `[WorkManagerSyncSchedulerImpl.kt:WorkManagerSyncSchedulerImpl]`: Implementation of `ChatSyncScheduler` SPI enqueuing periodic and one-time catch-up sync requests via Android `WorkManager`.
- `[SyncWorkModule.kt:SyncWorkModule]`: Hilt module binding `WorkManagerSyncSchedulerImpl` to `ChatSyncScheduler`.

## Invariants
- Depends only on `:core:data` and `:core:model`. Zero coupling to `:core:datastore`, `:core:database`, or `:core:network`.
- Maps retryable domain errors (`DataError.isRetryable()`) to native WorkManager `Result.retry()`.
- Periodic work uses `ExistingPeriodicWorkPolicy.KEEP` and network constraints (`NetworkType.CONNECTED`, `requiresBatteryNotLow`).

## Side Effects
- Enqueues and cancels WorkManager tasks in Android OS system scheduler.

## Verification
- Run: `./gradlew :sync:work:testDebugUnitTest --rerun-tasks`
