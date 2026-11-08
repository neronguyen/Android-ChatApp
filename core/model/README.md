# core:model

## Overview
Pure Kotlin client domain models and typed domain error hierarchies.

## Interface
- `[User.kt:User]`: Pure client user domain model (`id`, `email`, `displayName`).
- `[AuthState.kt:AuthState]`: Sealed authentication state (`Unauthenticated`, `Authenticated(user: User, accessToken: String)`).
- `[ChatRoom.kt:ChatRoom]`: Client chat room domain model (`id`, `type`, `name`, `participants`, `lastMessage`, `lastActivityAt`).
- `[ChatMessage.kt:ChatMessage]`: Client message domain model (`id`, `roomId`, `senderId`, `content`, `timestamp: Instant`, `deliveryStatus: MessageDeliveryStatus`, `deletedAt`).
- `[ChatParticipant.kt:ChatParticipant]`: Room participant domain model (`userId`, `email`, `displayName`, `profilePictureUrl`).
- `[MessageDeliveryStatus.kt:MessageDeliveryStatus]`: Message lifecycle enum (`SENDING`, `SENT`, `FAILED`, `DELETED`).
- `[RealtimeConnectionState.kt:RealtimeConnectionState]`: Connection lifecycle states (`Disconnected`, `Connecting`, `Connected`).
- `[AuthError.kt:AuthError]`: Sealed hierarchy of client authentication errors.
- `[ChatError.kt:ChatError]`: Sealed hierarchy of client chat errors.
- `[DataError.kt:DataError]`: Sealed hierarchy of data storage and network failures.

## Invariants
- Zero Android framework or persistence dependencies (pure Kotlin JVM module).
- Never depends on wire DTOs (`:contract:*`) or database entities (`:core:database`).

## Side Effects
- None (pure immutable domain models).

## Verification
- Run: `./gradlew :core:model:test --rerun-tasks`
