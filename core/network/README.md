# core:network

## Overview
Retrofit HTTP client infrastructure, authenticated OkHttp pipeline, token refresh authenticator, and WebSocket client for real-time bi-directional messaging.

## Interface
- `[AuthNetworkDataSource.kt:AuthNetworkDataSource]`: Interface defining HTTP authentication remote calls.
- `[ChatNetworkDataSource.kt:ChatNetworkDataSource]`: Interface defining HTTP chat room and message remote calls.
- `[ChatRealtimeDataSource.kt:ChatRealtimeDataSource]`: Interface defining real-time WebSocket connection lifecycle and event stream.
- `[ChatWebSocketClient.kt:ChatWebSocketClient]`: OkHttp WebSocket manager implementing `ChatRealtimeDataSource`, encoding `WsClientMessage` and decoding `WsServerEvent`.
- `[SocketConnectionState.kt:SocketConnectionState]`: Network-level socket connection states (`Disconnected`, `Connecting`, `Connected`, `Failed`).
- `[TokenAuthenticator.kt:TokenAuthenticator]`: OkHttp `Authenticator` intercepting `401 Unauthorized` responses and executing synchronized single-flight token refresh.
- `[AuthInterceptor.kt:AuthInterceptor]`: OkHttp `Interceptor` injecting `Authorization: Bearer <token>` headers.
- `[TokenRefresher.kt:TokenRefresher]`: Boundary interface allowing network layer to trigger repository-level token refresh without circular dependencies.
- `[NetworkModule.kt:NetworkModule]`: Hilt DI module providing Retrofit, OkHttp, and network data sources.

## Invariants
- Enforces strict callback isolation on superseded WebSocket connections.
- Concurrent `401` failures trigger exactly one token refresh request under a synchronization mutex.
- All JSON payloads are serialized using `kotlinx.serialization.json.Json` with `KMP-ChatContract` wire models.
- `[BASELINE-DEBT]`: Public data source interfaces leak wire DTOs (`UserDto`, `RegisterRequest`, etc.) across module boundaries (scheduled for domain model encapsulation in Track 4).

## Side Effects
- Establishes HTTP network connections to backend REST endpoints.
- Establishes and maintains persistent OkHttp WebSocket connections to `/ws/chat`.

## Verification
- Run: `./gradlew :core:network:testDebugUnitTest --rerun-tasks`
