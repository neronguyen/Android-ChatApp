package io.github.neronguyen.chat.core.security

import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AndroidKeyStoreCryptoManagerTest {

    private fun generateAes256Key(): SecretKey =
        KeyGenerator.getInstance("AES").apply {
            init(256)
        }.generateKey()

    @Test
    fun `encrypt and decrypt round-trip restores original bytes`() {
        val key = generateAes256Key()
        val cryptoManager = AndroidKeyStoreCryptoManager { key }
        val plaintext = "jwt.access.token-payload-123456".encodeToByteArray()

        val encrypted = cryptoManager.encrypt(plaintext)
        val decrypted = cryptoManager.decrypt(encrypted)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt prepends 12-byte IV and uses fresh IV per encryption`() {
        val key = generateAes256Key()
        val cryptoManager = AndroidKeyStoreCryptoManager { key }
        val plaintext = "identical-secret-token".encodeToByteArray()

        val encryptedFirst = cryptoManager.encrypt(plaintext)
        val encryptedSecond = cryptoManager.encrypt(plaintext)

        // 12-byte GCM IV + plaintext + 16-byte GCM authentication tag
        assertEquals(12 + plaintext.size + 16, encryptedFirst.size)
        assertEquals(12 + plaintext.size + 16, encryptedSecond.size)
        assertFalse(encryptedFirst.contentEquals(encryptedSecond))
    }

    @Test
    fun `encrypt returns empty array for empty input`() {
        val key = generateAes256Key()
        val cryptoManager = AndroidKeyStoreCryptoManager { key }

        val encrypted = cryptoManager.encrypt(byteArrayOf())

        assertEquals(0, encrypted.size)
    }

    @Test
    fun `decrypt returns empty array when payload is shorter than or equal to IV length`() {
        val key = generateAes256Key()
        val cryptoManager = AndroidKeyStoreCryptoManager { key }

        assertEquals(0, cryptoManager.decrypt(byteArrayOf()).size)
        assertEquals(0, cryptoManager.decrypt(ByteArray(11)).size)
        assertEquals(0, cryptoManager.decrypt(ByteArray(12)).size)
    }

    @Test
    fun `decrypt throws AEADBadTagException when ciphertext is tampered`() {
        val key = generateAes256Key()
        val cryptoManager = AndroidKeyStoreCryptoManager { key }
        val encrypted = cryptoManager.encrypt("sensitive-refresh-token".encodeToByteArray())

        // Flip a bit in the ciphertext / auth tag region
        val tampered = encrypted.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 0xFF).toByte()

        assertFailsWith<AEADBadTagException> {
            cryptoManager.decrypt(tampered)
        }
    }

    @Test
    fun `decrypt throws AEADBadTagException when key differs from encryption key`() {
        val encryptionKey = generateAes256Key()
        val differentKey = generateAes256Key()
        val encryptingManager = AndroidKeyStoreCryptoManager { encryptionKey }
        val decryptingManager = AndroidKeyStoreCryptoManager { differentKey }

        val encrypted = encryptingManager.encrypt("sensitive-access-token".encodeToByteArray())

        assertFailsWith<AEADBadTagException> {
            decryptingManager.decrypt(encrypted)
        }
    }
}
