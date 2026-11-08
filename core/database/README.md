# core:database

## Overview
Room SQLite local persistence engine serving as the Single Source of Truth (SSOT) for rooms, messages, participants, and cross-references.

## Interface
- `[ChatDatabase.kt:ChatDatabase]`: RoomDatabase instance managing SQLite tables and migrations.
- `[ChatDao.kt:ChatDao]`: Data Access Object providing reactive queries (`Flow<List<ChatRoomWithDetails>>`, `Flow<List<ChatMessageEntity>>`) and transactional upserts (`@Transaction`).
- `[ChatMessageEntity.kt:ChatMessageEntity]`: SQLite entity storing messages with client-assigned UUIDs and delivery status (`SENDING`, `SENT`, `FAILED`, `DELETED`).
- `[ChatRoomEntity.kt:ChatRoomEntity]`: SQLite entity storing room metadata and last activity timestamp.
- `[ChatParticipantEntity.kt:ChatParticipantEntity]`: SQLite entity storing user profile information.
- `[ChatRoomParticipantCrossRef.kt:ChatRoomParticipantCrossRef]`: Many-to-many junction entity with composite primary key `(roomId, userId)` and foreign key cascade rules.
- `[ChatRoomWithDetails.kt:ChatRoomWithDetails]`: Room relation model combining room metadata, participants, and latest message.
- `[DatabaseModule.kt:DatabaseModule]`: Hilt DI module providing `ChatDatabase` and `ChatDao`.

## Invariants
- Room database is the authoritative client state store; all UI streams read from reactive SQLite `Flow`s.
- `ChatRoomParticipantCrossRef` enforces `ForeignKey.CASCADE` on `roomId`.
- `ChatMessageEntity` links to `ChatRoomEntity` via foreign key constraint.

## Side Effects
- Reads/writes private app SQLite database file on disk.

## Verification
- Run: `./gradlew :core:database:testDebugUnitTest --rerun-tasks`
