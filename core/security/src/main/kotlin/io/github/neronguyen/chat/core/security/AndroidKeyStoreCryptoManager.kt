package io.github.neronguyen.chat.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AndroidKeyStoreCryptoManager internal constructor(
    private val secretKeyProvider: () -> SecretKey,
) : CryptoManager {

    @Inject
    constructor() : this(secretKeyProvider = AndroidKeyStoreKeyProvider())

    override fun encrypt(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) return byteArrayOf()

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeyProvider())
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(bytes)

        return iv + encryptedBytes
    }

    override fun decrypt(bytes: ByteArray): ByteArray {
        if (bytes.size <= GCM_IV_LENGTH) return byteArrayOf()

        val iv = bytes.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = bytes.copyOfRange(GCM_IV_LENGTH, bytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKeyProvider(), spec)

        return cipher.doFinal(ciphertext)
    }

    private class AndroidKeyStoreKeyProvider : () -> SecretKey {
        private val keyStore: KeyStore by lazy {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
                load(null)
            }
        }

        @Synchronized
        override fun invoke(): SecretKey {
            val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            return existingKey?.secretKey ?: generateSecretKey()
        }

        private fun generateSecretKey(): SecretKey {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "auth_token_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
}
