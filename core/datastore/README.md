# core:datastore

## Overview
Preferences DataStore wrapper storing encrypted access/refresh session tokens, current user session state, and reactive `AuthState` flows.

## Interface
- `[TokenDataSource.kt:TokenDataSource]`: Interface for saving, clearing, and streaming encrypted token credentials.
- `[PreferencesTokenDataSource.kt:PreferencesTokenDataSource]`: DataStore implementation backed by `CryptoManager` with in-memory `AtomicReference` cache.
- `[DataStoreModule.kt:DataStoreModule]`: Hilt DI module binding `TokenDataSource`.

## Invariants
- Tokens stored in DataStore are always encrypted via `CryptoManager`.
- In-memory `AtomicReference` ensures fast non-blocking token reads in OkHttp interceptors.
- Exposes `authState: Flow<AuthState>` using the domain model from `:core:model`.

## Side Effects
- Reads/writes private app Preferences DataStore files on disk.

## Verification
- Run: `./gradlew :core:datastore:testDebugUnitTest --rerun-tasks`
