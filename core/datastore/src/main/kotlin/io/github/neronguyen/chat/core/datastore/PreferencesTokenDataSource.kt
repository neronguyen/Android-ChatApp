package io.github.neronguyen.chat.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.neronguyen.chat.core.common.network.AppScope
import io.github.neronguyen.chat.core.common.network.ChatDispatchers
import io.github.neronguyen.chat.core.common.network.Dispatcher
import io.github.neronguyen.chat.core.datastore.model.UserData
import io.github.neronguyen.chat.core.datastore.model.toDomain
import io.github.neronguyen.chat.core.model.AuthState
import io.github.neronguyen.chat.core.model.User
import io.github.neronguyen.chat.core.security.CryptoManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_preferences")

@Singleton
internal class PreferencesTokenDataSource internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val cryptoManager: CryptoManager,
    @Dispatcher(ChatDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
    @AppScope private val appScope: CoroutineScope,
) : TokenDataSource {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        cryptoManager: CryptoManager,
        @Dispatcher(ChatDispatchers.IO) ioDispatcher: CoroutineDispatcher,
        @AppScope appScope: CoroutineScope,
    ) : this(
        dataStore = context.authDataStore,
        cryptoManager = cryptoManager,
        ioDispatcher = ioDispatcher,
        appScope = appScope,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val cachedAccessToken = AtomicReference<String?>(null)

    override val authState: Flow<AuthState> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            val encryptedAccessToken = preferences[KEY_ENCRYPTED_ACCESS_TOKEN]
                ?: return@map AuthState.Unauthenticated

            val userDataJson = preferences[KEY_USER_DATA]
            if (userDataJson == null) {
                clearCorruptedPreferences()
                return@map AuthState.Unauthenticated
            }

            val accessToken = decryptString(encryptedAccessToken)
            if (accessToken.isBlank()) {
                clearCorruptedPreferences()
                return@map AuthState.Unauthenticated
            }

            try {
                val userData = json.decodeFromString<UserData>(userDataJson)
                AuthState.Authenticated(
                    user = userData.toDomain(),
                    accessToken = accessToken,
                )
            } catch (_: Exception) {
                clearCorruptedPreferences()
                AuthState.Unauthenticated
            }
        }
        .distinctUntilChanged()
        .onEach { state ->
            if (state is AuthState.Authenticated) cachedAccessToken.set(state.accessToken)
            else cachedAccessToken.set(null)
        }
        .flowOn(context = ioDispatcher)
        .shareIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    override suspend fun getAccessToken(): String? {
        // Fast path: non-blocking memory read
        cachedAccessToken.get()?.takeIf { it.isNotBlank() }?.let { return it }
        // Slow path: waits for the shared flow's first emission (already running on externalScope)
        return (authState.firstOrNull() as? AuthState.Authenticated)?.accessToken
    }

    override suspend fun getRefreshToken(): String? = withContext(ioDispatcher) {
        val preferences = dataStore.data.firstOrNull() ?: return@withContext null
        val encrypted = preferences[KEY_ENCRYPTED_REFRESH_TOKEN] ?: return@withContext null
        if (encrypted.isEmpty()) return@withContext null

        val decrypted = decryptString(encrypted)
        if (decrypted.isBlank()) {
            clearCorruptedPreferences()
            return@withContext null
        }
        decrypted
    }

    override suspend fun saveAuthData(user: User, accessToken: String, refreshToken: String) =
        withContext(ioDispatcher) {
            cachedAccessToken.set(accessToken)
            val encryptedAccessToken = encryptString(accessToken)
            val encryptedRefreshToken = encryptString(refreshToken)

            val userData = UserData(
                userId = user.id,
                email = user.email,
                displayName = user.displayName,
                isEmailVerified = user.isEmailVerified,
            )
            val userDataJson = json.encodeToString(UserData.serializer(), userData)

            dataStore.edit { preferences ->
                preferences[KEY_ENCRYPTED_ACCESS_TOKEN] = encryptedAccessToken
                preferences[KEY_ENCRYPTED_REFRESH_TOKEN] = encryptedRefreshToken
                preferences[KEY_USER_DATA] = userDataJson
            }
            Unit
        }

    override suspend fun saveTokens(accessToken: String, refreshToken: String) =
        withContext(ioDispatcher) {
            cachedAccessToken.set(accessToken)
            val encryptedAccessToken = encryptString(accessToken)
            val encryptedRefreshToken = encryptString(refreshToken)

            dataStore.edit { preferences ->
                preferences[KEY_ENCRYPTED_ACCESS_TOKEN] = encryptedAccessToken
                preferences[KEY_ENCRYPTED_REFRESH_TOKEN] = encryptedRefreshToken
            }
            Unit
        }

    override suspend fun clearAuthData() {
        cachedAccessToken.set(null)
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private suspend fun clearCorruptedPreferences() {
        cachedAccessToken.set(null)
        runCatching {
            dataStore.edit { preferences ->
                preferences.clear()
            }
        }
    }

    private fun encryptString(rawText: String): String {
        if (rawText.isEmpty()) return ""

        val encryptedBytes = cryptoManager.encrypt(rawText.encodeToByteArray())
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    private fun decryptString(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""

        return try {
            val encryptedBytes = Base64.getDecoder().decode(encryptedBase64)
            val decryptedBytes = cryptoManager.decrypt(encryptedBytes)
            decryptedBytes.decodeToString()
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        internal val KEY_ENCRYPTED_ACCESS_TOKEN = stringPreferencesKey("encrypted_access_token")
        internal val KEY_ENCRYPTED_REFRESH_TOKEN = stringPreferencesKey("encrypted_refresh_token")
        internal val KEY_USER_DATA = stringPreferencesKey("user_data")
    }
}
