package io.github.neronguyen.chat.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.cash.turbine.test
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.security.CryptoManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.security.GeneralSecurityException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesTokenDataSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testUser = User(
        id = "user-1",
        email = "elena@example.com",
        displayName = "Elena Rostova",
        isEmailVerified = true,
    )

    private fun TestScope.createDataStore(name: String = "auth_test.preferences_pb"): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.newFile(name)
        }

    private fun TestScope.createDataSource(
        dataStore: DataStore<Preferences> = createDataStore(),
        cryptoManager: CryptoManager = FakeCryptoManager(),
    ): PreferencesTokenDataSource = PreferencesTokenDataSource(
        dataStore = dataStore,
        cryptoManager = cryptoManager,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        appScope = backgroundScope,
    )

    @Test
    fun `initial state is Unauthenticated with null tokens`() = runTest {
        val dataSource = createDataSource()

        dataSource.authState.test {
            assertEquals(AuthState.Unauthenticated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(dataSource.getAccessToken())
        assertNull(dataSource.getRefreshToken())
    }

    @Test
    fun `saveAuthData persists encrypted tokens and emits Authenticated`() = runTest {
        val dataStore = createDataStore()
        val dataSource = createDataSource(dataStore = dataStore)

        dataSource.authState.test {
            assertEquals(AuthState.Unauthenticated, awaitItem())

            dataSource.saveAuthData(
                user = testUser,
                accessToken = "access-token-1",
                refreshToken = "refresh-token-1",
            )

            assertEquals(
                AuthState.Authenticated(user = testUser, accessToken = "access-token-1"),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }

        val storedPrefs = dataStore.data.first()
        val storedAccess = storedPrefs[PreferencesTokenDataSource.KEY_ENCRYPTED_ACCESS_TOKEN]
        val storedRefresh = storedPrefs[PreferencesTokenDataSource.KEY_ENCRYPTED_REFRESH_TOKEN]
        assertTrue(!storedAccess.isNullOrBlank())
        assertTrue(!storedRefresh.isNullOrBlank())
        assertNotEquals("access-token-1", storedAccess)
        assertNotEquals("refresh-token-1", storedRefresh)

        assertEquals("access-token-1", dataSource.getAccessToken())
        assertEquals("refresh-token-1", dataSource.getRefreshToken())
    }

    @Test
    fun `saveTokens updates access and refresh tokens while preserving user`() = runTest {
        val dataSource = createDataSource()

        dataSource.saveAuthData(
            user = testUser,
            accessToken = "access-1",
            refreshToken = "refresh-1",
        )
        advanceUntilIdle()

        dataSource.saveTokens(
            accessToken = "access-2",
            refreshToken = "refresh-2",
        )
        advanceUntilIdle()

        assertEquals("access-2", dataSource.getAccessToken())
        assertEquals("refresh-2", dataSource.getRefreshToken())
        assertEquals(
            AuthState.Authenticated(user = testUser, accessToken = "access-2"),
            dataSource.authState.first(),
        )
    }

    @Test
    fun `clearAuthData removes preferences and emits Unauthenticated`() = runTest {
        val dataStore = createDataStore()
        val dataSource = createDataSource(dataStore = dataStore)

        dataSource.authState.test {
            assertEquals(AuthState.Unauthenticated, awaitItem())

            dataSource.saveAuthData(
                user = testUser,
                accessToken = "access-1",
                refreshToken = "refresh-1",
            )
            assertEquals(
                AuthState.Authenticated(user = testUser, accessToken = "access-1"),
                awaitItem(),
            )

            dataSource.clearAuthData()
            assertEquals(AuthState.Unauthenticated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(dataSource.getAccessToken())
        assertNull(dataSource.getRefreshToken())
        assertTrue(dataStore.data.first().asMap().isEmpty())
    }

    @Test
    fun `corrupted ciphertext self-heals by clearing preferences and emitting Unauthenticated`() =
        runTest {
            val dataStore = createDataStore()
            val cryptoManager = FakeCryptoManager()
            val initialDataSource = createDataSource(
                dataStore = dataStore,
                cryptoManager = cryptoManager,
            )

            initialDataSource.saveAuthData(
                user = testUser,
                accessToken = "access-1",
                refreshToken = "refresh-1",
            )
            advanceUntilIdle()

            // Simulate KeyStore key invalidation / corrupted ciphertext on subsequent app launch
            val failingCrypto = FakeCryptoManager(shouldThrowOnDecrypt = true)
            val recoveredDataSource = createDataSource(
                dataStore = dataStore,
                cryptoManager = failingCrypto,
            )

            recoveredDataSource.authState.test {
                assertEquals(AuthState.Unauthenticated, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            advanceUntilIdle()

            assertNull(recoveredDataSource.getAccessToken())
            assertNull(recoveredDataSource.getRefreshToken())
            assertTrue(dataStore.data.first().asMap().isEmpty())
        }

    @Test
    fun `corrupted user JSON self-heals by clearing preferences and emitting Unauthenticated`() =
        runTest {
            val dataStore = createDataStore()
            val cryptoManager = FakeCryptoManager()

            // Pre-populate DataStore with valid encrypted tokens but malformed JSON
            dataStore.edit { prefs ->
                prefs[PreferencesTokenDataSource.KEY_ENCRYPTED_ACCESS_TOKEN] =
                    java.util.Base64.getEncoder().encodeToString(
                        cryptoManager.encrypt("access-1".encodeToByteArray())
                    )
                prefs[PreferencesTokenDataSource.KEY_ENCRYPTED_REFRESH_TOKEN] =
                    java.util.Base64.getEncoder().encodeToString(
                        cryptoManager.encrypt("refresh-1".encodeToByteArray())
                    )
                prefs[PreferencesTokenDataSource.KEY_USER_DATA] = "{corrupted-json"
            }

            val dataSource = createDataSource(
                dataStore = dataStore,
                cryptoManager = cryptoManager,
            )

            dataSource.authState.test {
                assertEquals(AuthState.Unauthenticated, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            advanceUntilIdle()

            assertNull(dataSource.getAccessToken())
            assertNull(dataSource.getRefreshToken())
            assertTrue(dataStore.data.first().asMap().isEmpty())
        }

    @Test
    fun `corrupted refresh token returns null and clears corrupted preferences`() = runTest {
        val dataStore = createDataStore()
        val cryptoManager = FakeCryptoManager()
        val dataSource = createDataSource(
            dataStore = dataStore,
            cryptoManager = cryptoManager,
        )

        dataSource.saveAuthData(
            user = testUser,
            accessToken = "access-1",
            refreshToken = "refresh-1",
        )
        advanceUntilIdle()

        cryptoManager.shouldThrowOnDecrypt = true

        assertNull(dataSource.getRefreshToken())
        assertTrue(dataStore.data.first().asMap().isEmpty())
    }

    private class FakeCryptoManager(
        var shouldThrowOnDecrypt: Boolean = false,
    ) : CryptoManager {
        override fun encrypt(bytes: ByteArray): ByteArray =
            bytes.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()

        override fun decrypt(bytes: ByteArray): ByteArray {
            if (shouldThrowOnDecrypt) {
                throw GeneralSecurityException("Simulated KeyStore invalidation")
            }
            return bytes.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }
    }
}
