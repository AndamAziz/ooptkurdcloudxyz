package xyz.kurdcloud.englishmaster.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.kurdcloud.englishmaster.core.model.AuthSession
import xyz.kurdcloud.englishmaster.core.network.SessionProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets before they are written to disk. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}

/** AES-256-GCM with a non-exportable key held in the Android Keystore. */
class KeystoreCipher(private val alias: String = "englishmaster.session.v1") : SecretCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_SIZE))
        return cipher.doFinal(sealed, IV_SIZE, sealed.size - IV_SIZE)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

/**
 * Holds the signed-in session in memory and persists it encrypted in DataStore.
 * Call [load] once at start-up before the HTTP layer is used.
 */
class SessionStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : SessionProvider {
    private val state = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = state.asStateFlow()

    private val expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the server rejected the refresh token and the user was signed out. */
    val sessionExpired: SharedFlow<Unit> = expired.asSharedFlow()

    suspend fun load() {
        val blob = dataStore.data.first()[KEY] ?: return
        val restored = runCatching {
            val plain = cipher.decrypt(Base64.decode(blob, Base64.NO_WRAP))
            json.decodeFromString(Stored.serializer(), plain.decodeToString()).toSession()
        }.getOrNull()
        if (restored == null) {
            // Key lost (e.g. restored backup or reinstall) or data corrupted: sign out cleanly.
            dataStore.edit { it.remove(KEY) }
        }
        state.value = restored
    }

    suspend fun save(session: AuthSession) {
        val plain = json.encodeToString(Stored.serializer(), Stored.from(session)).encodeToByteArray()
        val blob = Base64.encodeToString(cipher.encrypt(plain), Base64.NO_WRAP)
        withContext(Dispatchers.IO) { dataStore.edit { it[KEY] = blob } }
        state.value = session
    }

    suspend fun remove() {
        withContext(Dispatchers.IO) { dataStore.edit { it.remove(KEY) } }
        state.value = null
    }

    override fun current(): AuthSession? = state.value

    override fun update(session: AuthSession) = runBlocking { save(session) }

    override fun clear(expired: Boolean) {
        runBlocking { remove() }
        if (expired) this.expired.tryEmit(Unit)
    }

    @Serializable
    private data class Stored(val u: String, val e: String?, val a: String, val r: String, val x: Long) {
        fun toSession() = AuthSession(u, e, a, r, x)

        companion object {
            fun from(s: AuthSession) = Stored(s.userId, s.email, s.accessToken, s.refreshToken, s.expiresAtEpochSeconds)
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("session")
        val json = Json { ignoreUnknownKeys = true }
    }
}
