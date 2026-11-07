# core:security

## Overview
Android KeyStore cryptographic manager providing hardware-backed AES-256-GCM authenticated encryption for sensitive credentials and session tokens.

## Interface
- `[CryptoManager.kt:CryptoManager]`: Interface defining `encrypt(bytes: ByteArray): ByteArray` and `decrypt(bytes: ByteArray): ByteArray`.
- `[AndroidKeyStoreCryptoManager.kt:AndroidKeyStoreCryptoManager]`: Implementation using `AndroidKeyStore` provider and `AES/GCM/NoPadding` cipher.
- `[SecurityModule.kt:SecurityModule]`: Hilt DI module binding `CryptoManager`.

## Invariants
- Uses 256-bit AES keys generated inside the Android hardware KeyStore.
- Prepends the 12-byte initialization vector (IV) to encrypted ciphertext payloads.

## Side Effects
- Mutates Android KeyStore alias keys on disk/TEE.

## Verification
- Run: `./gradlew :core:security:testDebugUnitTest --rerun-tasks`
